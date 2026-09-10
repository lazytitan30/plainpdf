package com.leaf.app.util.ocr

import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.IOException

/**
 * One Tesseract instance, initialised once for [languages] ("eng+srp_latn" style codes),
 * reused page after page and then [close]d. Recognition runs on the calling thread and
 * takes seconds; [cancel] from another thread makes it return early.
 */
class OcrEngine(dataParent: File, languages: List<String>) {

    private val api = TessBaseAPI()

    @Volatile
    private var closed = false

    init {
        require(languages.isNotEmpty()) { "At least one language" }
        val joined = OcrLanguages.join(languages)
        val ok = try {
            api.init(dataParent.absolutePath, joined, TessBaseAPI.OEM_LSTM_ONLY)
        } catch (e: RuntimeException) {
            runCatching { api.recycle() }
            throw IOException("Language data for $joined could not be loaded: ${e.message}", e)
        }
        if (!ok) {
            runCatching { api.recycle() }
            throw IOException("Language data for $joined is missing or damaged")
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
    }

    /** Words grouped into lines, with boxes in pixels of [bitmap]. Empty when nothing was read. */
    fun recognise(bitmap: Bitmap): OcrPage {
        check(!closed) { "Engine is closed" }
        // Tesseract4Android only accepts ARGB_8888.
        val source = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        try {
            api.setImage(source)
            api.utF8Text // runs recognition
            val lines = ArrayList<OcrLine>()
            val iterator = api.resultIterator ?: return OcrPage(bitmap.width, bitmap.height, emptyList())
            try {
                iterator.begin()
                var words = ArrayList<OcrWord>()
                do {
                    if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE) && words.isNotEmpty()) {
                        lines += OcrLine(words)
                        words = ArrayList()
                    }
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        if (rect != null && rect.width() > 0 && rect.height() > 0) {
                            words += OcrWord(
                                text = text,
                                left = rect.left.coerceIn(0, bitmap.width),
                                top = rect.top.coerceIn(0, bitmap.height),
                                right = rect.right.coerceIn(0, bitmap.width),
                                bottom = rect.bottom.coerceIn(0, bitmap.height),
                                confidence = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD),
                            )
                        }
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                if (words.isNotEmpty()) lines += OcrLine(words)
            } finally {
                runCatching { iterator.delete() }
            }
            return OcrPage(bitmap.width, bitmap.height, lines)
        } finally {
            if (source !== bitmap) source.recycle()
        }
    }

    /** Interrupts a running [recognise]; it returns with whatever was read so far. */
    fun cancel() {
        if (!closed) runCatching { api.stop() }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { api.recycle() }
    }
}
