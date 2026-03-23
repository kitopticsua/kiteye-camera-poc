package com.kitoptics.thermalview.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for YUYV→RGB conversion math (ITU-R BT.601).
 * These test the same formula used in the GLSL fragment shader.
 * GL compilation is device-only (no GL context in unit tests).
 */
class ShaderProgramTest {

    private val converter = YuvConverter()

    // UT-02: Pure white YUYV (Y=255, U=128, V=128) → white RGB
    @Test
    fun `pure white YUYV converts to white RGB`() {
        val rgb = converter.yuyvToRgb(y = 255, u = 128, v = 128)
        assertEquals(1.0f, rgb.r, 0.05f)
        assertEquals(1.0f, rgb.g, 0.05f)
        assertEquals(1.0f, rgb.b, 0.05f)
    }

    // UT-03: Pure black YUYV (Y=0, U=128, V=128) → black RGB
    @Test
    fun `pure black YUYV converts to black RGB`() {
        val rgb = converter.yuyvToRgb(y = 0, u = 128, v = 128)
        assertEquals(0.0f, rgb.r, 0.05f)
        assertEquals(0.0f, rgb.g, 0.05f)
        assertEquals(0.0f, rgb.b, 0.05f)
    }

    // UT-04: White-hot mode — high Y (bright pixel) → high luminance
    @Test
    fun `white-hot mode high Y gives bright pixel`() {
        val bright = converter.yuyvToRgb(y = 200, u = 128, v = 128, invertPalette = false)
        val dark = converter.yuyvToRgb(y = 50, u = 128, v = 128, invertPalette = false)
        assertTrue("High Y should be brighter than low Y in white-hot", bright.r > dark.r)
    }

    // UT-05: Black-hot mode — high Y (hot pixel) → dark
    @Test
    fun `black-hot mode high Y gives dark pixel`() {
        val hot = converter.yuyvToRgb(y = 200, u = 128, v = 128, invertPalette = true)
        val cold = converter.yuyvToRgb(y = 50, u = 128, v = 128, invertPalette = true)
        assertTrue("High Y should be darker than low Y in black-hot", hot.r < cold.r)
    }

    // Extra: output is always clamped to [0,1]
    @Test
    fun `output RGB is always clamped to 0 to 1`() {
        for (y in listOf(0, 64, 128, 192, 255)) {
            for (u in listOf(0, 128, 255)) {
                val rgb = converter.yuyvToRgb(y, u, u)
                assertTrue(rgb.r in 0f..1f)
                assertTrue(rgb.g in 0f..1f)
                assertTrue(rgb.b in 0f..1f)
            }
        }
    }
}
