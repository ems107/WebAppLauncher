package es.edgarms.weblauncher.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTest {
    @Test
    fun `at 100 percent the page gets the view's own width at scale 1`() {
        assertEquals(Viewport.Layout(450, 1f), Viewport.layoutFor(450, desktop = false, zoom = 100))
    }

    @Test
    fun `desktop lays out at 1280 and scales it to fit`() {
        val layout = Viewport.layoutFor(450, desktop = true, zoom = 100)
        assertEquals(1280, layout.width)
        assertEquals(450f / 1280, layout.scale, 0.0001f)
    }

    @Test
    fun `zooming in narrows the layout and the scale still fits it across`() {
        val layout = Viewport.layoutFor(450, desktop = false, zoom = 200)
        assertEquals(225, layout.width)
        assertEquals(2f, layout.scale, 0.0001f)
    }

    @Test
    fun `zooming out inside desktop mode widens it further`() {
        assertEquals(2560, Viewport.layoutFor(450, desktop = true, zoom = 50).width)
    }

    @Test
    fun `the width is never zero`() {
        assertTrue(Viewport.layoutFor(0, desktop = false, zoom = Viewport.ZOOM_MAX).width >= 1)
    }

    @Test
    fun `the script pins the scale and then frees the pinch`() {
        val script = Viewport.script(450, desktop = false, zoom = 200, pin = true)
        assertTrue(script.contains("width=225, initial-scale=2.0, minimum-scale=2.0, maximum-scale=2.0"))
        assertTrue(script.contains("minimum-scale=${Viewport.PINCH_MIN}, maximum-scale=${Viewport.PINCH_MAX}"))
    }

    @Test
    fun `after a load the scale is never pinned`() {
        val script = Viewport.script(450, desktop = false, zoom = 110, pin = false)
        assertFalse(script.contains("minimum-scale=${Viewport.layoutFor(450, false, 110).scale}"))
        assertTrue(script.contains("minimum-scale=${Viewport.PINCH_MIN}, maximum-scale=${Viewport.PINCH_MAX}"))
    }
}
