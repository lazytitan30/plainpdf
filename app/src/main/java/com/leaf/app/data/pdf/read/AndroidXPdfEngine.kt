package com.leaf.app.data.pdf.read

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.core.util.forEach
import androidx.pdf.PdfDocument
import androidx.pdf.PdfLoader
import androidx.pdf.PdfPasswordException
import androidx.pdf.SandboxedPdfLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/** androidx.pdf implementation. Documents open in the library's sandboxed service process. */
class AndroidXPdfEngine(context: Context) : PdfEngine {

    private val loader: PdfLoader = SandboxedPdfLoader(context.applicationContext, Dispatchers.IO)

    override suspend fun open(uri: Uri, password: String?): Result<PdfHandle> = try {
        Result.success(AndroidXPdfHandle(loader.openDocument(uri, password)))
    } catch (e: PdfPasswordException) {
        Result.failure(PdfOpenException(PdfOpenException.Kind.PASSWORD_REQUIRED, e))
    } catch (e: SecurityException) {
        Result.failure(PdfOpenException(PdfOpenException.Kind.PERMISSION_DENIED, e))
    } catch (e: FileNotFoundException) {
        Result.failure(PdfOpenException(PdfOpenException.Kind.NOT_FOUND, e))
    } catch (e: IOException) {
        Result.failure(PdfOpenException(PdfOpenException.Kind.CORRUPT, e))
    } catch (e: IllegalArgumentException) {
        Result.failure(PdfOpenException(PdfOpenException.Kind.CORRUPT, e))
    } catch (e: RuntimeException) {
        Result.failure(PdfOpenException(PdfOpenException.Kind.UNKNOWN, e))
    }
}

private class AndroidXPdfHandle(private val document: PdfDocument) : PdfHandle {

    override val pageCount: Int get() = document.pageCount

    /** androidx.pdf 1.0.0-beta01 exposes no outline API; callers fall back to a page grid. */
    override val outline: List<OutlineNode> get() = emptyList()

    override suspend fun search(query: String): List<SearchMatch> {
        if (query.isBlank() || pageCount == 0) return emptyList()
        val results = document.searchDocument(query, 0 until pageCount)
        val matches = ArrayList<SearchMatch>()
        results.forEach { page, bounds ->
            bounds.forEach { match -> matches.add(SearchMatch(page, match.bounds, match.textStartIndex)) }
        }
        return matches
    }

    override suspend fun pageSize(pageIndex: Int): PageDimensions {
        val info = document.getPageInfo(pageIndex)
        return PageDimensions(info.width, info.height)
    }

    override suspend fun renderPage(pageIndex: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        val info = document.getPageInfo(pageIndex)
        val width = widthPx.coerceAtLeast(1)
        val height = (width.toFloat() * info.height / info.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        document.getPageBitmapSource(pageIndex).use { source ->
            source.getBitmap(Size(width, height))
        }
    }

    override fun close() = document.close()
}
