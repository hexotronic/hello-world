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
import com.hexotronic.fluxplayer.data.UpscalerMode
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
    val timestampMode: Boolean,
    val qualityLabel: String,
    val boosted: Boolean,
    val bidirectional: Boolean,
    val upscaler: String,
    val upscaleFactor: Float,
    val latencyMs: Int
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
    @Volatile var upscaler: UpscalerMode = UpscalerMode.OFF
    /** Schärfung 0..1 (nur mit Upscaler). */
    @Volatile var sharpness: Float = 0.3f
    /** Aktuelles Wiedergabetempo (für Zeitlupen-Timing). */
    @Volatile var playbackSpeed: Float = 1f
    /** Bildrate des Videos laut Container (0 = unbekannt). */
    @Volatile var contentFrameRate: Float = 0f
    /** In Zeitlupe automatisch stärkere Bewegungsschätzung verwenden. */
    @Volatile var slowMoBoost: Boolean = true

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
    private var progUpscale = 0
    private var progBoundary = 0
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
        var bwdTex = 0
        var bwdFbo = 0
        var bwdValid = false
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
    private val levelTex = IntArray(MAX_LEVELS)
    private val levelFbo = IntArray(MAX_LEVELS)
    private var composeTex = 0
    private var composeFbo = 0
    private var activeQuality: FrameGenQuality = FrameGenQuality.BALANCED

    private class FlowJob(val a: Slot, val aSeq: Long, val b: Slot, val bSeq: Long) {
        val passes = ArrayDeque<() -> Unit>()
    }

    private val jobs = ArrayDeque<FlowJob>()

    private class Selection(val a: Slot, val b: Slot?, val t: Float)
    private var coarseTex = 0
    private var coarseFbo = 0
    private var fineTmpTex = 0
    private var fineTmpFbo = 0
    private var fineTmp2Tex = 0
    private var fineTmp2Fbo = 0
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
    private var lastUpscaleFactor = 1f
    private var lastLagNs = 0L

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
            progUpscale = try {
                GlUtil.buildProgram(Shaders.VS_ES3, Shaders.UPSCALE_FS)
            } catch (e: Exception) {
                Log.w(TAG, "Upscaler nicht verfügbar", e)
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Basis-Shader fehlgeschlagen", e)
            listener?.onRendererError("OpenGL-Fehler: ${e.message}")
            return
        }
        activeQuality = effectiveQuality()
        buildMotionPrograms(activeQuality)
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
            val q = effectiveQuality()
            if (q != activeQuality || builtQuality != q) {
                activeQuality = q
                buildMotionPrograms(q)
            }
            ensureFrameTextures()
            if (flowAllocatedFor != activeQuality) allocateFlowTextures()
            if (discontinuity.getAndSet(false)) handleDiscontinuity(now)
            consumeFrames(st, now)
            val sel = select(now)
            if (sel?.b != null && sel.t > 0.02f && sel.t < 0.98f) finishJobFor(sel.b)
            runJobs(passBudget())
            if (sel != null) draw(sel) else clearScreen()
            handleSnapshot()
        } catch (e: Exception) {
            Log.e(TAG, "Renderfehler", e)
        }
        if (statsEnabled) updateStats(now)
    }

    // ------------------------------------------------------------------ Aufbau

    private fun forgetGlObjects() {
        // Der alte Kontext ist weg – IDs sind ungültig und werden nur vergessen.
        progCopy = 0; progDisplay = 0; progCoarse = 0; progRefine = 0; progMedian = 0; progUpscale = 0; progBoundary = 0
        motionSupported = false
        builtQuality = null
        quadVbo = 0
        oesTex = 0
        uniformCache.clear()
        for (s in slots) {
            s.tex = 0; s.fbo = 0; s.flowTex = 0; s.flowFbo = 0; s.bwdTex = 0; s.bwdFbo = 0
            s.valid = false; s.flowValid = false; s.bwdValid = false; s.linked = false
        }
        frameW = 0; frameH = 0
        coarseTex = 0; coarseFbo = 0; fineTmpTex = 0; fineTmpFbo = 0; fineTmp2Tex = 0; fineTmp2Fbo = 0
        composeTex = 0; composeFbo = 0
        levelTex.fill(0)
        levelFbo.fill(0)
        jobs.clear()
        flowAllocatedFor = null
        lastIngest = null
        displayedSlot = null
    }

    private fun buildMotionPrograms(q: FrameGenQuality) {
        GlUtil.deleteProgram(progCoarse)
        GlUtil.deleteProgram(progRefine)
        GlUtil.deleteProgram(progMedian)
        GlUtil.deleteProgram(progBoundary)
        progCoarse = 0; progRefine = 0; progMedian = 0; progBoundary = 0
        uniformCache.clear()
        builtQuality = q
        motionSupported = try {
            progCoarse = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.coarseFs(q.coarseRadius, 0.012f))
            progRefine = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.refineFs(q.fineRadius, 0.010f, 0.015f))
            progMedian = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.MEDIAN_FS)
            if (q.bidirectional) progBoundary = GlUtil.buildProgram(Shaders.VS_ES3, Shaders.boundaryFs(0.02f))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Bewegungsschätzung nicht verfügbar – Überblendung als Rückfall", e)
            listener?.onRendererError("Bewegungsinterpolation wird von der GPU nicht unterstützt – nutze Überblendung.")
            false
        }
        for (s in slots) {
            s.flowValid = false
            s.bwdValid = false
        }
        jobs.clear()
    }

    private fun effectiveQuality(): FrameGenQuality {
        val q = quality
        if (!slowMoBoost || mode != FrameGenMode.MOTION) return q
        val sp = playbackSpeed
        return when {
            sp <= 0.3f -> FrameGenQuality.ULTRA
            sp <= 0.6f && q.ordinal < FrameGenQuality.QUALITY.ordinal -> FrameGenQuality.QUALITY
            else -> q
        }
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
            s.fbo = 0; s.tex = 0; s.valid = false; s.flowValid = false; s.bwdValid = false
        }
        GlUtil.deleteFramebuffer(composeFbo); GlUtil.deleteTexture(composeTex)
        jobs.clear()
        frameW = w
        frameH = h
        frameLevels = levelsFor(max(w, h))
        for (s in slots) {
            s.tex = GlUtil.createStorageTexture(w, h, frameLevels, mipmapped = true, linear = true)
            s.fbo = GlUtil.createFramebuffer(s.tex)
        }
        composeTex = GlUtil.createStorageTexture(w, h, 1, mipmapped = false, linear = true)
        composeFbo = GlUtil.createFramebuffer(composeTex)
        lastIngest = null
        displayedSlot = null
        allocateFlowTextures()
    }

    private fun allocateFlowTextures() {
        jobs.clear()
        GlUtil.deleteFramebuffer(coarseFbo); GlUtil.deleteTexture(coarseTex)
        GlUtil.deleteFramebuffer(fineTmpFbo); GlUtil.deleteTexture(fineTmpTex)
        GlUtil.deleteFramebuffer(fineTmp2Fbo); GlUtil.deleteTexture(fineTmp2Tex)
        fineTmp2Fbo = 0; fineTmp2Tex = 0
        for (i in 0 until MAX_LEVELS) {
            GlUtil.deleteFramebuffer(levelFbo[i]); GlUtil.deleteTexture(levelTex[i])
            levelFbo[i] = 0; levelTex[i] = 0
        }
        for (s in slots) {
            GlUtil.deleteFramebuffer(s.flowFbo); GlUtil.deleteTexture(s.flowTex)
            GlUtil.deleteFramebuffer(s.bwdFbo); GlUtil.deleteTexture(s.bwdTex)
            s.flowFbo = 0; s.flowTex = 0; s.flowValid = false
            s.bwdFbo = 0; s.bwdTex = 0; s.bwdValid = false
        }
        val q = activeQuality
        var cl = q.coarseLod
        while (cl > 1 && (min(frameW, frameH) shr cl) < 4) cl--
        cl = min(cl, frameLevels - 1).coerceAtLeast(1)
        val fl = min(q.fineLod, cl - 1).coerceAtLeast(0)
        coarseLod = cl
        fineLod = fl
        fineW = max(1, frameW shr fl)
        fineH = max(1, frameH shr fl)
        coarseW = max(1, frameW shr cl)
        coarseH = max(1, frameH shr cl)
        coarseTex = GlUtil.createStorageTexture(coarseW, coarseH, 1, mipmapped = false, linear = false)
        coarseFbo = GlUtil.createFramebuffer(coarseTex)
        for (lod in (cl - 1) downTo (fl + 1)) {
            levelTex[lod] = GlUtil.createStorageTexture(max(1, frameW shr lod), max(1, frameH shr lod), 1, mipmapped = false, linear = false)
            levelFbo[lod] = GlUtil.createFramebuffer(levelTex[lod])
        }
        fineTmpTex = GlUtil.createStorageTexture(fineW, fineH, 1, mipmapped = false, linear = false)
        fineTmpFbo = GlUtil.createFramebuffer(fineTmpTex)
        if (q.bidirectional) {
            fineTmp2Tex = GlUtil.createStorageTexture(fineW, fineH, 1, mipmapped = false, linear = false)
            fineTmp2Fbo = GlUtil.createFramebuffer(fineTmp2Tex)
        }
        for (s in slots) {
            s.flowTex = GlUtil.createStorageTexture(fineW, fineH, 1, mipmapped = false, linear = false)
            s.flowFbo = GlUtil.createFramebuffer(s.flowTex)
            if (q.bidirectional) {
                s.bwdTex = GlUtil.createStorageTexture(fineW, fineH, 1, mipmapped = false, linear = false)
                s.bwdFbo = GlUtil.createFramebuffer(s.bwdTex)
            }
        }
        flowAllocatedFor = q
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
            } else if (dt > maxLinkGapNs()) {
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
        slot.bwdValid = false
        if (linked && prev != null) {
            val dt = (ts - prev.ts).toDouble()
            intervalEmaNs = if (intervalEmaNs == 0.0) dt else intervalEmaNs * 0.8 + dt * 0.2
            if (mode == FrameGenMode.MOTION && motionSupported && prev.valid) {
                enqueueFlow(prev, slot)
            }
        }
        lastIngest = slot
    }

    private fun pickSlot(prev: Slot?): Slot {
        var best: Slot? = null
        val shown = displayedSlot
        val next = shown?.let { successor(it) }
        for (s in slots) {
            if (s === prev || s === shown || s === next) continue
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

    // ------------------------------------------------------------------ Bewegungsschätzung (Jobs)

    /**
     * Legt die Render-Passes für ein Bildpaar an. Bei normaler Geschwindigkeit laufen alle sofort,
     * in Zeitlupe werden sie auf mehrere Display-Refreshes verteilt (kein Ruckeln durch Lastspitzen).
     */
    private fun enqueueFlow(a: Slot, b: Slot) {
        while (jobs.size >= 4) jobs.removeFirst()
        val job = FlowJob(a, a.seq, b, b.seq)
        addFlowPasses(job, forward = true)
        if (activeQuality.bidirectional) addFlowPasses(job, forward = false)
        jobs.addLast(job)
    }

    private fun addFlowPasses(job: FlowJob, forward: Boolean) {
        val a = job.a
        val b = job.b
        // vorwärts: Raster von B, gesucht in A (A(x+F) = B(x)); rückwärts: Raster von A, gesucht in B
        val searchIn = if (forward) a else b
        val grid = if (forward) b else a
        val cl = coarseLod
        val fl = fineLod
        job.passes.addLast {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, coarseFbo)
            GLES20.glViewport(0, 0, coarseW, coarseH)
            GLES20.glUseProgram(progCoarse)
            bindTexture(0, searchIn.tex); bindTexture(1, grid.tex)
            GLES20.glUniform1i(uniform(progCoarse, "uPrev"), 0)
            GLES20.glUniform1i(uniform(progCoarse, "uCurr"), 1)
            GLES20.glUniform1i(uniform(progCoarse, "uLod"), cl)
            drawQuad()
        }
        var predTex = coarseTex
        for (lod in (cl - 1) downTo fl) {
            val outFbo = if (lod == fl) fineTmpFbo else levelFbo[lod]
            val outTex = if (lod == fl) fineTmpTex else levelTex[lod]
            val w = max(1, frameW shr lod)
            val h = max(1, frameH shr lod)
            val pred = predTex
            job.passes.addLast {
                val temporal = forward && a.flowValid && a.flowTex != 0
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo)
                GLES20.glViewport(0, 0, w, h)
                GLES20.glUseProgram(progRefine)
                bindTexture(0, searchIn.tex); bindTexture(1, grid.tex); bindTexture(2, pred)
                bindTexture(3, if (temporal) a.flowTex else pred)
                GLES20.glUniform1i(uniform(progRefine, "uPrev"), 0)
                GLES20.glUniform1i(uniform(progRefine, "uCurr"), 1)
                GLES20.glUniform1i(uniform(progRefine, "uPred"), 2)
                GLES20.glUniform1i(uniform(progRefine, "uTemporal"), 3)
                GLES20.glUniform1i(uniform(progRefine, "uUseTemporal"), if (temporal) 1 else 0)
                GLES20.glUniform1i(uniform(progRefine, "uLod"), lod)
                drawQuad()
            }
            predTex = outTex
        }
        val boundary = progBoundary != 0 && fineTmp2Fbo != 0
        job.passes.addLast {
            val target = if (forward) b.flowFbo else b.bwdFbo
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, if (boundary) fineTmp2Fbo else target)
            GLES20.glViewport(0, 0, fineW, fineH)
            GLES20.glUseProgram(progMedian)
            bindTexture(0, fineTmpTex)
            GLES20.glUniform1i(uniform(progMedian, "uFlow"), 0)
            drawQuad()
            if (!boundary) {
                if (forward) b.flowValid = true else b.bwdValid = true
            }
        }
        if (boundary) {
            job.passes.addLast {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, if (forward) b.flowFbo else b.bwdFbo)
                GLES20.glViewport(0, 0, fineW, fineH)
                GLES20.glUseProgram(progBoundary)
                bindTexture(0, searchIn.tex); bindTexture(1, grid.tex); bindTexture(2, fineTmp2Tex)
                GLES20.glUniform1i(uniform(progBoundary, "uPrev"), 0)
                GLES20.glUniform1i(uniform(progBoundary, "uCurr"), 1)
                GLES20.glUniform1i(uniform(progBoundary, "uPred"), 2)
                GLES20.glUniform1i(uniform(progBoundary, "uLod"), fl)
                drawQuad()
                if (forward) b.flowValid = true else b.bwdValid = true
            }
        }
    }

    private fun jobAlive(job: FlowJob): Boolean =
        job.a.seq == job.aSeq && job.b.seq == job.bSeq && job.a.valid && job.b.valid

    private fun runJobs(budget: Int) {
        var left = budget
        while (left > 0) {
            val job = jobs.firstOrNull() ?: return
            if (!jobAlive(job)) {
                jobs.removeFirst()
                continue
            }
            val pass = job.passes.removeFirstOrNull()
            if (pass == null) {
                jobs.removeFirst()
                continue
            }
            pass()
            left--
        }
    }

    /** Wird ein Bewegungsfeld jetzt gebraucht, aber ist noch nicht fertig, sofort fertigrechnen. */
    private fun finishJobFor(b: Slot) {
        if (jobs.none { it.b === b && it.bSeq == b.seq }) return
        var guard = 0
        while (jobs.isNotEmpty() && guard++ < 64) {
            val head = jobs.first()
            val isTarget = head.b === b && head.bSeq == b.seq
            runJobs(1)
            if (isTarget && (jobs.isEmpty() || jobs.first() !== head)) return
        }
    }

    private fun passBudget(): Int = if (playbackSpeed < SLOWMO_SPEED) SLOWMO_PASSES_PER_FRAME else Int.MAX_VALUE

    // ------------------------------------------------------------------ Anzeige

    private fun expectedIntervalNs(): Double {
        val fps = contentFrameRate
        val sp = playbackSpeed.coerceAtLeast(0.01f)
        return if (fps > 1f && fps < 400f) 1e9 / (fps * sp) else intervalEmaNs
    }

    private fun maxLinkGapNs(): Long {
        val iv = expectedIntervalNs()
        return max(MAX_LINK_GAP_NS, min(5e9, iv * 2.5).toLong())
    }

    /**
     * Anzeigeverzögerung, damit das nächste Bild schon vorliegt, wenn interpoliert wird.
     * ExoPlayer gibt Bilder ca. 40–50 ms vor ihrem Zeitpunkt frei – bei normaler Geschwindigkeit
     * reicht das (Verzögerung ≈ 0). In Zeitlupe liegen die Bilder weiter auseinander.
     */
    private fun displayLagNs(): Long {
        if (mode == FrameGenMode.OFF || !realtime) return 0L
        val iv = expectedIntervalNs()
        if (iv <= 0.0) return 0L
        if (arrivalMode) return iv.toLong()
        var lag = max(0.0, iv - EARLY_RELEASE_NS)
        if (playbackSpeed < SLOWMO_SPEED) lag += min(iv * 0.5, 150e6)
        return lag.toLong()
    }

    private fun slotWithSeq(seq: Long): Slot? {
        for (s in slots) if (s.valid && s.seq == seq) return s
        return null
    }

    private fun successor(s: Slot): Slot? {
        val n = slotWithSeq(s.seq + 1) ?: return null
        return if (n.linked) n else null
    }

    private fun select(now: Long): Selection? {
        val vsync = currentVsyncNs()
        var a: Slot? = null
        if (!realtime) {
            for (s in slots) if (s.valid && (a == null || s.seq > a.seq)) a = s
            a ?: return null
            displayedSlot = a
            return Selection(a, null, 0f)
        }
        val targetReal = now + vsync / 2
        // 1) aktuelle Kette = Kette des neuesten fälligen Bildes
        var real: Slot? = null
        for (s in slots) {
            if (!s.valid || s.ts > targetReal) continue
            if (real == null || s.seq > real.seq) real = s
        }
        if (real == null) {
            for (s in slots) if (s.valid && (a == null || s.ts < a.ts)) a = s
            a ?: return null
            displayedSlot = a
            return Selection(a, null, 0f)
        }
        var start: Slot = real
        while (start.linked) start = slotWithSeq(start.seq - 1) ?: break
        // 2) innerhalb der Kette um die Anzeigeverzögerung zurück
        val lag = displayLagNs()
        lastLagNs = lag
        val target = targetReal - lag
        a = start
        var cur: Slot? = start
        while (cur != null) {
            if (cur.ts <= target) a = cur
            if (cur.ts > target) break
            cur = successor(cur)
        }
        val chosen = a ?: return null
        displayedSlot = chosen
        val b = successor(chosen)
        var t = 0f
        if (mode != FrameGenMode.OFF && b != null && b.ts > target && chosen.ts <= target) {
            val dt = b.ts - chosen.ts
            if (dt in MIN_INTERP_NS..maxLinkGapNs() && dt > vsync * 1.15) {
                t = ((target - chosen.ts).toDouble() / dt.toDouble()).toFloat().coerceIn(0f, 1f)
            }
        }
        return Selection(chosen, b, t)
    }

    private fun clearScreen() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, surfW, surfH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun screenRect(): FloatArray {
        val r = displayRect
        val x = r[0] * surfW
        val yTop = r[1] * surfH
        val w = max(1f, (r[2] - r[0]) * surfW)
        val h = max(1f, (r[3] - r[1]) * surfH)
        return floatArrayOf(x, surfH - (yTop + h), w, h)
    }

    private fun adjustmentsActive(): Boolean {
        val adj = adjustments
        return adj[0] != 0f || adj[1] != 1f || adj[2] != 1f || adj[3] != 1f
    }

    private fun draw(sel: Selection) {
        val a = sel.a
        val b = sel.b
        val t = sel.t
        val generated = b != null && t > 0.02f && t < 0.98f
        val shown = if (b != null && t >= 0.98f) b else a
        val useUpscaler = upscaler != UpscalerMode.OFF && progUpscale != 0
        val rect = screenRect()
        lastUpscaleFactor = rect[2] / max(1, frameW)
        if (!useUpscaler) {
            clearScreen()
            displayPass(sel, shown, generated, rect)
        } else {
            val src: Int
            if (generated || adjustmentsActive()) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, composeFbo)
                GLES20.glViewport(0, 0, frameW, frameH)
                displayPass(sel, shown, generated, floatArrayOf(0f, 0f, frameW.toFloat(), frameH.toFloat()))
                src = composeTex
            } else {
                src = shown.tex
            }
            clearScreen()
            GLES20.glUseProgram(progUpscale)
            bindTexture(0, src)
            GLES20.glUniform1i(uniform(progUpscale, "uSrc"), 0)
            GLES20.glUniform4f(uniform(progUpscale, "uRect"), rect[0], rect[1], rect[2], rect[3])
            GLES20.glUniform1i(uniform(progUpscale, "uKernel"), if (upscaler == UpscalerMode.EDGE) 1 else 0)
            GLES20.glUniform1f(uniform(progUpscale, "uSharp"), sharpness.coerceIn(0f, 1f))
            drawQuad()
        }
        statsDraws++
        if (generated) statsGenerated++
    }

    private fun displayPass(sel: Selection, shown: Slot, generated: Boolean, rect: FloatArray) {
        GLES20.glUseProgram(progDisplay)
        GLES20.glUniform4f(uniform(progDisplay, "uRect"), rect[0], rect[1], rect[2], rect[3])
        val adj = adjustments
        GLES20.glUniform4f(uniform(progDisplay, "uAdj"), adj[0], adj[1], adj[2], max(0.1f, adj[3]))
        GLES20.glUniform1i(uniform(progDisplay, "uAdjOn"), if (adjustmentsActive()) 1 else 0)
        GLES20.glUniform1i(uniform(progDisplay, "uA"), 0)
        GLES20.glUniform1i(uniform(progDisplay, "uB"), 1)
        GLES20.glUniform1i(uniform(progDisplay, "uFlow"), 2)
        GLES20.glUniform1i(uniform(progDisplay, "uFlowB"), 3)
        val b = sel.b
        if (!generated || b == null) {
            bindTexture(0, shown.tex); bindTexture(1, shown.tex)
            bindTexture(2, shown.flowTex); bindTexture(3, shown.flowTex)
            GLES20.glUniform1i(uniform(progDisplay, "uMode"), 0)
            GLES20.glUniform1i(uniform(progDisplay, "uDual"), 0)
            GLES20.glUniform1f(uniform(progDisplay, "uT"), 0f)
        } else {
            val motion = mode == FrameGenMode.MOTION && motionSupported && b.flowValid
            val dual = motion && activeQuality.bidirectional && b.bwdValid && b.bwdTex != 0
            bindTexture(0, sel.a.tex); bindTexture(1, b.tex)
            bindTexture(2, b.flowTex); bindTexture(3, if (dual) b.bwdTex else b.flowTex)
            GLES20.glUniform1i(uniform(progDisplay, "uMode"), if (motion) 2 else 1)
            GLES20.glUniform1i(uniform(progDisplay, "uDual"), if (dual) 1 else 0)
            GLES20.glUniform1f(uniform(progDisplay, "uT"), sel.t)
            lastActiveMode = when {
                dual -> "Bewegung (bidirektional)"
                motion -> "Bewegung"
                else -> "Überblendung"
            }
        }
        drawQuad()
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
        val q = activeQuality
        listener?.onRenderStats(
            RenderStats(
                src, out, pct, active, frameW, frameH, fineW, fineH, !arrivalMode,
                q.label, q != quality, q.bidirectional,
                if (progUpscale != 0) upscaler.label else "nicht verfügbar",
                lastUpscaleFactor, (lastLagNs / 1_000_000L).toInt()
            )
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
        private const val EARLY_RELEASE_NS = 38_000_000.0
        private const val SLOWMO_SPEED = 0.75f
        private const val SLOWMO_PASSES_PER_FRAME = 3
        private const val MAX_LEVELS = 16
        private const val MIN_INTERP_NS = 3_000_000L
        private const val TIMESTAMP_TOLERANCE_NS = 2_000_000_000L
    }
}
