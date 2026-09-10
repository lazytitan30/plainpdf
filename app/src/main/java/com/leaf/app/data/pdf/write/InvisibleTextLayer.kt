package com.leaf.app.data.pdf.write

import com.leaf.app.util.ocr.OcrPage
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import java.util.Locale

/**
 * Writes recognised text onto a page as an invisible layer, so a scanned page becomes
 * searchable and its text can be selected and copied, while the picture stays exactly as
 * it is.
 *
 * The technique is the one Tesseract's own PDF writer uses: a "glyphless" font whose every
 * character maps to one empty glyph, text drawn in rendering mode 3 (neither fill nor
 * stroke), one word at a time, horizontally stretched to the width the OCR engine measured.
 * A ToUnicode map turns the character codes back into real Unicode for copy and search, so
 * any script works and no real font has to ship in the app.
 */
class InvisibleTextLayer(private val doc: PDDocument) {

    private val font: COSDictionary by lazy { buildFont() }

    /**
     * Adds [ocr] to [page]. The recognised image occupies the page rectangle whose lower-left
     * corner is ([imageLeftPt], [imageBottomPt]) and whose scale is [ptPerPx] points per
     * image pixel, matching how the image itself was drawn.
     */
    fun write(page: PDPage, ocr: OcrPage, imageLeftPt: Float, imageBottomPt: Float, ptPerPx: Float) {
        if (ocr.isEmpty) return
        val resources = page.resources ?: PDResources().also { page.resources = it }
        val fonts = resources.cosObject.getCOSDictionary(COSName.FONT)
            ?: COSDictionary().also { resources.cosObject.setItem(COSName.FONT, it) }
        fonts.setItem(FONT_RESOURCE, font)

        val sb = StringBuilder()
        for (line in ocr.lines) {
            if (line.words.isEmpty()) continue
            // One font size per line keeps selection rectangles level across the line.
            val fontSize = ((line.bottom - line.top) * ptPerPx).coerceAtLeast(1f)
            for (word in line.words) {
                val units = word.text.length
                if (units == 0 || word.text.isBlank()) continue
                val x = imageLeftPt + word.left * ptPerPx
                val baseline = imageBottomPt + (ocr.heightPx - word.bottom) * ptPerPx
                val widthPt = (word.width * ptPerPx).coerceAtLeast(0.5f)
                // Each glyph advances DW / 1000 = 0.5 text units; stretch so the word spans its box.
                val stretch = 2f * widthPt / (fontSize * units)
                sb.append("BT 3 Tr /").append(FONT_RESOURCE.name).append(' ').append(fmt(fontSize)).append(" Tf ")
                    .append(fmt(stretch)).append(" 0 0 1 ").append(fmt(x)).append(' ').append(fmt(baseline)).append(" Tm <")
                for (ch in word.text) sb.append(String.format(Locale.ROOT, "%04X", ch.code))
                sb.append("> Tj ET\n")
            }
        }
        if (sb.isEmpty()) return
        val stream = PDStream(doc)
        stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(sb.toString().toByteArray(Charsets.US_ASCII)) }
        val existing = page.contentStreams.asSequence().toList()
        page.setContents(existing + stream)
    }

    private fun buildFont(): COSDictionary {
        val fontFile = PDStream(doc)
        fontFile.createOutputStream(COSName.FLATE_DECODE).use { it.write(GLYPHLESS_TTF) }
        fontFile.cosObject.setInt(COSName.LENGTH1, GLYPHLESS_TTF.size)

        val descriptor = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT_DESC)
            setName(COSName.FONT_NAME, FONT_NAME)
            setInt(COSName.FLAGS, 5)
            setItem(COSName.FONT_BBOX, COSArray().apply { add(COSInteger.ZERO); add(COSInteger.ZERO); add(COSInteger.get(1000)); add(COSInteger.get(1000)) })
            setInt(COSName.ASCENT, 1000)
            setInt(COSName.DESCENT, -1)
            setInt(COSName.CAP_HEIGHT, 1000)
            setInt(COSName.ITALIC_ANGLE, 0)
            setInt(COSName.STEM_V, 80)
            setItem(COSName.FONT_FILE2, fontFile)
        }

        // Every CID points at glyph 1, the one empty glyph in the font.
        val cidToGid = PDStream(doc)
        cidToGid.createOutputStream(COSName.FLATE_DECODE).use { out ->
            val chunk = ByteArray(2 * 4096) { i -> if (i % 2 == 0) 0 else 1 }
            repeat(65536 / 4096) { out.write(chunk) }
        }

        val cidFont = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.CID_FONT_TYPE2)
            setName(COSName.BASE_FONT, FONT_NAME)
            setItem(COSName.CIDSYSTEMINFO, COSDictionary().apply {
                setItem(COSName.REGISTRY, COSString("Adobe"))
                setItem(COSName.ORDERING, COSString("Identity"))
                setInt(COSName.SUPPLEMENT, 0)
            })
            setItem(COSName.FONT_DESC, descriptor)
            setInt(COSName.DW, 500)
            setItem(COSName.CID_TO_GID_MAP, cidToGid)
        }

        val toUnicode = PDStream(doc)
        toUnicode.createOutputStream(COSName.FLATE_DECODE).use { it.write(TO_UNICODE_CMAP.toByteArray(Charsets.US_ASCII)) }

        return COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.TYPE0)
            setName(COSName.BASE_FONT, FONT_NAME)
            setItem(COSName.ENCODING, COSName.IDENTITY_H)
            setItem(COSName.DESCENDANT_FONTS, COSArray().apply { add(cidFont) })
            setItem(COSName.TO_UNICODE, toUnicode)
        }
    }

    private fun fmt(v: Float): String = String.format(Locale.ROOT, "%.3f", v).trimEnd('0').trimEnd('.')

    companion object {
        private const val FONT_NAME = "GlyphLessFont"
        private val FONT_RESOURCE: COSName = COSName.getPDFName("LeafOCR")

        /** Identity mapping: each two-byte code is its own Unicode code unit. */
        private val TO_UNICODE_CMAP = """
            /CIDInit /ProcSet findresource begin
            12 dict begin
            begincmap
            /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
            /CMapName /Adobe-Identity-UCS def
            /CMapType 2 def
            1 begincodespacerange
            <0000> <ffff>
            endcodespacerange
            1 beginbfrange
            <0000> <ffff> <0000>
            endbfrange
            endcmap
            CMapName currentdict /CMap defineresource pop
            end
            end
        """.trimIndent()

        /**
         * pdf.ttf from the Tesseract project (Apache 2.0): a 572-byte TrueType font with a
         * single empty glyph. Embedded as bytes so the JVM tests need no assets.
         */
        private val GLYPHLESS_TTF: ByteArray = java.util.Base64.getDecoder().decode(
            "AAEAAAAKAIAAAwAgT1MvMlbeyJQAAAEoAAAAYGNtYXAACgA0AAABkAAAAB5nbHlmFSJBJAAAAbgAAAAYaGVhZAt48WUAAACsAAAANmhoZWEMAgQCAAAA5AAAACRobXR4BAAAAAAAAYgAAAAIbG9jYQAMAAAAAAGwAAAABm1heHAABAAFAAABCAAAACBuYW1l8usW2gAAAdAAAABLcG9zdAABAAEAAAIcAAAAIAABAAAAAQAAsJRxEF8PPPUEBwgAAAAAAM+a/G4AAAAA1MOn8gAAAAAEAAgAAAAAEAACAAAAAAAAAAEAAAgA//8AAAQAAAAAAAQAAAEAAAAAAAAAAAAAAAAAAAACAAEAAAACAAQAAQAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAwAAAZAABQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUAAQABAAAAAAAAAAAAAAAAAAAAAAAAAAAAR09PRwBAAAAAAAAB//8AAAABAAGAAAAAAAAAAAAAAAAAAAABAAAAAAAABAAAAAAAAAIAAQAAAAAAFAADAAAAAAAUAAYACgAAAAAAAAAAAAAAAAAMAAAAAQAAAAAEAAgAAAMAADEhESEEAPwACAAAAAADACoAAAADAAAABQAWAAAAAQAAAAAABQALABYAAwABBAkABQAWAAAAVgBlAHIAcwBpAG8AbgAgADEALgAwVmVyc2lvbiAxLjAAAAEAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAA=",
        )
    }
}
