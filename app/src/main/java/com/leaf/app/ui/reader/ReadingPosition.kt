package com.leaf.app.ui.reader

import com.leaf.app.data.db.entities.DocumentEntity

data class ReadingPosition(val page: Int, val zoom: Float, val scrollY: Int)

/** Pure decisions about restoring a saved position, kept out of the ViewModel so they are testable. */
object PositionRestorePolicy {

    const val MIN_ZOOM = 0.25f
    const val MAX_ZOOM = 25f

    /**
     * What to restore for [document] once the viewer reports [pageCount] pages,
     * or null when the saved position is the start of the document or is nonsense.
     */
    fun targetFor(document: DocumentEntity, pageCount: Int): ReadingPosition? {
        if (pageCount <= 0) return null
        val page = document.lastPage.coerceIn(0, pageCount - 1)
        val zoom = document.lastZoom.takeIf { it.isFinite() && it in MIN_ZOOM..MAX_ZOOM } ?: 1f
        val scrollY = document.lastScrollY.coerceAtLeast(0)
        if (page == 0 && scrollY == 0 && zoom == 1f) return null
        return ReadingPosition(page = page, zoom = zoom, scrollY = scrollY)
    }

    /** Ignore viewport noise that would overwrite a good position with page 0 before restore lands. */
    fun shouldRecord(candidate: ReadingPosition, restorePending: Boolean): Boolean =
        !(restorePending && candidate.page == 0 && candidate.scrollY == 0)
}
