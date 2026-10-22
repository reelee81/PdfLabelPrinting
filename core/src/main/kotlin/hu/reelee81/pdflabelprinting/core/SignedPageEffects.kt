package hu.reelee81.pdflabelprinting.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object SignedPageEffects {
    const val GRAY_R = 0.213f
    const val GRAY_G = 0.715f
    const val GRAY_B = 0.072f

    const val SCAN_SATURATION = 0.95f
    const val SCAN_CONTRAST = 1.05f
    const val SCAN_BRIGHTNESS = -5f
    const val SCAN_EDGE_ALPHA = 12

    fun grayscale(rgb: ByteArray) {
        val m = saturation(0f)
        var i = 0
        while (i + 2 < rgb.size) {
            val r = u(rgb[i])
            val g = u(rgb[i + 1])
            val b = u(rgb[i + 2])
            rgb[i] = channel(m[0] * r + m[1] * g + m[2] * b).toByte()
            rgb[i + 1] = channel(m[3] * r + m[4] * g + m[5] * b).toByte()
            rgb[i + 2] = channel(m[6] * r + m[7] * g + m[8] * b).toByte()
            i += 3
        }
    }

    fun scanEffect(rgb: ByteArray, width: Int, height: Int) {
        require(width > 0 && height > 0 && rgb.size >= width * height * 3)
        scanColors(rgb)
        edgeShadow(rgb, width, height)
    }

    fun scanColors(rgb: ByteArray) {
        val m = saturation(SCAN_SATURATION)
        var i = 0
        while (i + 2 < rgb.size) {
            val r = u(rgb[i])
            val g = u(rgb[i + 1])
            val b = u(rgb[i + 2])
            for (c in 0 until 3) {
                val base = m[c * 3] * r + m[c * 3 + 1] * g + m[c * 3 + 2] * b
                rgb[i + c] = channel(SCAN_CONTRAST * base + SCAN_BRIGHTNESS).toByte()
            }
            i += 3
        }
    }

    fun edgeShadow(rgb: ByteArray, width: Int, height: Int) {
        require(width > 0 && height > 0 && rgb.size >= width * height * 3)
        val vertical = edgeFactors(height)
        val horizontal = edgeFactors(width)
        var i = 0
        for (y in 0 until height) {
            val fy = vertical[y]
            for (x in 0 until width) {
                val fx = horizontal[x]
                for (c in 0 until 3) {
                    val shadedY = channel(u(rgb[i + c]) * fy)
                    rgb[i + c] = channel(shadedY * fx).toByte()
                }
                i += 3
            }
        }
    }

    class Rgba(val width: Int, val height: Int, val premultiplied: ByteArray) {
        init {
            require(width > 0 && height > 0 && premultiplied.size >= width * height * 4)
        }
    }

    fun subsample(src: Rgba, sampleSize: Int): Rgba {
        if (sampleSize <= 1) return src
        val width = if (sampleSize > src.width) 1 else src.width / sampleSize
        val height = if (sampleSize > src.height) 1 else src.height / sampleSize
        val start = sampleSize / 2
        val out = ByteArray(width * height * 4)
        for (y in 0 until height) {
            val sy = min(start + y * sampleSize, src.height - 1)
            for (x in 0 until width) {
                val sx = min(start + x * sampleSize, src.width - 1)
                System.arraycopy(src.premultiplied, (sy * src.width + sx) * 4, out, (y * width + x) * 4, 4)
            }
        }
        return Rgba(width, height, out)
    }

    fun drawNearest(
        dst: ByteArray,
        dstWidth: Int,
        dstHeight: Int,
        src: Rgba,
        left: Float,
        top: Float,
        width: Int,
        height: Int
    ) {
        val x0 = max(0, pixelEdge(left))
        val x1 = min(dstWidth, pixelEdge(left + width))
        val y0 = max(0, pixelEdge(top))
        val y1 = min(dstHeight, pixelEdge(top + height))
        if (x0 >= x1 || y0 >= y1) return
        val scaleX = src.width.toFloat() / width
        val scaleY = src.height.toFloat() / height
        val pixels = src.premultiplied
        for (y in y0 until y1) {
            val sy = ((y + 0.5f - top) * scaleY).toInt().coerceIn(0, src.height - 1)
            var d = (y * dstWidth + x0) * 3
            for (x in x0 until x1) {
                val sx = ((x + 0.5f - left) * scaleX).toInt().coerceIn(0, src.width - 1)
                val s = (sy * src.width + sx) * 4
                val a = u(pixels[s + 3])
                if (a != 0) {
                    val keep = 255 - a
                    for (c in 0 until 3) {
                        dst[d + c] = min(255, u(pixels[s + c]) + (u(dst[d + c]) * keep + 127) / 255).toByte()
                    }
                }
                d += 3
            }
        }
    }

    private fun pixelEdge(v: Float): Int = floor(v + 0.5f).toInt()

    private fun saturation(sat: Float): FloatArray {
        val inv = 1f - sat
        val r = GRAY_R * inv
        val g = GRAY_G * inv
        val b = GRAY_B * inv
        return floatArrayOf(
            r + sat, g, b,
            r, g + sat, b,
            r, g, b + sat
        )
    }

    private fun edgeFactors(size: Int): FloatArray = FloatArray(size) {
        val t = ((it + 0.5f) / size).coerceIn(0f, 1f)
        1f - SCAN_EDGE_ALPHA * (1f - t) / 255f
    }

    private fun u(b: Byte): Int = b.toInt() and 0xFF

    private fun channel(v: Float): Int = (v.coerceIn(0f, 255f) + 0.5f).toInt()
}
