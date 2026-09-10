package com.leaf.app.data.pdf.write

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

/**
 * Golden-file tests. Each fixture page has MediaBox height 700 + n, so page order is
 * verified from geometry, independent of text extraction.
 */
class PdfBoxEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var engine: PdfBoxEngine

    @Before
    fun setUp() {
        engine = PdfBoxEngine(maxMainMemoryBytes = 8L * 1024 * 1024, scratchDir = tmp.newFolder("scratch"))
    }

    private fun fixture(name: String): File {
        val url = checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }
        return File(url.toURI())
    }

    /** 1-based source page numbers of each output page, read from the MediaBox heights. */
    private fun pageOrder(file: File, password: String? = null): List<Int> =
        PDDocument.load(file, password ?: "").use { doc -> doc.pages.map { (it.mediaBox.height - 700).toInt() } }

    private fun rotations(file: File): List<Int> = PDDocument.load(file).use { doc -> doc.pages.map { it.rotation } }

    private fun out(name: String) = File(tmp.root, name)

    @Test
    fun mergeKeepsSourceOrderAndCounts() {
        val target = out("merged.pdf")
        val pages = engine.merge(
            listOf(
                PdfBoxEngine.MergeInput(fixture("three_pages.pdf"), null, null),
                PdfBoxEngine.MergeInput(fixture("five_pages.pdf"), null, null),
            ),
            target,
        )
        assertEquals(8, pages)
        engine.verify(target, 8)
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 5), pageOrder(target))
    }

    @Test
    fun mergeHonoursPageRanges() {
        val target = out("merged_ranges.pdf")
        val pages = engine.merge(
            listOf(
                PdfBoxEngine.MergeInput(fixture("five_pages.pdf"), null, listOf(3..4)),
                PdfBoxEngine.MergeInput(fixture("three_pages.pdf"), null, listOf(0..0, 2..2)),
            ),
            target,
        )
        assertEquals(4, pages)
        assertEquals(listOf(4, 5, 1, 3), pageOrder(target))
    }

    @Test
    fun organiseReordersDeletesRotatesAndDuplicates() {
        val target = out("organised.pdf")
        val plan = PagePlan(
            listOf(
                PageOp(4),
                PageOp(0, rotationDelta = 90),
                PageOp(0),
                PageOp(2, rotationDelta = 270),
            ),
        )
        val pages = engine.organise(fixture("five_pages.pdf"), null, plan, target)
        assertEquals(4, pages)
        engine.verify(target, 4)
        assertEquals(listOf(5, 1, 1, 3), pageOrder(target))
        assertEquals(listOf(0, 90, 0, 270), rotations(target))
    }

    @Test
    fun organiseAddsToExistingRotation() {
        val target = out("rotated_more.pdf")
        engine.organise(fixture("rotated.pdf"), null, PagePlan(listOf(PageOp(1, 90), PageOp(1, 270))), target)
        assertEquals(listOf(180, 0), rotations(target))
    }

    @Test
    fun organiseIdentityPreservesEverything() {
        val target = out("identity.pdf")
        engine.organise(fixture("five_pages.pdf"), null, PagePlan.identity(5), target)
        assertEquals(listOf(1, 2, 3, 4, 5), pageOrder(target))
    }

    @Test
    fun organiseRejectsEmptyPlan() {
        try {
            engine.organise(fixture("five_pages.pdf"), null, PagePlan(emptyList()), out("empty.pdf"))
            fail("expected failure")
        } catch (e: DocOperationException) {
            assertTrue(e.error is OperationError.Unknown)
        }
    }

    @Test
    fun splitByRangesWritesOneFilePerRange() {
        val dir = tmp.newFolder("split")
        val files = engine.split(fixture("five_pages.pdf"), null, listOf(0..1, 2..2, 3..4), dir) { r -> "part_${r.first}-${r.last}.pdf" }
        assertEquals(listOf("part_1-2.pdf", "part_3-3.pdf", "part_4-5.pdf"), files.map { it.name })
        assertEquals(listOf(1, 2), pageOrder(files[0]))
        assertEquals(listOf(3), pageOrder(files[1]))
        assertEquals(listOf(4, 5), pageOrder(files[2]))
    }

    @Test
    fun splitRejectsRangeOutsideDocument() {
        try {
            engine.split(fixture("three_pages.pdf"), null, listOf(2..5), tmp.newFolder("bad")) { "x.pdf" }
            fail("expected failure")
        } catch (e: DocOperationException) {
            assertTrue(e.error is OperationError.Unknown)
        }
    }

    @Test
    fun corruptSourceFailsCleanly() {
        try {
            engine.pageCount(fixture("corrupt.pdf"), null)
            fail("expected failure")
        } catch (e: DocOperationException) {
            assertEquals(OperationError.Corrupt, e.error)
        }
    }

    @Test
    fun encryptedSourceNeedsPassword() {
        try {
            engine.pageCount(fixture("encrypted.pdf"), null)
            fail("expected failure")
        } catch (e: DocOperationException) {
            assertEquals(OperationError.PasswordRequired, e.error)
        }
        assertEquals(5, engine.pageCount(fixture("encrypted.pdf"), "user1"))
        assertEquals(listOf(1, 2, 3, 4, 5), pageOrder(fixture("encrypted.pdf"), "user1"))
    }

    @Test
    fun passwordRoundTrip() {
        val locked = out("locked.pdf")
        assertEquals(5, engine.setPassword(fixture("five_pages.pdf"), null, "secret", locked))
        try {
            engine.pageCount(locked, null)
            fail("expected password requirement")
        } catch (e: DocOperationException) {
            assertEquals(OperationError.PasswordRequired, e.error)
        }
        assertEquals(listOf(1, 2, 3, 4, 5), pageOrder(locked, "secret"))

        val unlocked = out("unlocked.pdf")
        assertEquals(5, engine.setPassword(locked, "secret", null, unlocked))
        assertEquals(listOf(1, 2, 3, 4, 5), pageOrder(unlocked))
    }

    /**
     * Stamp and watermark edit the loaded document in place. PdfBox refuses to save one it
     * decrypted unless protection is re-applied; the fonts and bitmaps those operations
     * need are Android-only, so the shared re-protection step is exercised on its own.
     */
    @Test
    fun editedEncryptedDocumentSavesAndStaysLocked() {
        engine.open(fixture("encrypted.pdf"), "user1").use { doc ->
            try {
                doc.save(out("unprotected.pdf"))
                fail("PdfBox should refuse to save a decrypted document as-is")
            } catch (_: IllegalStateException) {
                // The failure this fix is for.
            }
            engine.keepProtection(doc, "user1")
            doc.save(out("relocked.pdf"))
        }
        try {
            engine.pageCount(out("relocked.pdf"), null)
            fail("output should still need the password")
        } catch (e: DocOperationException) {
            assertEquals(OperationError.PasswordRequired, e.error)
        }
        engine.verify(out("relocked.pdf"), 5, "user1")
        assertEquals(listOf(1, 2, 3, 4, 5), pageOrder(out("relocked.pdf"), "user1"))
    }

    /** Same step on a lock made by this engine (AES-256, opened with owner rights). */
    @Test
    fun editedDocumentLockedHereStaysLocked() {
        val locked = out("locked_for_edit.pdf")
        engine.setPassword(fixture("three_pages.pdf"), null, "secret", locked)
        val target = out("relocked_own.pdf")
        engine.open(locked, "secret").use { doc ->
            engine.keepProtection(doc, "secret")
            doc.save(target)
        }
        engine.verify(target, 3, "secret")
        assertEquals(listOf(1, 2, 3), pageOrder(target, "secret"))
    }

    @Test
    fun unencryptedDocumentIsLeftAlone() {
        val target = out("still_open.pdf")
        engine.open(fixture("three_pages.pdf"), null).use { doc ->
            engine.keepProtection(doc, null)
            doc.save(target)
        }
        assertEquals(listOf(1, 2, 3), pageOrder(target))
    }

    @Test
    fun verifyDetectsPageCountMismatch() {
        val target = out("verify.pdf")
        engine.organise(fixture("five_pages.pdf"), null, PagePlan(listOf(PageOp(0))), target)
        try {
            engine.verify(target, expectedPages = 5)
            fail("expected mismatch")
        } catch (e: DocOperationException) {
            assertTrue(e.error is OperationError.Unknown)
        }
    }

    @Test
    fun outlineFixtureSurvivesMerge() {
        val target = out("with_outline.pdf")
        engine.merge(listOf(PdfBoxEngine.MergeInput(fixture("outline.pdf"), null, null)), target)
        assertEquals(listOf(1, 2, 3, 4, 5), pageOrder(target))
        PDDocument.load(target).use { doc ->
            val outline = doc.documentCatalog.documentOutline
            assertTrue("outline should be carried over", outline != null && outline.hasChildren())
        }
    }

    @Test
    fun compressKeepsPagesWhenThereAreNoPictures() {
        val target = out("compressed.pdf")
        val pages = engine.compress(fixture("five_pages.pdf"), null, CompressLevel.STRONG, target)
        assertEquals(5, pages)
        engine.verify(target, 5)
        assertEquals(listOf(1, 2, 3, 4, 5), pageOrder(target))
    }

    @Test
    fun compressKeepsEncryptedDocumentProtected() {
        val target = out("compressed_encrypted.pdf")
        val pages = engine.compress(fixture("encrypted.pdf"), "user1", CompressLevel.BALANCED, target)
        assertEquals(pageOrder(fixture("encrypted.pdf"), "user1").size, pages)
        engine.verify(target, pages, "user1")
    }

    // ---- Page numbers ----

    /** Text of one 1-based page, whitespace collapsed. */
    private fun pageText(file: File, page: Int, password: String? = null): String =
        PDDocument.load(file, password ?: "").use { doc ->
            PDFTextStripper().apply { startPage = page; endPage = page }.getText(doc).replace(Regex("\\s+"), " ").trim()
        }

    @Test
    fun pageNumbersKeepPagesAndPrintOneNumberPerPage() {
        val target = out("numbered.pdf")
        val pages = engine.pageNumbers(fixture("five_pages.pdf"), null, PageNumberPosition.BOTTOM_CENTRE, 1, "{n}", target)
        assertEquals(5, pages)
        engine.verify(target, 5)
        assertEquals(listOf(1, 2, 3, 4, 5), pageOrder(target))
        for (n in 1..3) {
            val text = pageText(target, n)
            // The fixture already says "PAGE n"; the number must appear on its own as well.
            assertTrue("page $n text was: $text", text.split(" ").contains(n.toString()))
        }
    }

    @Test
    fun pageNumbersHonourStartAndTotalFormat() {
        val target = out("numbered_total.pdf")
        engine.pageNumbers(fixture("three_pages.pdf"), null, PageNumberPosition.TOP_RIGHT, 7, "{n} / {total}", target)
        assertTrue(pageText(target, 1).contains("7 / 9"))
        assertTrue(pageText(target, 3).contains("9 / 9"))
    }

    @Test
    fun pageNumbersOnRotatedPagesStillWrite() {
        val target = out("numbered_rotated.pdf")
        val pages = engine.pageNumbers(fixture("rotated.pdf"), null, PageNumberPosition.BOTTOM_RIGHT, 1, "{n}", target)
        assertEquals(rotations(fixture("rotated.pdf")), rotations(target))
        assertTrue(pageText(target, 1).split(" ").contains("1"))
        assertEquals(pages, rotations(target).size)
    }

    @Test
    fun pageNumbersKeepEncryptedDocumentProtected() {
        val target = out("numbered_encrypted.pdf")
        val pages = engine.pageNumbers(fixture("encrypted.pdf"), "user1", PageNumberPosition.BOTTOM_CENTRE, 1, "{n}", target)
        assertEquals(5, pages)
        try {
            engine.pageCount(target, null)
            fail("output should still need the password")
        } catch (e: DocOperationException) {
            assertEquals(OperationError.PasswordRequired, e.error)
        }
        engine.verify(target, 5, "user1")
        assertTrue(pageText(target, 2, "user1").split(" ").contains("2"))
    }

    // ---- Redact ----

    @Test
    fun redactedPageLosesItsTextAndOthersKeepTheirs() {
        // Number the pages first so every page carries text that the stripper can find.
        val numbered = out("to_redact.pdf")
        engine.pageNumbers(fixture("five_pages.pdf"), null, PageNumberPosition.BOTTOM_CENTRE, 1, "{n}", numbered)
        assertTrue(pageText(numbered, 2).contains("PAGE 2"))

        val target = out("redacted.pdf")
        val pages = engine.redact(numbered, null, mapOf(1 to fixture("black.jpg"), 3 to fixture("black.jpg")), target)
        assertEquals(5, pages)
        engine.verify(target, 5)
        assertEquals("", pageText(target, 2))
        assertEquals("", pageText(target, 4))
        assertTrue(pageText(target, 1).contains("PAGE 1"))
        assertTrue(pageText(target, 3).contains("PAGE 3"))
        assertTrue(pageText(target, 5).contains("PAGE 5"))
        PDDocument.load(target).use { doc ->
            // The swapped page shows exactly one image and nothing else.
            val page = doc.getPage(1)
            assertEquals(1, page.resources.xObjectNames.count())
            assertTrue(page.resources.fontNames.none())
            assertEquals(0, page.annotations.size)
            // Untouched pages keep their geometry, so order is still readable from the boxes.
            assertEquals(701f, doc.getPage(0).mediaBox.height)
            assertEquals(705f, doc.getPage(4).mediaBox.height)
        }
    }

    @Test
    fun redactKeepsEncryptedDocumentProtected() {
        val target = out("redacted_encrypted.pdf")
        val pages = engine.redact(fixture("encrypted.pdf"), "user1", mapOf(0 to fixture("black.jpg")), target)
        assertEquals(5, pages)
        engine.verify(target, 5, "user1")
        assertEquals("", pageText(target, 1, "user1"))
    }

    @Test
    fun redactRejectsMissingPage() {
        try {
            engine.redact(fixture("three_pages.pdf"), null, mapOf(3 to fixture("black.jpg")), out("bad_redact.pdf"))
            fail("expected failure")
        } catch (e: DocOperationException) {
            assertTrue(e.error is OperationError.Unknown)
        }
    }

    // ---- Write a note ----

    /** The font the app bundles, read from the module's assets; unit tests run with the module as working directory. */
    private fun noteFont(): () -> InputStream {
        val file = listOf("src/main/assets/fonts/DejaVuSans.ttf", "app/src/main/assets/fonts/DejaVuSans.ttf")
            .map(::File).firstOrNull { it.exists() }
        checkNotNull(file) { "DejaVuSans.ttf is missing from the assets" }
        return { file.inputStream() }
    }

    private fun allText(file: File): String =
        PDDocument.load(file).use { doc -> PDFTextStripper().getText(doc).replace(Regex("\\s+"), " ").trim() }

    @Test
    fun noteWithLatinAndCyrillicTextIsWrittenAndReadable() {
        val target = out("note.pdf")
        val body = "Rent is due on the first of the month.\n\nЗакупац плаћа закупнину до првог у месецу.\nČćžšđ i još malo teksta."
        val pages = engine.textToPdf("Уговор о закупу", body, 12f, noteFont(), target)
        assertTrue(pages >= 1)
        engine.verify(target, pages)
        val text = allText(target)
        assertTrue("text was: $text", text.contains("Уговор о закупу"))
        assertTrue("text was: $text", text.contains("Rent is due on the first of the month."))
        assertTrue("text was: $text", text.contains("Закупац плаћа закупнину"))
        assertTrue("text was: $text", text.contains("Čćžšđ"))
        PDDocument.load(target).use { doc -> assertEquals(PDRectangle.A4.width, doc.getPage(0).mediaBox.width) }
    }

    @Test
    fun longNoteBreaksIntoSeveralPages() {
        val target = out("long_note.pdf")
        val body = (1..500).joinToString("\n") { "Line $it of a long note that keeps going" }
        val pages = engine.textToPdf("", body, 12f, noteFont(), target)
        assertTrue("pages: $pages", pages > 1)
        engine.verify(target, pages)
        val text = allText(target)
        assertTrue(text.contains("Line 1 of a long note"))
        assertTrue(text.contains("Line 500 of a long note"))
    }

    @Test
    fun longWordsWrapInsteadOfRunningOffThePage() {
        val target = out("wide_note.pdf")
        val word = "x".repeat(400)
        val pages = engine.textToPdf("", word, 12f, noteFont(), target)
        assertEquals(1, pages)
        // The stripper joins the pieces on one line; every character must have made it onto the page.
        assertEquals(400, allText(target).count { it == 'x' })
    }

    @Test
    fun emptyNoteIsRejected() {
        try {
            engine.textToPdf("  ", "\n\n", 12f, noteFont(), out("empty_note.pdf"))
            fail("expected failure")
        } catch (e: DocOperationException) {
            assertTrue(e.error is OperationError.Unknown)
        }
    }
}
