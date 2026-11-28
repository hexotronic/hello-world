package com.hexotronic.fluxplayer.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs

/**
 * GLSurfaceView mit dem Frame-Generierungs-Renderer und der kompletten Gestensteuerung
 * (Tippen, Doppeltippen, Wischen für Spulen/Helligkeit/Lautstärke, Pinch-Zoom, Verschieben, langes Drücken).
 */
@SuppressLint("ViewConstructor")
class VideoSurfaceView(context: Context) : GLSurfaceView(context) {

    enum class SwipeKind { SEEK, BRIGHTNESS, VOLUME, PAN }

    interface GestureListener {
        fun isControlsLocked(): Boolean
        fun isVideoZoomed(): Boolean
        fun onSingleTap()
        fun onDoubleTap(x: Float, width: Int)
        fun onLongPressStart()
        fun onLongPressEnd()
        fun onSwipeStart(kind: SwipeKind)
        fun onSwipe(kind: SwipeKind, totalDx: Float, totalDy: Float)
        fun onSwipeEnd(kind: SwipeKind)
        fun onPan(dx: Float, dy: Float)
        fun onScaleStart()
        fun onScale(factor: Float, focusX: Float, focusY: Float)
        fun onScaleEnd()
    }

    val renderer = FrameGenRenderer()
    var gestureListener: GestureListener? = null

    private var swipeKind: SwipeKind? = null
    private var multiTouch = false
    private var longPressing = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            gestureListener?.onSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val l = gestureListener ?: return false
            if (l.isControlsLocked()) return false
            l.onDoubleTap(e.x, width)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val l = gestureListener ?: return
            if (l.isControlsLocked() || multiTouch || swipeKind != null) return
            longPressing = true
            l.onLongPressStart()
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val l = gestureListener ?: return false
            if (l.isControlsLocked() || multiTouch || scaleDetector.isInProgress || e2.pointerCount > 1) return false
            val start = e1 ?: return false
            val totalDx = e2.x - start.x
            val totalDy = e2.y - start.y
            var kind = swipeKind
            if (kind == null) {
                // Wischen vom oberen/unteren Rand gehört dem System
                if (start.y < height * 0.06f || start.y > height * 0.96f) return false
                kind = when {
                    l.isVideoZoomed() -> SwipeKind.PAN
                    abs(totalDx) > abs(totalDy) -> SwipeKind.SEEK
                    start.x < width / 2f -> SwipeKind.BRIGHTNESS
                    else -> SwipeKind.VOLUME
                }
                swipeKind = kind
                l.onSwipeStart(kind)
            }
            if (kind == SwipeKind.PAN) {
                l.onPan(-distanceX, -distanceY)
            } else {
                l.onSwipe(kind, totalDx, totalDy)
            }
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            val l = gestureListener ?: return false
            if (l.isControlsLocked()) return false
            endSwipe()
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            l.onScaleStart()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val l = gestureListener ?: return false
            l.onScale(detector.scaleFactor, detector.focusX, detector.focusY)
            l.onPan(detector.focusX - lastFocusX, detector.focusY - lastFocusY)
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            gestureListener?.onScaleEnd()
        }
    }).apply {
        isQuickScaleEnabled = false
    }

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        preserveEGLContextOnPause = true
        renderer.renderRequester = { requestRender() }
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setContinuous(continuous: Boolean) {
        val mode = if (continuous) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        if (renderMode != mode) renderMode = mode
        requestRender()
    }

    private fun endSwipe() {
        val k = swipeKind
        if (k != null) {
            swipeKind = null
            gestureListener?.onSwipeEnd(k)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouch = false
                swipeKind = null
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                endSwipe()
            }
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endSwipe()
                if (longPressing) {
                    longPressing = false
                    gestureListener?.onLongPressEnd()
                }
            }
        }
        return true
    }
}
