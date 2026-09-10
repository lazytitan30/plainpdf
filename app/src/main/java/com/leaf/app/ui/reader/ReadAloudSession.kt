package com.leaf.app.ui.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

/** What the reader shows while a session runs; null once it stops. */
data class ReadAloudState(val page: Int, val paused: Boolean = false)

/** Things the session cannot handle itself and the screen should tell the user about. */
enum class ReadAloudEvent { NO_VOICE, NO_TEXT, PASSWORD_REQUIRED, FAILED }

/**
 * Speaks the document from a page onward with the phone's own voice. Pages are spoken one at
 * a time: each page becomes a queue of sentence-sized utterances, and when the last of them
 * finishes the next page with any text is loaded. TextToSpeech has no pause, so pausing stops
 * the engine and remembers the utterance it was on; resuming speaks from there again.
 *
 * Every callback from the engine arrives on a binder thread and is hopped onto [scope], which
 * must be the main thread: TextToSpeech itself wants a looper thread.
 */
class ReadAloudSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val source: PdfTextSource,
    private val onPage: (Int) -> Unit,
    private val onEvent: (ReadAloudEvent) -> Unit,
    /** Called with every change and with null once the session is over, however it ended. */
    private val onState: (ReadAloudState?) -> Unit,
) {
    private var current: ReadAloudState? = null
        set(value) { field = value; onState(value) }

    private var tts: TextToSpeech? = null
    private var job: Job? = null
    private var page = 0
    private var chunks: List<String> = emptyList()
    private var nextChunk = 0
    private var paused = false
    private var stopped = false
    private var maxLength = SpeechChunks.MAX_LENGTH

    fun start(fromPage: Int) {
        job = scope.launch {
            val ready = CompletableDeferred<Boolean>()
            val engine = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
            tts = engine
            if (!ready.await()) { fail(ReadAloudEvent.FAILED); return@launch }
            if (stopped) return@launch
            chooseLanguage(engine)
            engine.setOnUtteranceProgressListener(listener)
            maxLength = minOf(TextToSpeech.getMaxSpeechInputLength(), SpeechChunks.MAX_LENGTH)
            try {
                source.open()
            } catch (_: InvalidPasswordException) {
                fail(ReadAloudEvent.PASSWORD_REQUIRED); return@launch
            } catch (_: IOException) {
                fail(ReadAloudEvent.FAILED); return@launch
            }
            if (stopped) return@launch
            if (!speakFrom(fromPage)) {
                // Nothing from here to the end. Only a document with no words at all deserves the hint.
                val anyText = (0 until minOf(fromPage, source.pageCount)).any { SpeechChunks.split(source.pageText(it), maxLength).isNotEmpty() }
                if (!anyText) onEvent(ReadAloudEvent.NO_TEXT)
                stop()
            }
        }
    }

    /** The phone's language, or English when no voice for it is installed. */
    private fun chooseLanguage(engine: TextToSpeech) {
        val result = engine.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.ENGLISH)
            onEvent(ReadAloudEvent.NO_VOICE)
        }
    }

    /** Loads the first page at or after [from] that has words and starts speaking it. False at the end. */
    private suspend fun speakFrom(from: Int): Boolean {
        var candidate = from
        while (candidate < source.pageCount) {
            val pieces = SpeechChunks.split(source.pageText(candidate), maxLength)
            if (stopped) return true
            if (pieces.isNotEmpty()) {
                page = candidate
                chunks = pieces
                nextChunk = 0
                current = ReadAloudState(page, paused)
                onPage(page)
                // A pause that landed while the page was loading holds the queue for resume.
                if (!paused) enqueue(0)
                return true
            }
            candidate++
        }
        return false
    }

    private fun enqueue(from: Int) {
        val engine = tts ?: return
        for (i in from..chunks.lastIndex) {
            val mode = if (i == from) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunks[i], mode, null, "$page:$i")
        }
    }

    fun pause() {
        if (paused || stopped) return
        paused = true
        tts?.stop()
        current = current?.copy(paused = true)
    }

    fun resume() {
        if (!paused || stopped) return
        paused = false
        current = current?.copy(paused = false)
        if (chunks.isNotEmpty()) enqueue(nextChunk)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        job?.cancel()
        tts?.let { runCatching { it.stop(); it.shutdown() } }
        tts = null
        source.close()
        current = null
    }

    private fun fail(event: ReadAloudEvent) {
        onEvent(event)
        stop()
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = advance(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = advance(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = advance(utteranceId)

        /** A finished (or unspeakable) utterance moves the resume point; the last one turns the page. */
        private fun advance(utteranceId: String?) {
            val parts = utteranceId?.split(':') ?: return
            val donePage = parts.getOrNull(0)?.toIntOrNull() ?: return
            val index = parts.getOrNull(1)?.toIntOrNull() ?: return
            scope.launch {
                if (stopped || paused || donePage != page) return@launch
                nextChunk = index + 1
                if (index == chunks.lastIndex && !speakFrom(page + 1)) stop()
            }
        }
    }
}
