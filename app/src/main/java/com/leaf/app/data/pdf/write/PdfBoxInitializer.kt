package com.leaf.app.data.pdf.write

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PdfBox loads font resources on init, which is not free. Never called from
 * Application.onCreate; the first tool that needs it pays, off the main thread, once.
 */
object PdfBoxInitializer {

    private val initialised = AtomicBoolean(false)
    private val lock = Mutex()

    suspend fun ensure(context: Context) {
        if (initialised.get()) return
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (!initialised.get()) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    initialised.set(true)
                }
            }
        }
    }
}
