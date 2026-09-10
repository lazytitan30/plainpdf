package com.leaf.app.util

/**
 * Parses user-typed page ranges like "1-4, 8, 12-20". Input and output are 1-based
 * inclusive because that is what the user sees; convert with [toZeroBased] before use.
 */
object PageRanges {

    sealed interface Result {
        data class Ok(val ranges: List<IntRange>) : Result
        data class Error(val kind: ErrorKind, val token: String? = null) : Result
    }

    enum class ErrorKind { EMPTY, BAD_TOKEN, OUT_OF_RANGE, REVERSED }

    private val TOKEN = Regex("^(\\d+)(?:\\s*-\\s*(\\d+))?$")

    fun parse(input: String, pageCount: Int): Result {
        val tokens = input.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return Result.Error(ErrorKind.EMPTY)
        val ranges = ArrayList<IntRange>(tokens.size)
        for (token in tokens) {
            val match = TOKEN.matchEntire(token) ?: return Result.Error(ErrorKind.BAD_TOKEN, token)
            val start = match.groupValues[1].toIntOrNull() ?: return Result.Error(ErrorKind.BAD_TOKEN, token)
            val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.let {
                it.toIntOrNull() ?: return Result.Error(ErrorKind.BAD_TOKEN, token)
            } ?: start
            if (start < 1 || end > pageCount) return Result.Error(ErrorKind.OUT_OF_RANGE, token)
            if (end < start) return Result.Error(ErrorKind.REVERSED, token)
            ranges.add(start..end)
        }
        return Result.Ok(ranges)
    }

    fun toZeroBased(ranges: List<IntRange>): List<IntRange> = ranges.map { (it.first - 1)..(it.last - 1) }

    fun pageCountOf(ranges: List<IntRange>): List<Int> = ranges.map { it.last - it.first + 1 }

    /** Chunk 1..pageCount into consecutive groups of [n]; the last group may be shorter. */
    fun everyN(pageCount: Int, n: Int): List<IntRange> {
        require(n >= 1) { "n must be >= 1" }
        if (pageCount <= 0) return emptyList()
        return (1..pageCount step n).map { start -> start..minOf(start + n - 1, pageCount) }
    }
}
