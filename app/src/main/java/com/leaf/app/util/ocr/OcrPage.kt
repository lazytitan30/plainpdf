package com.leaf.app.util.ocr

/**
 * One recognised word. Coordinates are pixels of the image that was recognised, with the
 * origin at the top-left, as OCR engines report them.
 */
data class OcrWord(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** 0 to 100. */
    val confidence: Float = 100f,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Words that sit on one baseline, in reading order. */
data class OcrLine(val words: List<OcrWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
    val top: Int get() = words.minOf { it.top }
    val bottom: Int get() = words.maxOf { it.bottom }
}

/** Everything recognised on one image. */
data class OcrPage(val widthPx: Int, val heightPx: Int, val lines: List<OcrLine>) {
    val text: String get() = lines.joinToString("\n") { it.text }
    val isEmpty: Boolean get() = lines.all { it.words.isEmpty() }
}
