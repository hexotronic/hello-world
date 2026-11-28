package com.hexotronic.fluxplayer

import com.hexotronic.fluxplayer.data.AspectMode
import com.hexotronic.fluxplayer.render.ZoomState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStateTest {
    private fun state(): ZoomState = ZoomState().apply {
        setViewSize(2000f, 1000f)
        setVideoSize(1920f, 1080f)
    }

    @Test
    fun fitLetterboxesWideView() {
        val r = state().rect()
        // 16:9 in 2:1-View -> Höhe voll, Breite 1777.8, zentriert
        assertEquals(1000f, r.height, 0.5f)
        assertEquals(1777.8f, r.width, 0.5f)
        assertEquals(111.1f, r.left, 0.5f)
    }

    @Test
    fun fillCoversView() {
        val z = state().apply { setAspect(AspectMode.FILL) }
        val r = z.rect()
        assertEquals(2000f, r.width, 0.5f)
        assertTrue(r.height >= 1000f)
    }

    @Test
    fun stretchMatchesView() {
        val z = state().apply { setAspect(AspectMode.STRETCH) }
        val r = z.rect()
        assertEquals(0f, r.left, 0.01f)
        assertEquals(2000f, r.width, 0.01f)
        assertEquals(1000f, r.height, 0.01f)
    }

    @Test
    fun customRatio() {
        val z = state().apply { setAspect(AspectMode.R4_3) }
        val r = z.rect()
        assertEquals(4f / 3f, r.width / r.height, 0.001f)
    }

    @Test
    fun zoomKeepsFocusPointFixed() {
        val z = state()
        val before = z.rect()
        val fx = 1500f
        val fy = 300f
        // relative Position des Fokuspunkts im Video
        val u = (fx - before.left) / before.width
        val v = (fy - before.top) / before.height
        z.zoomBy(2f, fx, fy)
        val after = z.rect()
        assertEquals(2f, z.scale, 0.0001f)
        assertEquals(fx, after.left + u * after.width, 0.5f)
        assertEquals(fy, after.top + v * after.height, 0.5f)
    }

    @Test
    fun panIsClampedToVideoEdges() {
        val z = state()
        z.zoomBy(2f, 1000f, 500f)
        z.panBy(100000f, 100000f)
        val r = z.rect()
        // Videorand darf nicht in die View hineinwandern
        assertEquals(0f, r.left, 0.5f)
        assertEquals(0f, r.top, 0.5f)
        z.panBy(-100000f, -100000f)
        val r2 = z.rect()
        assertEquals(2000f, r2.right, 0.5f)
        assertEquals(1000f, r2.bottom, 0.5f)
    }

    @Test
    fun smallVideoStaysCentered() {
        val z = state()
        z.setScale(0.5f)
        z.panBy(300f, 300f)
        val r = z.rect()
        assertEquals(1000f, (r.left + r.right) / 2f, 0.5f)
        assertEquals(500f, (r.top + r.bottom) / 2f, 0.5f)
    }

    @Test
    fun scaleIsClamped() {
        val z = state()
        z.zoomBy(1000f, 0f, 0f)
        assertEquals(ZoomState.MAX_SCALE, z.scale, 0.0001f)
        z.zoomBy(0.00001f, 0f, 0f)
        assertEquals(ZoomState.MIN_SCALE, z.scale, 0.0001f)
    }
}
