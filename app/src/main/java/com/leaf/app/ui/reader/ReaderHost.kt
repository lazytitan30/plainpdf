package com.leaf.app.ui.reader

import android.graphics.Bitmap
import java.io.File
import com.leaf.app.data.pdf.read.SearchMatch
import com.leaf.app.data.prefs.PageDisplayMode

/** What the PDF fragment tells the reader screen. Called on the main thread. */
interface ReaderHost {
    /** Document is open. Return a position to restore, or null to stay at the top. */
    fun onDocumentLoaded(pageCount: Int): ReadingPosition?
    fun onViewportChanged(firstVisiblePage: Int, visiblePagesCount: Int, zoom: Float, scrollY: Int)
    fun onLoadError(error: Throwable)
    /** The library asked to enter or leave immersive mode, typically because the user scrolled. */
    fun onImmersiveModeRequested(enterImmersive: Boolean)
    /**
     * A single tap that should toggle the chrome: anywhere on the page that is not a link or
     * form field in continuous mode, the centre third in paged mode.
     */
    fun onCentreTap()
}

/** What the reader screen asks the fragment to do. */
interface PdfController {
    val isDocumentLoaded: Boolean
    fun scrollToPage(pageIndex: Int)
    fun scrollToMatch(match: SearchMatch)
    suspend fun search(query: String): List<SearchMatch>
    /** Paints all matches; the one at [currentIndex] gets [currentColor]. Empty list clears. */
    fun highlight(matches: List<SearchMatch>, currentIndex: Int, color: Int, currentColor: Int)
    suspend fun renderPage(pageIndex: Int, widthPx: Int): Bitmap?
    fun setPageDisplayMode(mode: PageDisplayMode)

    /** True when the document has an AcroForm and this device can edit and save it. */
    val hasForm: Boolean
    fun setFormFilling(enabled: Boolean)

    /** True once a field has been changed since form filling was last switched on. */
    val hasFormEdits: Boolean

    /** Writes the document with its in-memory form edits to [target]. False when unsupported or failed. */
    suspend fun writeEditedCopy(target: File): Boolean

    /** True when this device and document can take ink and highlight annotations and save them. */
    val hasAnnotations: Boolean

    /** Shows or hides the drawing toolbar. Switching off throws away unsaved strokes. */
    fun setAnnotating(enabled: Boolean)

    /** True while there are strokes that have not been written to a copy yet. */
    val hasAnnotationEdits: Boolean

    /** Applies the drawn strokes and writes the result to [target]. False when unsupported or failed. */
    suspend fun writeAnnotatedCopy(target: File): Boolean
}
