package com.leaf.app.util.saf

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputNamesTest {

    @Test
    fun stripsPdfExtensionCaseInsensitively() {
        assertEquals("report", OutputNames.stripExtension("report.pdf"))
        assertEquals("report", OutputNames.stripExtension("report.PDF"))
        assertEquals("notes.txt", OutputNames.stripExtension("notes.txt"))
        assertEquals("report", OutputNames.stripExtension("  report.pdf  "))
    }

    @Test
    fun sanitizeReplacesIllegalCharactersAndNeverReturnsEmpty() {
        assertEquals("a_b_c_d", OutputNames.sanitize("a/b\\c:d"))
        assertEquals("document", OutputNames.sanitize(""))
        assertEquals("document", OutputNames.sanitize("..."))
        assertEquals("q_uote", OutputNames.sanitize("q\"uote"))
    }

    @Test
    fun sanitizeBoundsLength() {
        val long = "x".repeat(500)
        assertEquals(120, OutputNames.sanitize(long).length)
    }

    @Test
    fun buildUsesDefaultPattern() {
        assertEquals("report_merged.pdf", OutputNames.build("{name}_{op}", "report.pdf", "merged"))
        assertEquals("report_pages_1-4.pdf", OutputNames.build("{name}_{op}", "report.pdf", "pages_1-4"))
    }

    @Test
    fun buildFallsBackWhenPatternIsBlank() {
        assertEquals("report_split.pdf", OutputNames.build("   ", "report.pdf", "split"))
    }

    @Test
    fun buildAppendsOpWhenPatternOmitsIt() {
        assertEquals("quire_report_merged.pdf", OutputNames.build("quire_{name}", "report.pdf", "merged"))
    }

    @Test
    fun buildSupportsOtherExtensions() {
        assertEquals("scan_page_003.png", OutputNames.build("{name}_{op}", "scan.pdf", "page_003", extension = "png"))
    }

    @Test
    fun pagesOpFormatsRanges() {
        assertEquals("pages_1-4", OutputNames.pagesOp(listOf(1..4)))
        assertEquals("pages_1-4_8_12-20", OutputNames.pagesOp(listOf(1..4, 8..8, 12..20)))
    }

    @Test
    fun pageNumberLabelPadsToPageCountWidth() {
        assertEquals("007", OutputNames.pageNumberLabel(7, 120))
        assertEquals("7", OutputNames.pageNumberLabel(7, 9))
        assertEquals("0042", OutputNames.pageNumberLabel(42, 1000))
    }
}
