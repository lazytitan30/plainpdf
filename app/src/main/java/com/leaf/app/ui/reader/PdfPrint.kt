package com.leaf.app.ui.reader

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException

/**
 * Hands the document to the system print dialog. Nothing is rendered here: print services,
 * "Save as PDF" included, take a PDF as it is, so the bytes are streamed straight through.
 * Returns false when the device has no print support at all.
 */
fun Context.printPdf(uri: Uri, name: String, pageCount: Int, scope: CoroutineScope): Boolean {
    val manager = getSystemService(PrintManager::class.java) ?: return false
    return runCatching {
        manager.print(name, PdfPrintAdapter(contentResolver, uri, name, pageCount, scope), PrintAttributes.Builder().build())
    }.isSuccess
}

private class PdfPrintAdapter(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val name: String,
    private val pageCount: Int,
    private val scope: CoroutineScope,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(if (pageCount > 0) pageCount else PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        // The content never depends on paper size or orientation; only the first layout is a change.
        callback.onLayoutFinished(info, oldAttributes == null || oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        // Called on the main thread; the copy goes to IO and the answer comes back to main.
        scope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                input.use { source ->
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            if (cancellationSignal.isCanceled) return@use false
                            val read = source.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                        out.flush()
                        true
                    }
                }
            }
            withContext(Dispatchers.Main) {
                outcome.fold(
                    onSuccess = { finished -> if (finished) callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) else callback.onWriteCancelled() },
                    onFailure = { callback.onWriteFailed(it.message) },
                )
            }
        }
    }
}
