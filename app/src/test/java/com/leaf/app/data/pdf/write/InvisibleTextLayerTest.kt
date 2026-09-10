package com.leaf.app.data.pdf.write

import com.leaf.app.util.ocr.OcrLine
import com.leaf.app.util.ocr.OcrPage
import com.leaf.app.util.ocr.OcrWord
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The invisible layer must come back out of a stock text extractor as the words that went
 * in, in reading order, at the positions the OCR boxes describe.
 */
class InvisibleTextLayerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A 2000 x 2828 px image drawn to fill an A4 page. */
    private val ptPerPx = PDRectangle.A4.width / 2000f

    private fun page(): OcrPage = OcrPage(
        widthPx = 2000,
        heightPx = 2828,
        lines = listOf(
            OcrLine(listOf(OcrWord("Ugovor", 200, 300, 620, 380), OcrWord("o", 650, 300, 700, 380), OcrWord("zakupu", 730, 300, 1150, 380))),
            OcrLine(listOf(OcrWord("Član", 200, 500, 420, 560), OcrWord("1", 450, 500, 500, 560))),
            OcrLine(listOf(OcrWord("Договор", 200, 700, 700, 760), OcrWord("日本語", 750, 700, 1000, 760))),
        ),
    )

    private fun writeDocument(): File {
        val out = File(tmp.root, "ocr.pdf")
        PDDocument().use { doc ->
            val pdPage = PDPage(PDRectangle.A4)
            doc.addPage(pdPage)
            InvisibleTextLayer(doc).write(pdPage, page(), imageLeftPt = 0f, imageBottomPt = 0f, ptPerPx = ptPerPx)
            doc.save(out)
        }
        return out
    }

    @Test
    fun `text is extractable in reading order across scripts`() {
        val out = writeDocument()
        val text = PDDocument.load(out).use { doc ->
            PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(listOf("Ugovor o zakupu", "Član 1", "Договор 日本語"), lines)
    }

    @Test
    fun `words land where their boxes were`() {
        val out = writeDocument()
        val positions = mutableListOf<TextPosition>()
        PDDocument.load(out).use { doc ->
            object : PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    positions += text
                    super.processTextPosition(text)
                }
            }.apply { sortByPosition = true }.getText(doc)
        }
        val first = positions.first { it.unicode == "U" }
        // x of the first glyph is the left edge of the first box; the baseline (measured from
        // the top of the page) is the bottom edge of the box.
        assertEquals(200 * ptPerPx, first.xDirAdj, 1.5f)
        assertEquals(380 * ptPerPx, first.yDirAdj, 1.5f)
        val cyrillic = positions.first { it.unicode == "Д" }
        assertTrue("Cyrillic line is below the title", cyrillic.yDirAdj > first.yDirAdj)
    }

    @Test
    fun `empty recognition adds nothing`() {
        val out = File(tmp.root, "empty.pdf")
        PDDocument().use { doc ->
            val pdPage = PDPage(PDRectangle.A4)
            doc.addPage(pdPage)
            InvisibleTextLayer(doc).write(pdPage, OcrPage(10, 10, emptyList()), 0f, 0f, 1f)
            doc.save(out)
        }
        PDDocument.load(out).use { doc ->
            assertEquals("", PDFTextStripper().getText(doc).trim())
            assertTrue(doc.getPage(0).resources?.fontNames?.none() ?: true)
        }
    }
}
