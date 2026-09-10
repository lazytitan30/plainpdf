package com.leaf.app.util.saf

/** Pure helpers for suggested output file names. No Android types, fully unit tested. */
object OutputNames {

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private const val MAX_LENGTH = 120
    private const val FALLBACK = "document"

    /** "report.PDF" -> "report". Leaves names without a pdf extension alone. */
    fun stripExtension(displayName: String): String {
        val trimmed = displayName.trim()
        return if (trimmed.endsWith(".pdf", ignoreCase = true)) trimmed.dropLast(4) else trimmed
    }

    /** Removes characters that SAF providers reject and guarantees a non-empty result. */
    fun sanitize(name: String): String {
        val cleaned = ILLEGAL.replace(name, "_").trim().trim('.')
        val bounded = if (cleaned.length > MAX_LENGTH) cleaned.take(MAX_LENGTH).trimEnd() else cleaned
        return bounded.ifEmpty { FALLBACK }
    }

    /**
     * Applies the user's pattern. `{name}` is the source without extension, `{op}` the
     * operation label such as `merged` or `pages_1-4`. A pattern that drops `{name}` still
     * produces a distinct file because `{op}` is always appended when missing.
     */
    fun build(pattern: String, sourceName: String, op: String, extension: String = "pdf"): String {
        val name = sanitize(stripExtension(sourceName))
        val effectivePattern = pattern.ifBlank { DEFAULT_PATTERN }.let {
            if (it.contains("{op}")) it else "${it}_{op}"
        }
        val body = effectivePattern.replace("{name}", name).replace("{op}", op)
        return "${sanitize(body)}.$extension"
    }

    /** 1-based inclusive ranges to a label: [1..4] -> "pages_1-4", [1..4, 8..8] -> "pages_1-4_8". */
    fun pagesOp(ranges: List<IntRange>): String {
        val parts = ranges.map { r -> if (r.first == r.last) "${r.first}" else "${r.first}-${r.last}" }
        return "pages_" + parts.joinToString("_")
    }

    /** Zero-padded page label for image export: page 7 of 120 -> "007". */
    fun pageNumberLabel(pageNumber: Int, pageCount: Int): String {
        val width = pageCount.toString().length.coerceAtLeast(1)
        return pageNumber.toString().padStart(width, '0')
    }

    const val DEFAULT_PATTERN = "{name}_{op}"
}
