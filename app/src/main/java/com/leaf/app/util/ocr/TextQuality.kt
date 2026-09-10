package com.leaf.app.util.ocr

/**
 * Tells usable text from the rubbish some PDFs hand back. A common case: Cyrillic documents
 * whose fonts carry no Unicode map, so extraction yields private-use codes, replacement
 * characters or symbols instead of letters, while digits still come out right. Such a page
 * is better recognised from its picture than copied as it is.
 */
object TextQuality {

    /** True when [text] has enough characters to judge and most of them are not readable letters or digits. */
    fun looksGarbled(text: String): Boolean {
        var letters = 0
        var digits = 0
        var bad = 0
        var visible = 0
        for (ch in text) {
            if (ch.isWhitespace()) continue
            visible++
            when {
                ch.isLetter() -> letters++
                ch.isDigit() -> digits++
                isSuspicious(ch) -> bad++
            }
        }
        if (visible < MIN_VISIBLE) return false
        if (bad.toFloat() / visible > MAX_BAD_FRACTION) return true
        // Symbols and punctuation only, with hardly any letters: the "text" is not words.
        return letters.toFloat() / visible < MIN_LETTER_FRACTION
    }

    private fun isSuspicious(ch: Char): Boolean {
        val c = ch.code
        return c in 0xE000..0xF8FF || // private use area
            c == 0xFFFD || // replacement character
            c < 0x20 || c in 0x7F..0x9F // control characters
    }

    private const val MIN_VISIBLE = 20
    private const val MAX_BAD_FRACTION = 0.10f
    private const val MIN_LETTER_FRACTION = 0.30f
}
