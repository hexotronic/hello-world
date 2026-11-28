package com.hexotronic.fluxplayer.render

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.hexotronic.fluxplayer.data.FrameGenMode
import com.hexotronic.fluxplayer.data.FrameGenQuality
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Laufzeit-Statistik für das Overlay. */
data class RenderStats(
    val sourceFps: Float,
    val outputFps: Float,
    val generatedPercent: Int,
    val activeMode: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val flowWidth: Int,
    val flowHeight: Int,
    val timestampMode: Boolean
)

/**
 * OpenGL-ES-3-Renderer mit Frame-Generierung.
 *
 * Der Decoder (ExoPlayer/MediaCodec) schreibt in eine [SurfaceTexture]. Jedes neue Bild wird mit
 * seinem Präsentationszeitstempel in einen Ringpuffer kopiert. Bei jedem Bildschirm-Refresh wird
 * das zum aktuellen Zeitpunkt passende Bild angezeigt – liegt der Zeitpunkt zwischen zwei Bildern,
 * wird ein Zwischenbild erzeugt (Überblendung oder bewegungskompensiert).
 */
class FrameGenRenderer : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    interface Listener {
        /** Wird auf dem GL-Thread aufgerufen, sobald eine (neue) Video-Surface bereitsteht. */
        fun onVideoSurfaceCreated(surface: Surface, previous: Surface?, previousTexture: SurfaceTexture?)
        fun onRenderStats(stats: RenderStats)
        fun onRendererError(message: String)
    }

    @Volatile var listener: Listener? = null
    @Volatile var renderRequester: (() -> Unit)? = null

    @Volatile var mode: FrameGenMode = FrameGenMode.MOTION
    @Volatile var quality: FrameGenQuality = FrameGenQuality.BALANCED
    /** true während der Wiedergabe (Zeitstempel beachten), false bei Pause (neuestes Bild zeigen). */
    @Volatile var realtime: Boolean = true
    /** Videorechteck normiert auf die View: links, oben, rechts, unten. */
    @Volatile var displayRect: FloatArray = floatArrayOf(0f, 0f, 1f, 1f)
    /** Helligkeit, Kontrast, Sättigung, Gamma. */
    @Volatile var adjustments: FloatArray = floatArrayOf(0f, 1f, 1f, 1f)
    @Volatile var statsEnabled: Boolean = false
    @Volatile var nominalVsyncNs: Long = 16_666_667L

    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0

    private val discontinuity = AtomicBoolean(false)
    private val snapshotRequest = AtomicReference<((Bitmap?) -> Unit)?>(null)
    private val pendingFrames = AtomicInteger(0)

    // ------------------------------------------------------------------ GL-Zustand (nur GL-Thread)
    private var eglContext: EGLContext? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var oesTex = 0
    private var progCopy = 0
    private var progDisplay = 0
    private var progCoarse = 0
    private var progRefine = 0
    private var progMedian = 0
    private var motionSupported = false
    private var builtQuality: FrameGenQuality? = null
    private var quadVbo = 0
    private val texMatrix = FloatArray(16)
    private val uniformCache = HashMap<Long, Int>()
    private var surfW = 1
    private var surfH = 1

    private class Slot {
        var tex = 0
        var fbo = 0
        var flowTex = 0
        var flowFbo = 0
        var valid = false
        var flowValid = false
        var linked = false
        var ts = 0L
        var seq = 0L
    }

    private val slots = Array(SLOT_COUNT) { Slot() }
    private var frameW = 0
    private var frameH = 0
    private var frameLevels = 1
    private var fineLod = 3
    private var coarseLod = 5
    private var fineW = 1
    private var fineH = 1
    private var coarseW = 1
    private var coarseH = 1
    private var midW = 1
    private var midH = 1
    private var midTex = 0
    private var midFbo = 0
    private var coarseTex = 0
    private var coarseFbo = 0
    private var fineTmpTex = 0
    private var fineTmpFbo = 0
    private var flowAllocatedFor: FrameGenQuality? = null

    private var seqCounter = 0L
    private var lastIngest: Slot? = null
    private var displayedSlot: Slot? = null
    private var lastRawTs = Long.MIN_VALUE
    private var discontinuityPending = false
    private var intervalEmaNs = 0.0
    private var arrivalMode = false
    private var lastDrawNs = 0L
    private var drawIntervalEmaNs = 0.0

    // Statistik
    private var statsWindowStart = 0L
    private var statsDraws = 0
    private var statsGenerated = 0
    private var lastActiveMode = "–"

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        renderRequester?.invoke()
    }

    /** Nach Sprüngen (Seek, Titelwechsel) aufrufen, damit nicht über den Schnitt interpoliert wird. */
    fun markDiscontinuity() {
        discontinuity.set(true)
    }

    fun requestSnapshot(callback: (Bitmap?) -> Unit) {
        snapshotRequest.set(callback)
        renderRequester?.invoke()
    }

    /** Gibt Surface/SurfaceTexture frei (Activity wird zerstört). */
    fun releaseSurface() {
        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        pendingFrames.incrementAndGet()
        if (!realtime) renderRequester?.invoke()
    }

    // ------------------------------------------------------------------ Renderer-Callbacks

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        val ctx = EGL14.eglGetCurrentContext()
        if (ctx == eglContext && progDisplay != 0 && surfaceTexture != null) {
            // Gleicher Kontext: alle Ressourcen sind noch gültig.
            return
        }
        eglContext = ctx
        forgetGlObjects()
        try {
            progCopy = GlUtil.buildProgram(Shaders.VS_COPY, Shaders.FS_COPY_OES)
            progDisplay = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.DISPLAY_FS)
        } catch (e: Exception) {
            Log.e(TAG, "Basis-Shader fehlgeschlagen", e)
            listener?.onRendererError("OpenGL-Fehler: ${e.message}")
            return
        }
        buildMotionPrograms(quality)
        quadVbo = GlUtil.createQuadBuffer()
        oesTex = GlUtil.createExternalTexture()

        val previousSurface = surface
        val previousTexture = surfaceTexture
        val st = SurfaceTexture(oesTex)
        st.setOnFrameAvailableListener(this)
        val s = Surface(st)
        surfaceTexture = st
        surface = s
        pendingFrames.set(0)
        lastRawTs = Long.MIN_VALUE
        listener?.onVideoSurfaceCreated(s, previousSurface, previousTexture)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        surfW = max(1, width)
        surfH = max(1, height)
        GLES20.glViewport(0, 0, surfW, surfH)
    }

    override fun onDrawFrame(unused: GL10?) {
        val st = surfaceTexture
        if (st == null || progDisplay == 0) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }
        val now = System.nanoTime()
        trackVsync(now)
        try {
            if (builtQuality != quality) buildMotionPrograms(quality)
            ensureFrameTextures()
            if (flowAllocatedFor != quality) allocateFlowTextures()
            if (discontinuity.getAndSet(false)) handleDiscontinuity(now)
            consumeFrames(st, now)
            drawDisplay(now)
            handleSnapshot()
        } catch (e: Exception) {
            Log.e(TAG, "Renderfehler", e)
        }
        if (statsEnabled) updateStats(now)
    }

    // ------------------------------------------------------------------ Aufbau

    private fun forgetGlObjects() {
        // Der alte Kontext ist weg – IDs sind ungültig und werden nur vergessen.
        progCopy = 0; progDisplay = 0; progCoarse = 0; progRefine = 0; progMedian = 0
        motionSupported = false
        builtQuality = null
        quadVbo = 0
        oesTex = 0
        uniformCache.clear()
        for (s in slots) {
            s.tex = 0; s.fbo = 0; s.flowTex = 0; s.flowFbo = 0
            s.valid = false; s.flowValid = false; s.linked = false
        }
        frameW = 0; frameH = 0
        coarseTex = 0; coarseFbo = 0; fineTmpTex = 0; fineTmpFbo = 0; midTex = 0; midFbo = 0
        flowAllocatedFor = null
        lastIngest = null
        displayedSlot = null
    }

    private fun buildMotionPrograms(q: FrameGenQuality) {
        GlUtil.deleteProgram(progCoarse)
        GlUtil.deleteProgram(progRefine)
        GlUtil.deleteProgram(progMedian)
        progCoarse = 0; progRefine = 0; progMedian = 0
        uniformCache.clear()
        builtQuality = q
        motionSupported = try {
            progCoarse = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.coarseFs(q.coarseRadius, 0.012f))
            progRefine = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.refineFs(q.fineRadius, 0.010f, 0.015f))
            progMedian = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.MEDIAN_FS)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Bewegungsschätzung nicht verfügbar – Überblendung als Rückfall", e)
            listener?.onRendererError("Bewegungsinterpolation wird von der GPU nicht unterstützt – nutze Überblendung.")
            false
        }
        for (s in slots) s.flowValid = false
    }

    private fun ensureFrameTextures() {
        var vw = videoWidth
        var vh = videoHeight
        if (vw <= 0 || vh <= 0) {
            vw = 1280; vh = 720
        }
        val scale = min(1f, MAX_FRAME_DIM.toFloat() / max(vw, vh))
        val w = max(2, (vw * scale).roundToInt())
        val h = max(2, (vh * scale).roundToInt())
        if (w == frameW && h == frameH && slots[0].tex != 0) return

        for (s in slots) {
            GlUtil.deleteFramebuffer(s.fbo); GlUtil.deleteTexture(s.tex)
            s.fbo = 0; s.tex = 0; s.valid = false; s.flowValid = false
        }
        frameW = w
        frameH = h
        frameLevels = levelsFor(max(w, h))
        for (s in slots) {
            s.tex = GlUtil.createStorageTexture(w, h, frameLevels, mipmapped = true, linear = true)
            s.fbo = GlUtil.createFramebuffer(s.tex)
        }
        lastIngest = null
        displayedSlot = null
        allocateFlowTextures()
    }

    private fun allocateFlowTextures() {
        GlUtil.deleteFramebuffer(coarseFbo); GlUtil.deleteTexture(coarseTex)
        GlUtil.deleteFramebuffer(fineTmpFbo); GlUtil.deleteTexture(fineTmpTex)
        GlUtil.deleteFramebuffer(midFbo); GlUtil.deleteTexture(midTex)
        for (s in slots) {
            GlUtil.deleteFramebuffer(s.flowFbo); GlUtil.deleteTexture(s.flowTex)
            s.flowFbo = 0; s.flowTex = 0; s.flowValid = false
        }
        var fl = quality.fineLod
        while (fl > 1 && (min(frameW, frameH) shr (fl + 2)) < 4) fl--
        while (fl + 2 > frameLevels - 1 && fl > 0) fl--
        fineLod = fl
        coarseLod = fl + 2
        fineW = max(1, frameW shr fineLod)
        fineH = max(1, frameH shr fineLod)
        coarseW = max(1, frameW shr coarseLod)
        coarseH = max(1, frameH shr coarseLod)
        midW = max(1, frameW shr (fineLod + 1))
        midH = max(1, frameH shr (fineLod + 1))
        coarseTex = GlUtil.createStorageTexture(coarseW, coarseH, 1, mipmapped = false, linear = false)
        coarseFbo = GlUtil.createFramebuffer(coarseTex)
        fineTmpTex = GlUtil.createStorageTexture(fineW, fineH, 1, mipmapped = false, linear = false)
        fineTmpFbo = GlUtil.createFramebuffer(fineTmpTex)
        midTex = GlUtil.createStorageTexture(midW, midH, 1, mipmapped = false, linear = false)
        midFbo = GlUtil.createFramebuffer(midTex)
        for (s in slots) {
            s.flowTex = GlUtil.createStorageTexture(fineW, fineH, 1, mipmapped = false, linear = false)
            s.flowFbo = GlUtil.createFramebuffer(s.flowTex)
        }
        flowAllocatedFor = quality
    }

    // ------------------------------------------------------------------ Bildannahme

    private fun handleDiscontinuity(now: Long) {
        discontinuityPending = true
        val keep = displayedSlot
        for (s in slots) {
            if (s.valid && s !== keep && s.ts > now) s.valid = false
        }
    }

    private fun consumeFrames(st: SurfaceTexture, now: Long) {
        var n = pendingFrames.getAndSet(0)
        if (n <= 0) return
        if (n > 8) n = 8
        for (i in 0 until n) {
            try {
                st.updateTexImage()
            } catch (e: Exception) {
                Log.w(TAG, "updateTexImage fehlgeschlagen", e)
                return
            }
            val raw = st.timestamp
            val usable = raw != 0L && abs(raw - now) < TIMESTAMP_TOLERANCE_NS
            if (usable) {
                arrivalMode = false
                if (raw == lastRawTs) continue
                lastRawTs = raw
                st.getTransformMatrix(texMatrix)
                ingest(raw)
            } else {
                arrivalMode = true
                if (i == n - 1) {
                    st.getTransformMatrix(texMatrix)
                    ingest(now)
                }
            }
        }
    }

    private fun ingest(ts: Long) {
        val prev = lastIngest
        var linked = prev != null && prev.valid
        if (discontinuityPending) {
            linked = false
            discontinuityPending = false
        }
        if (prev != null && prev.valid) {
            val dt = ts - prev.ts
            if (dt <= 0L) {
                // Zeitstempel springen zurück -> veraltete Zukunftsbilder verwerfen
                for (s in slots) if (s.valid && s !== displayedSlot && s.ts >= ts) s.valid = false
                linked = false
            } else if (dt > MAX_LINK_GAP_NS) {
                linked = false
            }
        }
        val slot = pickSlot(prev)
        copyOesToSlot(slot)
        slot.ts = ts
        slot.seq = ++seqCounter
        slot.valid = true
        slot.linked = linked
        slot.flowValid = false
        if (linked && prev != null) {
            val dt = (ts - prev.ts).toDouble()
            intervalEmaNs = if (intervalEmaNs == 0.0) dt else intervalEmaNs * 0.9 + dt * 0.1
            if (mode == FrameGenMode.MOTION && motionSupported && prev.valid) {
                computeFlow(prev, slot)
            }
        }
        lastIngest = slot
    }

    private fun pickSlot(prev: Slot?): Slot {
        var best: Slot? = null
        for (s in slots) {
            if (s === prev || s === displayedSlot) continue
            if (!s.valid) return s
            if (best == null || s.seq < best.seq) best = s
        }
        return best ?: slots.first { it !== prev }
    }

    private fun copyOesToSlot(slot: Slot) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, slot.fbo)
        GLES20.glViewport(0, 0, frameW, frameH)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(progCopy)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniform1i(uniform(progCopy, "uTex"), 0)
        GLES20.glUniformMatrix4fv(uniform(progCopy, "uTexMatrix"), 1, false, texMatrix, 0)
        val vw = videoWidth
        val downscale = vw > 0 && vw > frameW * 1.3f
        if (downscale) {
            GLES20.glUniform2f(uniform(progCopy, "uOff"), 0.25f / frameW, 0.25f / frameH)
        } else {
            GLES20.glUniform2f(uniform(progCopy, "uOff"), 0f, 0f)
        }
        drawQuad()
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, slot.tex)
        GLES30.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
    }

    private fun computeFlow(a: Slot, b: Slot) {
        // 1) grob: volle Suche auf kleiner Mipmap-Stufe
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, coarseFbo)
        GLES20.glViewport(0, 0, coarseW, coarseH)
        GLES20.glUseProgram(progCoarse)
        bindTexture(0, a.tex); bindTexture(1, b.tex)
        GLES20.glUniform1i(uniform(progCoarse, "uPrev"), 0)
        GLES20.glUniform1i(uniform(progCoarse, "uCurr"), 1)
        GLES20.glUniform1i(uniform(progCoarse, "uLod"), coarseLod)
        drawQuad()

        // 2) + 3) Verfeinerung mit Kandidaten (räumlich + zeitlich)
        val temporal = a.flowValid && a.flowTex != 0
        GLES20.glUseProgram(progRefine)
        GLES20.glUniform1i(uniform(progRefine, "uPrev"), 0)
        GLES20.glUniform1i(uniform(progRefine, "uCurr"), 1)
        GLES20.glUniform1i(uniform(progRefine, "uPred"), 2)
        GLES20.glUniform1i(uniform(progRefine, "uTemporal"), 3)
        GLES20.glUniform1i(uniform(progRefine, "uUseTemporal"), if (temporal) 1 else 0)
        bindTexture(0, a.tex); bindTexture(1, b.tex)
        bindTexture(3, if (temporal) a.flowTex else coarseTex)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, midFbo)
        GLES20.glViewport(0, 0, midW, midH)
        bindTexture(2, coarseTex)
        GLES20.glUniform1i(uniform(progRefine, "uLod"), fineLod + 1)
        drawQuad()

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fineTmpFbo)
        GLES20.glViewport(0, 0, fineW, fineH)
        bindTexture(2, midTex)
        GLES20.glUniform1i(uniform(progRefine, "uLod"), fineLod)
        drawQuad()

        // 4) Median
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, b.flowFbo)
        GLES20.glUseProgram(progMedian)
        bindTexture(0, fineTmpTex)
        GLES20.glUniform1i(uniform(progMedian, "uFlow"), 0)
        drawQuad()
        b.flowValid = true
    }

    // ------------------------------------------------------------------ Anzeige

    private fun drawDisplay(now: Long) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, surfW, surfH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val vsync = currentVsyncNs()
        val lag = if (arrivalMode && mode != FrameGenMode.OFF && intervalEmaNs > 0.0) intervalEmaNs.toLong() else 0L
        val target = now + vsync / 2 - lag

        var a: Slot? = null
        if (!realtime) {
            for (s in slots) if (s.valid && (a == null || s.seq > a.seq)) a = s
        } else {
            for (s in slots) {
                if (!s.valid || s.ts > target) continue
                if (a == null || s.ts > a.ts || (s.ts == a.ts && s.seq > a.seq)) a = s
            }
            if (a == null) {
                for (s in slots) if (s.valid && (a == null || s.ts < a.ts)) a = s
            }
        }
        if (a == null) return
        displayedSlot = a

        var b: Slot? = null
        for (s in slots) if (s.valid && s.linked && s.seq == a.seq + 1) b = s

        var t = 0f
        if (realtime && mode != FrameGenMode.OFF && b != null && b.ts > target) {
            val dt = b.ts - a.ts
            if (dt in MIN_INTERP_NS..MAX_LINK_GAP_NS && dt > vsync * 1.15) {
                t = ((target - a.ts).toDouble() / dt.toDouble()).toFloat().coerceIn(0f, 1f)
            }
        }

        GLES20.glUseProgram(progDisplay)
        val r = displayRect
        val x = r[0] * surfW
        val yTop = r[1] * surfH
        val w = max(1f, (r[2] - r[0]) * surfW)
        val h = max(1f, (r[3] - r[1]) * surfH)
        GLES20.glUniform4f(uniform(progDisplay, "uRect"), x, surfH - (yTop + h), w, h)
        val adj = adjustments
        val adjOn = adj[0] != 0f || adj[1] != 1f || adj[2] != 1f || adj[3] != 1f
        GLES20.glUniform4f(uniform(progDisplay, "uAdj"), adj[0], adj[1], adj[2], max(0.1f, adj[3]))
        GLES20.glUniform1i(uniform(progDisplay, "uAdjOn"), if (adjOn) 1 else 0)
        GLES20.glUniform1i(uniform(progDisplay, "uA"), 0)
        GLES20.glUniform1i(uniform(progDisplay, "uB"), 1)
        GLES20.glUniform1i(uniform(progDisplay, "uFlow"), 2)

        val generated: Boolean
        if (b == null || t <= 0.02f) {
            bindTexture(0, a.tex); bindTexture(1, a.tex); bindTexture(2, a.flowTex)
            GLES20.glUniform1i(uniform(progDisplay, "uMode"), 0)
            GLES20.glUniform1f(uniform(progDisplay, "uT"), 0f)
            generated = false
        } else if (t >= 0.98f) {
            bindTexture(0, b.tex); bindTexture(1, b.tex); bindTexture(2, b.flowTex)
            GLES20.glUniform1i(uniform(progDisplay, "uMode"), 0)
            GLES20.glUniform1f(uniform(progDisplay, "uT"), 0f)
            generated = false
        } else {
            val motion = mode == FrameGenMode.MOTION && motionSupported && b.flowValid
            bindTexture(0, a.tex); bindTexture(1, b.tex); bindTexture(2, b.flowTex)
            GLES20.glUniform1i(uniform(progDisplay, "uMode"), if (motion) 2 else 1)
            GLES20.glUniform1f(uniform(progDisplay, "uT"), t)
            generated = true
            lastActiveMode = if (motion) "Bewegung" else "Überblendung"
        }
        drawQuad()
        statsDraws++
        if (generated) statsGenerated++
    }

    private fun handleSnapshot() {
        val cb = snapshotRequest.getAndSet(null) ?: return
        val slot = displayedSlot
        if (slot == null || !slot.valid) {
            cb(null)
            return
        }
        try {
            val buf = ByteBuffer.allocateDirect(frameW * frameH * 4).order(ByteOrder.nativeOrder())
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, slot.fbo)
            GLES20.glReadPixels(0, 0, frameW, frameH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            buf.rewind()
            val raw = Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buf)
            val flipped = Bitmap.createBitmap(raw, 0, 0, frameW, frameH, Matrix().apply { preScale(1f, -1f) }, false)
            if (flipped !== raw) raw.recycle()
            cb(flipped)
        } catch (e: Throwable) {
            Log.e(TAG, "Schnappschuss fehlgeschlagen", e)
            cb(null)
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private fun trackVsync(now: Long) {
        if (realtime && lastDrawNs != 0L) {
            val dt = (now - lastDrawNs).toDouble()
            if (dt > 4_000_000.0 && dt < 50_000_000.0) {
                drawIntervalEmaNs = if (drawIntervalEmaNs == 0.0) dt else drawIntervalEmaNs * 0.95 + dt * 0.05
            }
        }
        lastDrawNs = now
    }

    private fun currentVsyncNs(): Long {
        val measured = drawIntervalEmaNs
        return if (measured > 0.0) measured.toLong() else nominalVsyncNs
    }

    private fun updateStats(now: Long) {
        if (statsWindowStart == 0L) {
            statsWindowStart = now
            return
        }
        val elapsed = now - statsWindowStart
        if (elapsed < 500_000_000L) return
        val out = statsDraws * 1e9f / elapsed
        val src = if (intervalEmaNs > 0.0 && realtime) (1e9 / intervalEmaNs).toFloat() else 0f
        val pct = if (statsDraws > 0) statsGenerated * 100 / statsDraws else 0
        val active = when {
            mode == FrameGenMode.OFF -> "Aus"
            pct == 0 -> "Bereit"
            else -> lastActiveMode
        }
        listener?.onRenderStats(
            RenderStats(src, out, pct, active, frameW, frameH, fineW, fineH, !arrivalMode)
        )
        statsWindowStart = now
        statsDraws = 0
        statsGenerated = 0
    }

    private fun bindTexture(unit: Int, tex: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
    }

    private fun drawQuad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun uniform(program: Int, name: String): Int {
        val key = (program.toLong() shl 32) or (name.hashCode().toLong() and 0xffffffffL)
        return uniformCache.getOrPut(key) { GLES20.glGetUniformLocation(program, name) }
    }

    private fun levelsFor(size: Int): Int {
        var levels = 1
        var s = size
        while (s > 1) {
            s = s shr 1
            levels++
        }
        return levels
    }

    companion object {
        private const val TAG = "FrameGenRenderer"
        private const val SLOT_COUNT = 6
        private const val MAX_FRAME_DIM = 1920
        private const val MAX_LINK_GAP_NS = 150_000_000L
        private const val MIN_INTERP_NS = 3_000_000L
        private const val TIMESTAMP_TOLERANCE_NS = 2_000_000_000L
    }
}
