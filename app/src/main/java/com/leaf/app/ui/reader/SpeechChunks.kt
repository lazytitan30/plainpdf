package com.leaf.app.ui.reader

/**
 * Cuts a page of text into utterances for TextToSpeech. The engine refuses anything longer
 * than getMaxSpeechInputLength(), and pause and resume restart at an utterance boundary, so
 * short sentence-sized pieces are what make "Resume" land where the voice left off.
 */
object SpeechChunks {

    /** Below every engine's limit (4000 on stock Android) with room for the engine's own framing. */
    const val MAX_LENGTH = 3900

    /** Sentences are packed up to about this many characters so tiny fragments do not each become an utterance. */
    const val TARGET_LENGTH = 400

    private const val SENTENCE_ENDS = ".!?…"
    private const val CLOSERS = "\"'’”)]"

    /** Empty for a page with no words, so a scan without text recognition is simply skipped. */
    fun split(text: String, maxLength: Int = MAX_LENGTH): List<String> {
        require(maxLength > 0) { "maxLength must be positive" }
        val target = minOf(TARGET_LENGTH, maxLength)
        // Line ends from the PDF are layout, not meaning: fold every run of whitespace to a space.
        val flat = text.replace(WHITESPACE, " ").trim()
        if (flat.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (sentence in sentences(flat)) {
            for (piece in fit(sentence, maxLength)) {
                when {
                    current.isEmpty() -> current.append(piece)
                    current.length + 1 + piece.length <= target -> current.append(' ').append(piece)
                    else -> { out += current.toString(); current.setLength(0); current.append(piece) }
                }
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /** Splits after ". ! ? …" (plus any closing quote or bracket) when whitespace follows. */
    private fun sentences(text: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            if (text[i] in SENTENCE_ENDS) {
                var end = i + 1
                while (end < text.length && text[end] in SENTENCE_ENDS) end++
                while (end < text.length && text[end] in CLOSERS) end++
                if (end >= text.length || text[end].isWhitespace()) {
                    result += text.substring(start, end).trim()
                    start = end
                    i = end
                    continue
                }
            }
            i++
        }
        if (start < text.length) text.substring(start).trim().takeIf { it.isNotEmpty() }?.let { result += it }
        return result
    }

    /** A sentence over the limit is cut at word boundaries, and a single giant word is cut hard. */
    private fun fit(sentence: String, maxLength: Int): List<String> {
        if (sentence.length <= maxLength) return listOf(sentence)
        val pieces = ArrayList<String>()
        var rest = sentence
        while (rest.length > maxLength) {
            val cut = rest.lastIndexOf(' ', maxLength).takeIf { it > 0 } ?: maxLength
            pieces += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }

    private val WHITESPACE = Regex("\\s+")
}
