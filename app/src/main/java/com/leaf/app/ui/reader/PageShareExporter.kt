package com.leaf.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.leaf.app.data.pdf.write.DocOperationException
import com.leaf.app.data.pdf.write.OperationError
import com.leaf.app.data.pdf.write.PdfBoxInitializer
import com.leaf.app.data.pdf.write.PdfBoxOperationRunner
import com.leaf.app.util.saf.OutputNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** A file in cacheDir/work/share ready for the share sheet. */
data class SharedPage(val file: File, val mime: String)

/**
 * Turns one page of the open document into something another app can take: a PNG from the
 * framework renderer, or a one-page PDF cut out with PdfBox. Works from a private copy of
 * the source so content providers that hand out pipes still work. Outputs land in
 * [workDir]/share, which the FileProvider exposes and app start sweeps.
 */
class PageShareExporter(private val context: Context, private val workDir: File) {

    sealed interface Result {
        data class Ok(val page: SharedPage) : Result
        data object PasswordRequired : Result
        data object Failed : Result
    }

    enum class Format(val extension: String, val mime: String) {
        PNG("png", "image/png"),
        PDF("pdf", "application/pdf"),
    }

    suspend fun export(uri: Uri, pageIndex: Int, displayName: String, format: Format): Result = withContext(Dispatchers.IO) {
        val shareDir = File(workDir, "share").apply { mkdirs() }
        val session = File(workDir, "share-src-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val src = File(session, "source.pdf")
            context.contentResolver.openInputStream(uri)?.use { input -> src.outputStream().use { input.copyTo(it) } }
                ?: return@withContext Result.Failed
            coroutineContext.ensureActive()
            val name = OutputNames.build(OutputNames.DEFAULT_PATTERN, displayName, OutputNames.pagesOp(listOf((pageIndex + 1)..(pageIndex + 1))), format.extension)
            val out = File(shareDir, name)
            when (format) {
                Format.PNG -> renderPng(src, pageIndex, out)
                Format.PDF -> splitPdf(src, pageIndex, out)
            }
        } catch (_: SecurityException) {
            Result.PasswordRequired
        } catch (e: DocOperationException) {
            if (e.error is OperationError.PasswordRequired) Result.PasswordRequired else Result.Failed
        } catch (_: IOException) {
            Result.Failed
        } catch (_: OutOfMemoryError) {
            Result.Failed
        } catch (_: IllegalArgumentException) {
            Result.Failed
        } catch (_: IllegalStateException) {
            Result.Failed
        } finally {
            session.deleteRecursively()
        }
    }

    /** The framework renderer refuses protected files with a SecurityException, which the caller maps to the password message. */
    private suspend fun renderPng(src: File, pageIndex: Int, out: File): Result {
        val pfd = ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            pfd.close()
            throw e
        }
        try {
            if (pageIndex !in 0 until renderer.pageCount) return Result.Failed
            val bitmap = renderer.openPage(pageIndex).use { page -> PdfBoxOperationRunner.renderPageBitmap(page, SHARE_DPI) }
            try {
                coroutineContext.ensureActive()
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        } finally {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
        return Result.Ok(SharedPage(out, Format.PNG.mime))
    }

    private suspend fun splitPdf(src: File, pageIndex: Int, out: File): Result {
        PdfBoxInitializer.ensure(context)
        val engine = PdfBoxOperationRunner.engineFor(context, workDir)
        val files = engine.split(src, null, listOf(pageIndex..pageIndex), out.parentFile ?: workDir) { out.name }
        val file = files.firstOrNull() ?: return Result.Failed
        engine.verify(file, 1)
        return Result.Ok(SharedPage(file, Format.PDF.mime))
    }

    companion object {
        /** Enough for a page to read well in a chat or an email without a huge file. */
        const val SHARE_DPI = 150
    }
}
