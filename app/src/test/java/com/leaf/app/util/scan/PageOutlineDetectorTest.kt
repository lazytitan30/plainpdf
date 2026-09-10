package com.leaf.app.util.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Synthetic photos: a bright page on a darker table, with grain, a soft shadow and lines
 * of "text", at various tilts. The detector must land every corner within a few percent.
 */
class PageOutlineDetectorTest {

    private val w = 400
    private val h = 300

    @Test
    fun `finds an upright page with margins`() {
        val expected = Outline(Corner(0.15f, 0.12f), Corner(0.85f, 0.12f), Corner(0.85f, 0.88f), Corner(0.15f, 0.88f))
        assertClose(expected, PageOutlineDetector.detectOrNull(photo(expected)))
    }

    @Test
    fun `finds a page photographed at an angle`() {
        val expected = Outline(Corner(0.20f, 0.10f), Corner(0.82f, 0.16f), Corner(0.78f, 0.90f), Corner(0.12f, 0.84f))
        assertClose(expected, PageOutlineDetector.detectOrNull(photo(expected)))
    }

    @Test
    fun `finds a page that nearly fills the frame`() {
        val expected = Outline(Corner(0.04f, 0.05f), Corner(0.96f, 0.04f), Corner(0.95f, 0.96f), Corner(0.05f, 0.95f))
        assertClose(expected, PageOutlineDetector.detectOrNull(photo(expected)))
    }

    @Test
    fun `finds a dark page on a light table`() {
        val expected = Outline(Corner(0.18f, 0.14f), Corner(0.80f, 0.12f), Corner(0.84f, 0.86f), Corner(0.16f, 0.88f))
        assertClose(expected, PageOutlineDetector.detectOrNull(photo(expected, page = 70, table = 200)))
    }

    @Test
    fun `flat image yields the fallback frame`() {
        val flat = GrayImage(w, h, IntArray(w * h) { 12 })
        assertNull(PageOutlineDetector.detectOrNull(flat))
        assertEquals(Outline.full(PageOutlineDetector.FALLBACK_INSET), PageOutlineDetector.detect(flat))
    }

    @Test
    fun `pure noise yields the fallback frame`() {
        val rnd = Random(7)
        val noise = GrayImage(w, h, IntArray(w * h) { rnd.nextInt(256) })
        assertEquals(Outline.full(PageOutlineDetector.FALLBACK_INSET), PageOutlineDetector.detect(noise))
    }

    @Test
    fun `tiny images do not crash`() {
        assertNull(PageOutlineDetector.detectOrNull(GrayImage(4, 4, IntArray(16))))
    }

    private fun assertClose(expected: Outline, actual: Outline?) {
        assertNotNull("no outline found", actual)
        val tolerance = 0.03f
        expected.corners().zip(actual!!.corners()).forEachIndexed { i, (e, a) ->
            assertTrue("corner $i expected $e but was $a", abs(e.x - a.x) <= tolerance && abs(e.y - a.y) <= tolerance)
        }
    }

    /** Renders a page quadrilateral with grain, a shadow gradient and text lines. */
    private fun photo(outline: Outline, page: Int = 225, table: Int = 90): GrayImage {
        val rnd = Random(1)
        val px = IntArray(w * h)
        val poly = outline.corners().map { it.x * w to it.y * h }
        for (y in 0 until h) for (x in 0 until w) {
            val insidePage = contains(poly, x + 0.5f, y + 0.5f)
            val shadow = (x.toFloat() / w) * 30f // the light falls off to the right
            var v = (if (insidePage) page else table) - shadow
            if (insidePage && isTextRow(y, outline) && x % 7 < 4 && rnd.nextInt(3) != 0) v -= 120f
            v += rnd.nextInt(-8, 9)
            px[y * w + x] = v.toInt().coerceIn(0, 255)
        }
        return GrayImage(w, h, px)
    }

    private fun isTextRow(y: Int, outline: Outline): Boolean {
        val top = (outline.tl.y + outline.tr.y) / 2 * h
        val bottom = (outline.bl.y + outline.br.y) / 2 * h
        if (y < top + 20 || y > bottom - 20) return false
        return ((y - top).toInt() % 12) < 4
    }

    private fun contains(poly: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]
            val (xj, yj) = poly[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
