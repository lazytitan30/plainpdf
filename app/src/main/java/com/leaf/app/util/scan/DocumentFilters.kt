package com.leaf.app.util.scan

/**
 * The "document look" filters, on plain ARGB int arrays so they run anywhere and can be
 * unit tested. All functions work in place.
 */
object DocumentFilters {

    /** Where the paper should land after whitening, out of 255. */
    const val WHITE_TARGET = 245f

    /** Most a dark photo is brightened, so a black frame stays black instead of turning to noise. */
    const val MAX_GAIN = 2.5f

    /** How much darker than its surroundings a pixel must be to count as ink. */
    const val BW_OFFSET = 10

    /** Unsharp mask strength applied to Colour and Grayscale scans. */
    const val SHARPEN_AMOUNT = 0.6f

    fun luma(argb: Int): Int = ((argb shr 16 and 0xFF) * 299 + (argb shr 8 and 0xFF) * 587 + (argb and 0xFF) * 114) / 1000

    fun toGray(px: IntArray) {
        for (i in px.indices) {
            val g = luma(px[i])
            px[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
    }

    /**
     * Lifts the paper toward white. The local brightness over a wide window stands in for
     * the page background, and every pixel is scaled so that background lands near white.
     * One gain for all three channels keeps colours true. The background is estimated on a
     * reduced copy: it varies slowly, and a full-size integral image would cost more memory
     * than the photo itself.
     */
    fun whitenBackground(px: IntArray, w: Int, h: Int) {
        val bg = BackgroundMap(px, w, h, radiusFraction = 10)
        for (y in 0 until h) for (x in 0 until w) {
            val gain = (WHITE_TARGET / maxOf(bg.meanAt(x, y), 1f)).coerceIn(1f, MAX_GAIN)
            val c = px[y * w + x]
            val r = ((c shr 16 and 0xFF) * gain).toInt().coerceAtMost(255)
            val g = ((c shr 8 and 0xFF) * gain).toInt().coerceAtMost(255)
            val b = ((c and 0xFF) * gain).toInt().coerceAtMost(255)
            px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /** Local mean threshold, which keeps text crisp even under a shadow across the page. */
    fun adaptiveThreshold(px: IntArray, w: Int, h: Int) {
        val bg = BackgroundMap(px, w, h, radiusFraction = 40)
        for (y in 0 until h) for (x in 0 until w) {
            val v = luma(px[y * w + x])
            val out = if (v < bg.meanAt(x, y) - BW_OFFSET) 0 else 255
            px[y * w + x] = (0xFF shl 24) or (out shl 16) or (out shl 8) or out
        }
    }

    /**
     * Unsharp mask with a 3x3 blur: brings back the crispness that the camera's demosaicing,
     * the downscale and the perspective resampling each take a little of. [amount] is the
     * fraction of the blurred difference added back; 0.6 is visible without haloes.
     */
    fun sharpen(px: IntArray, w: Int, h: Int, amount: Float = SHARPEN_AMOUNT) {
        if (w < 3 || h < 3) return
        // Three rotating copies of the untouched rows above, at and below the current one,
        // so the whole image never has to be duplicated.
        var above = IntArray(w)
        var here = IntArray(w)
        var below = IntArray(w)
        System.arraycopy(px, 0, above, 0, w)
        System.arraycopy(px, w, here, 0, w)
        for (y in 1 until h - 1) {
            System.arraycopy(px, (y + 1) * w, below, 0, w)
            for (x in 1 until w - 1) {
                var r = 0; var g = 0; var b = 0
                for (dx in -1..1) {
                    val a = above[x + dx]; val m = here[x + dx]; val d = below[x + dx]
                    r += (a shr 16 and 0xFF) + (m shr 16 and 0xFF) + (d shr 16 and 0xFF)
                    g += (a shr 8 and 0xFF) + (m shr 8 and 0xFF) + (d shr 8 and 0xFF)
                    b += (a and 0xFF) + (m and 0xFF) + (d and 0xFF)
                }
                val c = here[x]
                val cr = c shr 16 and 0xFF; val cg = c shr 8 and 0xFF; val cb = c and 0xFF
                val nr = (cr + (cr - r / 9f) * amount).toInt().coerceIn(0, 255)
                val ng = (cg + (cg - g / 9f) * amount).toInt().coerceIn(0, 255)
                val nb = (cb + (cb - b / 9f) * amount).toInt().coerceIn(0, 255)
                px[y * w + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
            val recycled = above
            above = here
            here = below
            below = recycled
        }
    }

    /**
     * Mean luma over a square window, answered from an integral image built on a copy
     * shrunk by [SCALE] in each direction. Memory is 1/[SCALE]^2 of the full image and the
     * blur inherent in shrinking is harmless for a background estimate.
     */
    private class BackgroundMap(px: IntArray, private val w: Int, private val h: Int, radiusFraction: Int) {
        private val sw = (w + SCALE - 1) / SCALE
        private val sh = (h + SCALE - 1) / SCALE
        private val integral = LongArray((sw + 1) * (sh + 1))
        private val radius = (maxOf(sw, sh) / radiusFraction).coerceAtLeast(2)

        init {
            // Each small cell is the mean of its SCALE x SCALE block, sampled on a grid rather
            // than fully averaged: cheaper, and plenty for a smooth background.
            for (sy in 1..sh) {
                var row = 0L
                val y = ((sy - 1) * SCALE + SCALE / 2).coerceAtMost(h - 1)
                for (sx in 1..sw) {
                    val x = ((sx - 1) * SCALE + SCALE / 2).coerceAtMost(w - 1)
                    row += luma(px[y * w + x])
                    integral[sy * (sw + 1) + sx] = integral[(sy - 1) * (sw + 1) + sx] + row
                }
            }
        }

        fun meanAt(x: Int, y: Int): Float {
            val cx = x / SCALE
            val cy = y / SCALE
            val x0 = (cx - radius).coerceAtLeast(0)
            val y0 = (cy - radius).coerceAtLeast(0)
            val x1 = (cx + radius + 1).coerceAtMost(sw)
            val y1 = (cy + radius + 1).coerceAtMost(sh)
            val count = (x1 - x0) * (y1 - y0)
            val sum = integral[y1 * (sw + 1) + x1] - integral[y0 * (sw + 1) + x1] - integral[y1 * (sw + 1) + x0] + integral[y0 * (sw + 1) + x0]
            return sum.toFloat() / count
        }

        companion object {
            const val SCALE = 4
        }
    }
}
