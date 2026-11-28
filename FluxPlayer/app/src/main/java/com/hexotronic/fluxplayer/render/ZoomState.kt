package com.hexotronic.fluxplayer.render

import com.hexotronic.fluxplayer.data.AspectMode
import kotlin.math.max
import kotlin.math.min

/** Einfaches Rechteck (unabhängig von android.graphics für Unit-Tests). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * Zoom-, Pan- und Seitenverhältnis-Logik.
 * Alle Koordinaten in View-Pixeln, Ursprung oben links.
 */
class ZoomState {
    var aspect: AspectMode = AspectMode.FIT
        private set
    var scale: Float = 1f
        private set
    private var panX = 0f
    private var panY = 0f

    var viewWidth = 1f
        private set
    var viewHeight = 1f
        private set
    /** Darstellungsgröße des Videos (Pixel-Seitenverhältnis bereits eingerechnet). */
    private var videoWidth = 16f
    private var videoHeight = 9f

    fun setViewSize(w: Float, h: Float) {
        viewWidth = max(1f, w)
        viewHeight = max(1f, h)
        clampPan()
    }

    fun setVideoSize(w: Float, h: Float) {
        if (w > 0f && h > 0f) {
            videoWidth = w
            videoHeight = h
            clampPan()
        }
    }

    fun setAspect(mode: AspectMode) {
        aspect = mode
        scale = 1f
        panX = 0f
        panY = 0f
    }

    fun setScale(newScale: Float) {
        scale = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
        clampPan()
    }

    fun reset() {
        scale = 1f
        panX = 0f
        panY = 0f
    }

    val isZoomed: Boolean get() = scale > 1.02f

    /** Zoomt um Faktor [factor] um den Fokuspunkt (fx, fy) – der Punkt bleibt unter den Fingern. */
    fun zoomBy(factor: Float, fx: Float, fy: Float) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val f = newScale / scale
        val cx = viewWidth / 2f + panX
        val cy = viewHeight / 2f + panY
        val ncx = fx + (cx - fx) * f
        val ncy = fy + (cy - fy) * f
        scale = newScale
        panX = ncx - viewWidth / 2f
        panY = ncy - viewHeight / 2f
        clampPan()
    }

    fun panBy(dx: Float, dy: Float) {
        panX += dx
        panY += dy
        clampPan()
    }

    /** Größe des Videos bei Zoom 100 % im gewählten Modus. */
    fun baseSize(): Pair<Float, Float> {
        val viewRatio = viewWidth / viewHeight
        val ratio = aspect.ratio ?: (videoWidth / videoHeight)
        return when (aspect) {
            AspectMode.STRETCH -> viewWidth to viewHeight
            AspectMode.ORIGINAL -> videoWidth to videoHeight
            AspectMode.FILL -> if (ratio > viewRatio) viewHeight * ratio to viewHeight else viewWidth to viewWidth / ratio
            else -> if (ratio > viewRatio) viewWidth to viewWidth / ratio else viewHeight * ratio to viewHeight
        }
    }

    fun rect(): Box {
        val (bw, bh) = baseSize()
        val w = bw * scale
        val h = bh * scale
        val cx = viewWidth / 2f + panX
        val cy = viewHeight / 2f + panY
        return Box(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Rechteck normiert auf die View (für den Renderer). */
    fun normalizedRect(): FloatArray {
        val r = rect()
        return floatArrayOf(r.left / viewWidth, r.top / viewHeight, r.right / viewWidth, r.bottom / viewHeight)
    }

    private fun clampPan() {
        val (bw, bh) = baseSize()
        val w = bw * scale
        val h = bh * scale
        val maxX = max(0f, (w - viewWidth) / 2f)
        val maxY = max(0f, (h - viewHeight) / 2f)
        panX = min(maxX, max(-maxX, panX))
        panY = min(maxY, max(-maxY, panY))
    }

    companion object {
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 10f
    }
}
