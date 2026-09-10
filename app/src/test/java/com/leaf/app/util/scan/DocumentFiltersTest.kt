package com.leaf.app.util.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentFiltersTest {

    private val w = 200
    private val h = 120

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Grey paper under a left-to-right shadow, with a dark word in the middle. */
    private fun shadowedPage(paper: Int = 170): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val shade = paper - x * 60 / w
        val ink = y in 50..60 && x in 80..120
        if (ink) rgb(20, 20, 20) else rgb(shade, shade, shade)
    }

    @Test
    fun `whitening lifts paper to near white on both the lit and shadowed side`() {
        val px = shadowedPage()
        DocumentFilters.whitenBackground(px, w, h)
        val left = DocumentFilters.luma(px[10 * w + 10])
        val right = DocumentFilters.luma(px[10 * w + (w - 10)])
        assertTrue("left $left", left >= 235)
        assertTrue("right $right", right >= 225)
    }

    @Test
    fun `whitening keeps ink dark`() {
        val px = shadowedPage()
        DocumentFilters.whitenBackground(px, w, h)
        assertTrue(DocumentFilters.luma(px[55 * w + 100]) < 80)
    }

    @Test
    fun `whitening never brightens a black frame into noise`() {
        val px = IntArray(w * h) { i -> val v = 2 + (i * 7) % 5; rgb(v, v + 1, v) }
        DocumentFilters.whitenBackground(px, w, h)
        val maxLuma = px.maxOf { DocumentFilters.luma(it) }
        assertTrue("max $maxLuma", maxLuma <= 20)
    }

    @Test
    fun `whitening keeps colour ratios`() {
        // Dark enough that the capped gain does not clip any channel.
        val px = IntArray(w * h) { rgb(80, 40, 20) }
        DocumentFilters.whitenBackground(px, w, h)
        val c = px[w * 50 + 50]
        val r = c shr 16 and 0xFF
        val g = c shr 8 and 0xFF
        val b = c and 0xFF
        assertTrue("brightened r=$r", r > 80)
        assertEquals(2.0, r.toDouble() / g, 0.1)
        assertEquals(4.0, r.toDouble() / b, 0.2)
    }

    @Test
    fun `adaptive threshold separates ink from shadowed paper`() {
        val px = shadowedPage()
        DocumentFilters.toGray(px)
        DocumentFilters.adaptiveThreshold(px, w, h)
        assertEquals(0, DocumentFilters.luma(px[55 * w + 100]))
        assertEquals(255, DocumentFilters.luma(px[10 * w + 10]))
        assertEquals(255, DocumentFilters.luma(px[100 * w + (w - 10)]))
    }

    @Test
    fun `toGray produces equal channels`() {
        val px = intArrayOf(rgb(200, 100, 50))
        DocumentFilters.toGray(px)
        val c = px[0]
        assertEquals(c shr 16 and 0xFF, c shr 8 and 0xFF)
        assertEquals(c shr 8 and 0xFF, c and 0xFF)
    }
}
