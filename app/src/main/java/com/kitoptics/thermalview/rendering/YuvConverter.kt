package com.kitoptics.thermalview.rendering

/**
 * YUYV→RGB conversion using ITU-R BT.601 coefficients.
 * Same formula as the GLSL fragment shader — testable without GL context.
 */
data class Rgb(val r: Float, val g: Float, val b: Float)

class YuvConverter {

    /**
     * Convert a single YUYV pixel to RGB.
     *
     * @param y  Luma [0..255]
     * @param u  Cb chrominance [0..255], neutral=128
     * @param v  Cr chrominance [0..255], neutral=128
     * @param invertPalette false=White-hot, true=Black-hot
     */
    fun yuyvToRgb(y: Int, u: Int, v: Int, invertPalette: Boolean = false): Rgb {
        val yf = y / 255f
        val uf = u / 255f - 0.5f
        val vf = v / 255f - 0.5f

        var r = yf + 1.402f * vf
        var g = yf - 0.344f * uf - 0.714f * vf
        var b = yf + 1.772f * uf

        if (invertPalette) {
            r = 1f - r
            g = 1f - g
            b = 1f - b
        }

        return Rgb(
            r = r.coerceIn(0f, 1f),
            g = g.coerceIn(0f, 1f),
            b = b.coerceIn(0f, 1f)
        )
    }
}
