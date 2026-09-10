package com.leaf.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.leaf.app.util.ocr.OcrEngine
import com.leaf.app.util.ocr.TessdataStore
import com.leaf.app.util.scan.DocumentImageProcessor
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Reads a page the way the eye does: renders it and runs text recognition. Used when a
 * document has no text layer, or one that comes out as rubbish (fonts without a Unicode
 * map are common in older Cyrillic PDFs). The recogniser is created on first use and kept
 * for the life of the fallback, so a whole document costs one initialisation.
 */
class OcrTextFallback(
    private val context: Context,
    private val uri: Uri,
    private val languages: List<String>,
) : Closeable {

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var engine: OcrEngine? = null
    private var unavailable = false

    /** Recognised text of one 0-based page, or empty when the page cannot be rendered or recognised. */
    fun pageText(pageIndex: Int): String {
        if (unavailable || languages.isEmpty()) return ""
        return try {
            val pages = renderer ?: open()
            if (pageIndex !in 0 until pages.pageCount) return ""
            val bitmap = pages.openPage(pageIndex).use { render(it) }
            try {
                (engine ?: openEngine()).recognise(bitmap).text
            } finally {
                bitmap.recycle()
            }
        } catch (_: IOException) {
            unavailable = true
            ""
        } catch (_: SecurityException) {
            // PdfRenderer cannot open protected files; the viewer already showed the text.
            unavailable = true
            ""
        } catch (_: OutOfMemoryError) {
            ""
        }
    }

    @Throws(IOException::class)
    private fun open(): PdfRenderer {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open $uri")
        pfd = fd
        return PdfRenderer(fd).also { renderer = it }
    }

    @Throws(IOException::class)
    private fun openEngine(): OcrEngine {
        TessdataStore(context).ensureBundled()
        return OcrEngine(File(context.filesDir, "ocr"), languages).also { engine = it }
    }

    private fun render(page: PdfRenderer.Page): Bitmap {
        // Points to pixels at a resolution good enough for recognition, within the heap budget.
        val cap = DocumentImageProcessor.adaptiveMaxEdge()
        val scale = minOf(DPI / 72f, cap.toFloat() / maxOf(page.width, page.height))
        val w = (page.width * scale).roundToInt().coerceAtLeast(1)
        val h = (page.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        return bitmap
    }

    override fun close() {
        runCatching { engine?.close() }
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        engine = null
        renderer = null
        pfd = null
    }

    private companion object {
        const val DPI = 250
    }
}
