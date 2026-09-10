package com.leaf.app.data.pdf.write

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import android.graphics.Bitmap
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pure PdfBox half of the write path. Works on files only, which keeps it runnable
 * from JVM unit tests with golden fixtures. The Android runner owns URIs, temp files and
 * the commit step.
 *
 * Every document is opened with a mixed memory setting so large files spill to disk
 * instead of exhausting the heap. Every PDDocument is closed in a finally.
 */
class PdfBoxEngine(
    private val maxMainMemoryBytes: Long,
    private val scratchDir: File,
) {

    /** One input for merge: a file, its password if any, and 0-based page ranges or null for all. */
    data class MergeInput(val file: File, val password: String?, val pages: List<IntRange>?)

    private fun memory(): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(maxMainMemoryBytes).setTempDir(scratchDir.apply { mkdirs() })

    /** Opens a document, translating PdfBox failures into [DocOperationException]. */
    fun open(file: File, password: String?): PDDocument = try {
        PDDocument.load(file, password ?: "", memory())
    } catch (e: InvalidPasswordException) {
        throw DocOperationException(OperationError.PasswordRequired, e)
    } catch (e: IOException) {
        throw DocOperationException(OperationError.Corrupt, e)
    } catch (e: OutOfMemoryError) {
        throw DocOperationException(OperationError.OutOfMemory, e)
    }

    fun pageCount(file: File, password: String?): Int = open(file, password).use { it.numberOfPages }

    /** Owner-password restrictions are honoured, never bypassed. */
    private fun requireAssembly(doc: PDDocument) {
        val perms: AccessPermission = doc.currentAccessPermission
        if (!perms.isOwnerPermission && !perms.canAssembleDocument()) {
            throw DocOperationException(OperationError.AssemblyRestricted)
        }
    }

    // ---- Operations ----

    fun merge(inputs: List<MergeInput>, out: File): Int {
        require(inputs.isNotEmpty()) { "merge needs at least one input" }
        val dest = PDDocument(memory())
        val opened = ArrayList<PDDocument>()
        try {
            val merger = PDFMergerUtility()
            for (input in inputs) {
                val src = open(input.file, input.password).also { opened += it }
                requireAssembly(src)
                if (input.pages == null) {
                    merger.appendDocument(dest, src)
                } else {
                    for (range in input.pages) {
                        val sub = extract(src, range).also { opened += it }
                        merger.appendDocument(dest, sub)
                    }
                }
            }
            if (dest.numberOfPages == 0) throw DocOperationException(OperationError.Unknown("Output would be empty"))
            save(dest, out)
            return dest.numberOfPages
        } finally {
            opened.forEach { runCatching { it.close() } }
            runCatching { dest.close() }
        }
    }

    /** Rotate, delete, reorder, extract and duplicate: one [PagePlan], one code path. */
    fun organise(source: File, password: String?, plan: PagePlan, out: File): Int {
        if (plan.isEmpty) throw DocOperationException(OperationError.Unknown("Output would be empty"))
        val src = open(source, password)
        val dest = PDDocument(memory())
        try {
            requireAssembly(src)
            val count = src.numberOfPages
            for (op in plan.pages) {
                if (op.sourceIndex !in 0 until count) {
                    throw DocOperationException(OperationError.Unknown("Page ${op.sourceIndex + 1} does not exist"))
                }
                val page: PDPage = src.getPage(op.sourceIndex)
                val imported = dest.importPage(page)
                if (op.rotationDelta != 0) {
                    imported.rotation = ((page.rotation + op.rotationDelta) % 360 + 360) % 360
                }
            }
            save(dest, out)
            return dest.numberOfPages
        } finally {
            runCatching { dest.close() }
            runCatching { src.close() }
        }
    }

    /**
     * One output per 0-based inclusive range. [name] builds a file name from the 1-based
     * range so "report_pages_1-4.pdf" reads naturally.
     */
    fun split(source: File, password: String?, ranges: List<IntRange>, outDir: File, name: (IntRange) -> String): List<File> {
        if (ranges.isEmpty()) throw DocOperationException(OperationError.Unknown("No page ranges"))
        val src = open(source, password)
        val outputs = ArrayList<File>()
        try {
            requireAssembly(src)
            outDir.mkdirs()
            for (range in ranges) {
                if (range.first < 0 || range.last >= src.numberOfPages || range.isEmpty()) {
                    throw DocOperationException(OperationError.Unknown("Range ${range.first + 1}-${range.last + 1} is outside the document"))
                }
                val part = extract(src, range)
                try {
                    val target = File(outDir, name((range.first + 1)..(range.last + 1)))
                    save(part, target)
                    outputs += target
                } finally {
                    runCatching { part.close() }
                }
            }
            return outputs
        } finally {
            runCatching { src.close() }
        }
    }

    /** [newPassword] null removes protection. Needs owner rights to strip it. */
    fun setPassword(source: File, currentPassword: String?, newPassword: String?, out: File): Int {
        val doc = open(source, currentPassword)
        try {
            if (newPassword == null) {
                if (doc.isEncrypted && !doc.currentAccessPermission.isOwnerPermission) {
                    throw DocOperationException(OperationError.AssemblyRestricted)
                }
                doc.isAllSecurityToBeRemoved = true
            } else {
                val policy = StandardProtectionPolicy(newPassword, newPassword, AccessPermission()).apply {
                    encryptionKeyLength = 256
                    setPreferAES(true)
                }
                doc.protect(policy)
            }
            save(doc, out)
            return doc.numberOfPages
        } finally {
            runCatching { doc.close() }
        }
    }

    /**
     * PdfBox refuses to save a document it decrypted on load unless a protection policy is
     * set again, so an in-place edit re-applies the same password and the output stays locked.
     */
    internal fun keepProtection(doc: PDDocument, password: String?) {
        if (!doc.isEncrypted) return
        val pw = password ?: ""
        val policy = StandardProtectionPolicy(pw, pw, doc.currentAccessPermission).apply {
            encryptionKeyLength = 256
            setPreferAES(true)
        }
        doc.protect(policy)
    }

    fun watermark(source: File, password: String?, text: String, opts: WatermarkOptions, out: File): Int {
        val doc = open(source, password)
        try {
            requireAssembly(doc)
            keepProtection(doc, password)
            val font = PDType1Font.HELVETICA_BOLD
            val state = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = opts.opacity.coerceIn(0.05f, 1f)
                strokingAlphaConstant = opts.opacity.coerceIn(0.05f, 1f)
            }
            for (page in doc.pages) {
                val box = page.mediaBox
                val textWidth = font.getStringWidth(text) / 1000f * opts.fontSizePt
                val rad = Math.toRadians(opts.rotationDegrees.toDouble())
                val cx = box.width / 2f
                val cy = box.height / 2f
                val x = cx - (textWidth / 2f) * cos(rad).toFloat()
                val y = cy - (textWidth / 2f) * sin(rad).toFloat()
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(state)
                    cs.setNonStrokingColor(128, 128, 128)
                    cs.beginText()
                    cs.setFont(font, opts.fontSizePt)
                    cs.setTextMatrix(Matrix.getRotateInstance(rad, x, y))
                    cs.showText(text)
                    cs.endText()
                }
            }
            save(doc, out)
            return doc.numberOfPages
        } finally {
            runCatching { doc.close() }
        }
    }

    /**
     * Draw [image] on [pageIndex]. [left], [top] and [width] are fractions of the page as
     * displayed (rotation applied), top-left origin, so they match what the user placed on
     * screen. The content stream is wrapped so the rotation transform never leaks.
     */
    fun stamp(source: File, password: String?, pageIndex: Int, image: File, left: Float, top: Float, width: Float, out: File): Int {
        val doc = open(source, password)
        try {
            val perms = doc.currentAccessPermission
            if (!perms.isOwnerPermission && !perms.canModify()) throw DocOperationException(OperationError.AssemblyRestricted)
            if (pageIndex !in 0 until doc.numberOfPages) throw DocOperationException(OperationError.Unknown("Page ${pageIndex + 1} does not exist"))
            keepProtection(doc, password)
            val page = doc.getPage(pageIndex)
            val img = try {
                PDImageXObject.createFromFile(image.path, doc)
            } catch (e: IOException) {
                throw DocOperationException(OperationError.Unknown("Signature image could not be read"), e)
            }
            val box = page.mediaBox
            val rot = ((page.rotation % 360) + 360) % 360
            val w = box.width
            val h = box.height
            val displayW = if (rot == 90 || rot == 270) h else w
            val displayH = if (rot == 90 || rot == 270) w else h
            val drawW = width * displayW
            val drawH = drawW * img.height / img.width.coerceAtLeast(1)
            val x = left * displayW
            val yFromTop = top * displayH
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                when (rot) {
                    90 -> cs.transform(Matrix.getRotateInstance(Math.toRadians(90.0), w, 0f))
                    180 -> cs.transform(Matrix.getRotateInstance(Math.toRadians(180.0), w, h))
                    270 -> cs.transform(Matrix.getRotateInstance(Math.toRadians(270.0), 0f, h))
                }
                cs.drawImage(img, box.lowerLeftX + x, box.lowerLeftY + displayH - yFromTop - drawH, drawW, drawH)
            }
            save(doc, out)
            return doc.numberOfPages
        } finally {
            runCatching { doc.close() }
        }
    }

    /**
     * Prints a number on every page in Helvetica at [PAGE_NUMBER_FONT_SIZE] pt, [PAGE_NUMBER_MARGIN]
     * pt in from the edge of the page as displayed, so a rotated page gets its number where the
     * reader expects it. [format] takes "{n}" and "{total}", the last number.
     */
    fun pageNumbers(source: File, password: String?, position: PageNumberPosition, startAt: Int, format: String, out: File): Int {
        val doc = open(source, password)
        try {
            val perms = doc.currentAccessPermission
            if (!perms.isOwnerPermission && !perms.canModify()) throw DocOperationException(OperationError.AssemblyRestricted)
            keepProtection(doc, password)
            val font = PDType1Font.HELVETICA
            val total = doc.numberOfPages
            val last = (startAt + total - 1).toString()
            for ((i, page) in doc.pages.withIndex()) {
                val label = format.replace("{n}", (startAt + i).toString()).replace("{total}", last)
                if (label.isBlank()) continue
                val box = page.mediaBox
                val rot = ((page.rotation % 360) + 360) % 360
                val w = box.width
                val h = box.height
                val displayW = if (rot == 90 || rot == 270) h else w
                val displayH = if (rot == 90 || rot == 270) w else h
                val textWidth = font.getStringWidth(label) / 1000f * PAGE_NUMBER_FONT_SIZE
                val x = when (position) {
                    PageNumberPosition.BOTTOM_CENTRE -> (displayW - textWidth) / 2f
                    PageNumberPosition.BOTTOM_RIGHT, PageNumberPosition.TOP_RIGHT -> displayW - PAGE_NUMBER_MARGIN - textWidth
                }
                // Baseline: the margin up from the bottom, or the margin plus the cap height down from the top.
                val y = when (position) {
                    PageNumberPosition.TOP_RIGHT -> displayH - PAGE_NUMBER_MARGIN - PAGE_NUMBER_FONT_SIZE * HELVETICA_CAP_HEIGHT
                    else -> PAGE_NUMBER_MARGIN
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    when (rot) {
                        90 -> cs.transform(Matrix.getRotateInstance(Math.toRadians(90.0), w, 0f))
                        180 -> cs.transform(Matrix.getRotateInstance(Math.toRadians(180.0), w, h))
                        270 -> cs.transform(Matrix.getRotateInstance(Math.toRadians(270.0), 0f, h))
                    }
                    cs.setNonStrokingColor(0, 0, 0)
                    cs.beginText()
                    cs.setFont(font, PAGE_NUMBER_FONT_SIZE)
                    cs.newLineAtOffset(box.lowerLeftX + x, box.lowerLeftY + y)
                    cs.showText(label)
                    cs.endText()
                }
            }
            save(doc, out)
            return doc.numberOfPages
        } finally {
            runCatching { doc.close() }
        }
    }

    /**
     * Replaces every page in [pageImages] with a fresh page showing only that JPEG, a
     * picture of the page as displayed with the redaction boxes already painted over. The
     * old page object goes away entirely (content, resources, annotations, thumbnail), so
     * nothing under a box can be recovered. Pages not in the map are untouched.
     *
     * The rendering side is the runner's, because it needs the framework renderer; this
     * half is plain PdfBox so the swap can be tested on the JVM with any JPEG file.
     */
    fun redact(source: File, password: String?, pageImages: Map<Int, File>, out: File): Int {
        if (pageImages.isEmpty()) throw DocOperationException(OperationError.Unknown("Nothing to redact"))
        val doc = open(source, password)
        try {
            val perms = doc.currentAccessPermission
            if (!perms.isOwnerPermission && !perms.canModify()) throw DocOperationException(OperationError.AssemblyRestricted)
            keepProtection(doc, password)
            for ((index, jpeg) in pageImages) {
                if (index !in 0 until doc.numberOfPages) throw DocOperationException(OperationError.Unknown("Page ${index + 1} does not exist"))
                val old = doc.getPage(index)
                val display = displayBox(old)
                val info = try {
                    readJpegInfo(jpeg)
                } catch (e: IOException) {
                    throw DocOperationException(OperationError.Unknown("Redacted page ${index + 1} could not be read"), e)
                }
                if (info.components != 1 && info.components != 3) {
                    throw DocOperationException(OperationError.Unknown("Redacted page ${index + 1} has an unsupported colour layout"))
                }
                val colorSpace = if (info.components == 1) PDDeviceGray.INSTANCE else PDDeviceRGB.INSTANCE
                val image = jpeg.inputStream().use { PDImageXObject(doc, it, COSName.DCT_DECODE, info.width, info.height, 8, colorSpace) }
                // The picture is in display orientation, so the new page is upright with the displayed size.
                val fresh = PDPage(display)
                fresh.rotation = 0
                fresh.cropBox = display
                PDPageContentStream(doc, fresh).use { cs ->
                    cs.drawImage(image, display.lowerLeftX, display.lowerLeftY, display.width, display.height)
                }
                replacePage(doc, old, fresh)
            }
            save(doc, out)
            return doc.numberOfPages
        } finally {
            runCatching { doc.close() }
        }
    }

    /**
     * Lays typed text out on A4 pages and writes a fresh document. [title], when not blank,
     * comes first at [NOTE_TITLE_SCALE] times [fontSizePt]; [body] follows with a paragraph
     * per line break, each paragraph word-wrapped to the text width using the font's own
     * glyph widths. The font is loaded from [fontStream] and embedded as a subset so every
     * script it covers, Cyrillic included, shows and extracts correctly. Returns the page count.
     */
    fun textToPdf(title: String, body: String, fontSizePt: Float, fontStream: () -> InputStream, out: File): Int {
        val doc = PDDocument(memory())
        try {
            val font = try {
                fontStream().use { PDType0Font.load(doc, it, true) }
            } catch (e: IOException) {
                throw DocOperationException(OperationError.Unknown("The note font could not be loaded"), e)
            }
            val size = fontSizePt.coerceIn(6f, 72f)
            val titleSize = size * NOTE_TITLE_SCALE
            val pageRect = PDRectangle.A4
            val textWidth = pageRect.width - 2 * NOTE_MARGIN
            val glyphs = GlyphFilter(font)

            // Every line to print, with its size; an empty string is a blank line of that size.
            val lines = ArrayList<Pair<String, Float>>()
            val heading = glyphs.clean(title).trim()
            if (heading.isNotEmpty()) {
                wrap(font, heading, titleSize, textWidth).forEach { lines += it to titleSize }
                lines += "" to size
            }
            val paragraphs = glyphs.clean(body).replace("\r\n", "\n").replace('\r', '\n').split('\n')
            for (paragraph in paragraphs) {
                val wrapped = wrap(font, paragraph.trimEnd(), size, textWidth)
                if (wrapped.isEmpty()) lines += "" to size else wrapped.forEach { lines += it to size }
            }
            // Blank lines at the very end would only add empty pages.
            while (lines.isNotEmpty() && lines.last().first.isEmpty()) lines.removeAt(lines.size - 1)
            if (lines.isEmpty()) throw DocOperationException(OperationError.Unknown("The note is empty"))

            val top = pageRect.height - NOTE_MARGIN
            var cs: PDPageContentStream? = null
            var y = top
            try {
                for ((text, lineSize) in lines) {
                    val lineHeight = lineSize * NOTE_LINE_HEIGHT
                    if (cs == null || y - lineHeight < NOTE_MARGIN) {
                        cs?.close()
                        val page = PDPage(pageRect)
                        doc.addPage(page)
                        cs = PDPageContentStream(doc, page)
                        y = top
                    }
                    y -= lineHeight
                    if (text.isEmpty()) continue
                    cs.beginText()
                    cs.setFont(font, lineSize)
                    cs.newLineAtOffset(NOTE_MARGIN, y)
                    cs.showText(text)
                    cs.endText()
                }
            } finally {
                cs?.close()
            }
            save(doc, out)
            return doc.numberOfPages
        } finally {
            runCatching { doc.close() }
        }
    }

    /** Breaks [text] into lines no wider than [maxWidth] at spaces, and inside a word only when the word alone is too wide. */
    private fun wrap(font: PDFont, text: String, size: Float, maxWidth: Float): List<String> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        fun width(s: String): Float = font.getStringWidth(s) / 1000f * size
        val lines = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) lines += current.toString()
            current.setLength(0)
        }
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (width(candidate) <= maxWidth) {
                current.setLength(0)
                current.append(candidate)
                continue
            }
            flush()
            if (width(word) <= maxWidth) {
                current.append(word)
                continue
            }
            // A single word wider than the page: split it wherever the width runs out.
            var piece = StringBuilder()
            var i = 0
            while (i < word.length) {
                val cp = word.codePointAt(i)
                val next = piece.toString() + String(Character.toChars(cp))
                if (piece.isNotEmpty() && width(next) > maxWidth) {
                    lines += piece.toString()
                    piece = StringBuilder()
                }
                piece.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
            current.append(piece)
        }
        flush()
        return lines
    }

    /**
     * Swaps characters the font cannot show for a placeholder, so a stray emoji does not
     * fail the whole note. Tabs become spaces; other control characters vanish.
     */
    private class GlyphFilter(private val font: PDFont) {
        private val known = HashMap<Int, Boolean>()

        private fun has(cp: Int): Boolean = known.getOrPut(cp) {
            runCatching { font.encode(String(Character.toChars(cp))) }.isSuccess
        }

        fun clean(text: String): String {
            val sb = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                i += Character.charCount(cp)
                when {
                    cp == '\n'.code || cp == '\r'.code -> sb.appendCodePoint(cp)
                    cp == '\t'.code -> sb.append("    ")
                    Character.isISOControl(cp) -> Unit
                    has(cp) -> sb.appendCodePoint(cp)
                    else -> sb.append(NOTE_MISSING_GLYPH)
                }
            }
            return sb.toString()
        }
    }

    /** The page rectangle as a viewer shows it: the crop box, turned by the page rotation, at the origin. */
    private fun displayBox(page: PDPage): PDRectangle {
        val box = page.cropBox ?: page.mediaBox
        val rot = ((page.rotation % 360) + 360) % 360
        return if (rot == 90 || rot == 270) PDRectangle(box.height, box.width) else PDRectangle(box.width, box.height)
    }

    /** Swaps [old] for [fresh] in place in the page tree, so page order, outlines and links to other pages survive. */
    private fun replacePage(doc: PDDocument, old: PDPage, fresh: PDPage) {
        val parent = old.cosObject.getCOSDictionary(COSName.PARENT)
        val kids = parent?.getCOSArray(COSName.KIDS)
        val at = kids?.indexOfObject(old.cosObject) ?: -1
        if (parent == null || kids == null || at < 0) {
            // A malformed tree: remove and re-insert at the same index, which keeps the count right.
            val index = doc.pages.indexOf(old)
            doc.removePage(old)
            if (index >= doc.numberOfPages) doc.addPage(fresh) else doc.pages.insertBefore(fresh, doc.getPage(index))
            return
        }
        fresh.cosObject.setItem(COSName.PARENT, parent)
        kids.set(at, fresh.cosObject)
    }

    private data class JpegInfo(val width: Int, val height: Int, val components: Int)

    /** Reads the size and channel count from a JPEG's start-of-frame marker without decoding it. */
    private fun readJpegInfo(file: File): JpegInfo {
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readUnsignedShort() != 0xFFD8) throw IOException("Not a JPEG file")
            while (true) {
                var marker = input.readUnsignedByte()
                if (marker != 0xFF) throw IOException("Malformed JPEG")
                while (marker == 0xFF) marker = input.readUnsignedByte()
                when (marker) {
                    0x01, in 0xD0..0xD8 -> continue // stand-alone markers carry no length
                    0xD9, 0xDA -> throw IOException("JPEG has no frame header")
                    0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF -> {
                        input.readUnsignedShort() // segment length
                        input.readUnsignedByte() // sample precision
                        val height = input.readUnsignedShort()
                        val width = input.readUnsignedShort()
                        val components = input.readUnsignedByte()
                        if (width <= 0 || height <= 0) throw IOException("JPEG has no size")
                        return JpegInfo(width, height, components)
                    }
                    else -> {
                        var remaining = input.readUnsignedShort() - 2
                        while (remaining > 0) {
                            val skipped = input.skipBytes(remaining)
                            if (skipped <= 0) throw IOException("Truncated JPEG")
                            remaining -= skipped
                        }
                    }
                }
            }
        }
    }

    /**
     * Shrinks the pictures in a document. Every image XObject on every page (and inside
     * form XObjects) is decoded, scaled down to the level's longest edge and re-encoded as
     * JPEG. Bilevel scans, stencils and images with transparency masks are left alone
     * because JPEG would either bloat them or lose the mask. Text, fonts and vector art
     * are not touched, so the result reads exactly like the original.
     */
    fun compress(source: File, password: String?, level: CompressLevel, out: File): Int {
        val doc = open(source, password)
        try {
            val perms = doc.currentAccessPermission
            if (!perms.isOwnerPermission && !perms.canModify()) throw DocOperationException(OperationError.AssemblyRestricted)
            // Shared images are replaced once and reused; a null marks an image to leave as is.
            val done = HashMap<COSBase, PDImageXObject?>()
            for (page in doc.pages) {
                page.resources?.let { compressResources(doc, it, level, done, depth = 0) }
            }
            if (doc.isEncrypted) keepProtection(doc, password)
            save(doc, out)
            return doc.numberOfPages
        } finally {
            runCatching { doc.close() }
        }
    }

    private fun compressResources(doc: PDDocument, res: PDResources, level: CompressLevel, done: MutableMap<COSBase, PDImageXObject?>, depth: Int) {
        if (depth > 8) return
        for (name in res.xObjectNames.toList()) {
            val xo = try { res.getXObject(name) } catch (_: IOException) { continue }
            when (xo) {
                is PDFormXObject -> xo.resources?.let { compressResources(doc, it, level, done, depth + 1) }
                is PDImageXObject -> {
                    val key = xo.cosObject
                    val replacement = if (done.containsKey(key)) done[key] else recompress(doc, xo, level).also { done[key] = it }
                    if (replacement != null) res.put(name, replacement)
                }
            }
        }
    }

    /** Returns the smaller image, or null when this one is best left untouched. */
    private fun recompress(doc: PDDocument, image: PDImageXObject, level: CompressLevel): PDImageXObject? {
        if (image.isStencil || image.bitsPerComponent == 1) return null
        if (runCatching { image.softMask }.getOrNull() != null || runCatching { image.mask }.getOrNull() != null) return null
        val longEdge = maxOf(image.width, image.height)
        if (longEdge <= 0) return null
        val isJpeg = hasFilter(image.cosObject.filters, COSName.DCT_DECODE)
        val needsResize = longEdge > level.maxEdge
        if (!needsResize && isJpeg && !level.recompressJpeg) return null
        var subsampling = 1
        while (longEdge / (subsampling * 2) >= level.maxEdge) subsampling *= 2
        val decoded: Bitmap = try {
            image.getImage(null, subsampling) ?: return null
        } catch (_: IOException) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        } catch (_: RuntimeException) {
            return null
        }
        try {
            val edge = maxOf(decoded.width, decoded.height)
            val scaled = if (edge > level.maxEdge) {
                val f = level.maxEdge.toFloat() / edge
                Bitmap.createScaledBitmap(decoded, (decoded.width * f).toInt().coerceAtLeast(1), (decoded.height * f).toInt().coerceAtLeast(1), true)
            } else decoded
            try {
                return JPEGFactory.createFromImage(doc, scaled, level.jpegQuality)
            } catch (_: IOException) {
                return null
            } finally {
                if (scaled !== decoded) scaled.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun hasFilter(filters: COSBase?, wanted: COSName): Boolean = when (filters) {
        is COSName -> filters == wanted
        is COSArray -> filters.toList().any { it == wanted }
        else -> false
    }

    /** Reopen the written file and confirm it has the page count the plan promised. */
    fun verify(out: File, expectedPages: Int, password: String? = null) {
        val actual = try {
            pageCount(out, password)
        } catch (e: DocOperationException) {
            throw DocOperationException(OperationError.Unknown("Output could not be read back"), e)
        }
        if (actual != expectedPages) {
            throw DocOperationException(OperationError.Unknown("Output has $actual pages, expected $expectedPages"))
        }
    }

    // ---- Helpers ----

    private fun extract(src: PDDocument, range: IntRange): PDDocument {
        val splitter = Splitter().apply {
            setStartPage(range.first + 1)
            setEndPage(range.last + 1)
            setSplitAtPage(range.last - range.first + 1)
        }
        val parts = splitter.split(src)
        // Splitter yields one document for a range whose size equals splitAtPage.
        val head = parts.firstOrNull() ?: throw DocOperationException(OperationError.Unknown("Nothing to extract"))
        parts.drop(1).forEach { runCatching { it.close() } }
        return head
    }

    private fun save(doc: PDDocument, out: File) {
        try {
            out.parentFile?.mkdirs()
            // Every file the app writes names its producer, the way desktop tools do.
            doc.documentInformation.producer = "Plain PDF (plainpdf.app)"
            doc.documentInformation.creator = "Plain PDF by TekiTana"
            doc.save(out)
        } catch (e: IOException) {
            throw DocOperationException(OperationError.Unknown(e.message), e)
        } catch (e: OutOfMemoryError) {
            throw DocOperationException(OperationError.OutOfMemory, e)
        }
    }

    companion object {
        const val PAGE_NUMBER_FONT_SIZE = 10f
        const val PAGE_NUMBER_MARGIN = 24f
        /** Helvetica's cap height as a fraction of the font size, from its AFM. */
        private const val HELVETICA_CAP_HEIGHT = 0.718f
        /** Write a note: A4 with this margin on every side, lines this many times the font size apart. */
        const val NOTE_MARGIN = 56f
        const val NOTE_LINE_HEIGHT = 1.4f
        const val NOTE_TITLE_SCALE = 1.5f
        private const val NOTE_MISSING_GLYPH = "?"
    }
}
