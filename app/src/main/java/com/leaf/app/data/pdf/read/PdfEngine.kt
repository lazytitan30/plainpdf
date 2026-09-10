package com.leaf.app.data.pdf.read

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import java.io.Closeable

/**
 * Thin seam over the rendering library. androidx.pdf is beta and its API moves; nothing
 * outside this package imports it except the fragment host in the reader.
 */
interface PdfEngine {
    suspend fun open(uri: Uri, password: String? = null): Result<PdfHandle>
}

interface PdfHandle : Closeable {
    val pageCount: Int

    /** Document outline. Empty when the document has none or the engine cannot read it. */
    val outline: List<OutlineNode>

    suspend fun search(query: String): List<SearchMatch>

    /** Page dimensions in PDF points. */
    suspend fun pageSize(pageIndex: Int): PageDimensions

    /** Renders [pageIndex] scaled to [widthPx] wide, keeping aspect ratio. */
    suspend fun renderPage(pageIndex: Int, widthPx: Int): Bitmap
}

data class OutlineNode(
    val title: String,
    val pageIndex: Int,
    val level: Int,
    val children: List<OutlineNode> = emptyList(),
)

data class SearchMatch(val pageIndex: Int, val bounds: List<RectF>, val textStartIndex: Int)

data class PageDimensions(val widthPt: Int, val heightPt: Int)

class PdfOpenException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { PASSWORD_REQUIRED, NOT_FOUND, PERMISSION_DENIED, CORRUPT, UNKNOWN }
}
