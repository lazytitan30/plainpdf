package com.leaf.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangesTest {

    private fun ok(input: String, pageCount: Int = 20): List<IntRange> {
        val result = PageRanges.parse(input, pageCount)
        assertTrue("expected Ok for '$input' but got $result", result is PageRanges.Result.Ok)
        return (result as PageRanges.Result.Ok).ranges
    }

    private fun error(input: String, pageCount: Int = 20): PageRanges.Result.Error {
        val result = PageRanges.parse(input, pageCount)
        assertTrue("expected Error for '$input' but got $result", result is PageRanges.Result.Error)
        return result as PageRanges.Result.Error
    }

    @Test
    fun parsesMixedRangesAndSinglePages() {
        assertEquals(listOf(1..4, 8..8, 12..20), ok("1-4, 8, 12-20"))
    }

    @Test
    fun toleratesSpacingAndSemicolons() {
        assertEquals(listOf(1..4, 8..8), ok(" 1 - 4 ;8 "))
        assertEquals(listOf(3..3), ok("3,,"))
    }

    @Test
    fun rejectsEmptyInput() {
        assertEquals(PageRanges.ErrorKind.EMPTY, error("").kind)
        assertEquals(PageRanges.ErrorKind.EMPTY, error(" , ").kind)
    }

    @Test
    fun rejectsGarbage() {
        val e = error("1-4, abc")
        assertEquals(PageRanges.ErrorKind.BAD_TOKEN, e.kind)
        assertEquals("abc", e.token)
    }

    @Test
    fun rejectsOutOfRange() {
        assertEquals(PageRanges.ErrorKind.OUT_OF_RANGE, error("0-3").kind)
        assertEquals(PageRanges.ErrorKind.OUT_OF_RANGE, error("18-25").kind)
    }

    @Test
    fun rejectsReversedRange() {
        assertEquals(PageRanges.ErrorKind.REVERSED, error("9-4").kind)
    }

    @Test
    fun convertsToZeroBased() {
        assertEquals(listOf(0..3, 7..7), PageRanges.toZeroBased(listOf(1..4, 8..8)))
    }

    @Test
    fun countsPagesPerRange() {
        assertEquals(listOf(4, 1, 9), PageRanges.pageCountOf(listOf(1..4, 8..8, 12..20)))
    }

    @Test
    fun everyNChunksWithShortTail() {
        assertEquals(listOf(1..3, 4..6, 7..7), PageRanges.everyN(7, 3))
        assertEquals(listOf(1..5), PageRanges.everyN(5, 5))
        assertEquals(emptyList<IntRange>(), PageRanges.everyN(0, 3))
    }
}
