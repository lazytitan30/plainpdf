package com.leaf.app.util.ocr

import android.content.Context
import android.net.Uri
import com.leaf.app.data.pdf.write.PdfBoxInitializer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * After recognition: did the text come out as words, or as rubbish? Rubbish usually means
 * the page is in a language whose pack is not on the phone, so the caller can offer one.
 * Reads at most the first few pages; a wrong answer only costs a hint, never a document.
 */
object OcrQualityCheck {

    private const val PAGES = 3

    suspend fun looksGarbled(context: Context, file: File): Boolean =
        check(context) { file.inputStream() }

    suspend fun looksGarbled(context: Context, uri: Uri): Boolean =
        check(context) { context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri") }

    private suspend fun check(context: Context, open: () -> InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            PdfBoxInitializer.ensure(context)
            open().use { stream ->
                PDDocument.load(stream).use { doc ->
                    if (doc.numberOfPages == 0) return@withContext false
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = minOf(PAGES, doc.numberOfPages)
                    }
                    val text = stripper.getText(doc)
                    // No text at all means recognition was off or found nothing; not a language problem.
                    text.count { !it.isWhitespace() } >= 20 && TextQuality.looksGarbled(text)
                }
            }
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}
