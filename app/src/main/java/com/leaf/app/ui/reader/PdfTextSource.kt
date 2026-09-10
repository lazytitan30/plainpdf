package com.leaf.app.ui.reader

import android.content.Context
import android.net.Uri
import com.leaf.app.data.pdf.write.PdfBoxInitializer
import com.leaf.app.util.ocr.TextQuality
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The words of the open document, read with PdfBox because the androidx viewer in beta01
 * exposes no text API. One parsed document stays open for the life of the source, so reading
 * aloud page by page does not reparse the file for every page. Every call runs on IO.
 */
class PdfTextSource(
    private val context: Context,
    private val uri: Uri,
    private val scratchDir: File,
    /** Recognises a page from its picture when the text layer is missing or unreadable. */
    private val fallback: OcrTextFallback? = null,
) : Closeable {

    private var document: PDDocument? = null

    val pageCount: Int get() = document?.numberOfPages ?: 0

    /**
     * Parses the document. Throws [InvalidPasswordException] when it needs a user password
     * (the viewer asked for one but never tells us) and [IOException] when unreadable.
     */
    @Throws(IOException::class)
    suspend fun open() = withContext(Dispatchers.IO) {
        PdfBoxInitializer.ensure(context)
        val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        // Large files spill to disk rather than the heap; the stream is fully consumed by load.
        val memory = MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES).setTempDir(scratchDir.apply { mkdirs() })
        document = input.use { PDDocument.load(it, "", memory) }
    }

    /**
     * Text of one 0-based page. A scan without a text layer, or a page whose fonts yield
     * rubbish instead of letters, is recognised from its picture when a fallback is set.
     */
    suspend fun pageText(pageIndex: Int): String = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext ""
        if (pageIndex !in 0 until doc.numberOfPages) return@withContext ""
        val own = try {
            PDFTextStripper().apply { startPage = pageIndex + 1; endPage = pageIndex + 1 }.getText(doc)
        } catch (_: IOException) {
            ""
        }
        if (fallback == null || (own.isNotBlank() && !TextQuality.looksGarbled(own))) return@withContext own
        val recognised = fallback.pageText(pageIndex)
        if (recognised.isNotBlank()) recognised else own
    }

    /** Whole-document text with a form feed between pages, the way PdfBox writes it. */
    suspend fun allText(): String {
        val count = pageCount
        if (count == 0) return ""
        return buildString {
            for (i in 0 until count) {
                if (i > 0) append('')
                append(pageText(i))
            }
        }
    }

    override fun close() {
        runCatching { document?.close() }
        runCatching { fallback?.close() }
        document = null
    }

    private companion object {
        const val MAX_MAIN_MEMORY_BYTES = 32L * 1024 * 1024
    }
}
