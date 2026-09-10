package com.leaf.app.ui.reader

import com.leaf.app.data.db.entities.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionRestorePolicyTest {

    private fun doc(page: Int = 0, zoom: Float = 1f, scrollY: Int = 0) = DocumentEntity(
        id = 1,
        uri = "content://x/doc",
        displayName = "doc.pdf",
        sizeBytes = null,
        pageCount = null,
        addedAt = 0,
        lastOpenedAt = 0,
        lastPage = page,
        lastZoom = zoom,
        lastScrollY = scrollY,
        thumbnailPath = null,
    )

    @Test
    fun noRestoreAtDocumentStart() {
        assertNull(PositionRestorePolicy.targetFor(doc(), pageCount = 10))
    }

    @Test
    fun restoresSavedPage() {
        assertEquals(ReadingPosition(7, 1.5f, 120), PositionRestorePolicy.targetFor(doc(7, 1.5f, 120), pageCount = 10))
    }

    @Test
    fun clampsPageToDocument() {
        assertEquals(ReadingPosition(9, 1f, 0), PositionRestorePolicy.targetFor(doc(42), pageCount = 10))
    }

    @Test
    fun discardsNonsenseZoom() {
        assertEquals(ReadingPosition(3, 1f, 0), PositionRestorePolicy.targetFor(doc(3, zoom = 0f), pageCount = 10))
        assertEquals(ReadingPosition(3, 1f, 0), PositionRestorePolicy.targetFor(doc(3, zoom = Float.NaN), pageCount = 10))
        assertEquals(ReadingPosition(3, 1f, 0), PositionRestorePolicy.targetFor(doc(3, zoom = 999f), pageCount = 10))
    }

    @Test
    fun nothingToRestoreWithoutPages() {
        assertNull(PositionRestorePolicy.targetFor(doc(5), pageCount = 0))
    }

    @Test
    fun ignoresInitialTopOfDocumentWhileRestorePending() {
        assertFalse(PositionRestorePolicy.shouldRecord(ReadingPosition(0, 1f, 0), restorePending = true))
        assertTrue(PositionRestorePolicy.shouldRecord(ReadingPosition(0, 1f, 0), restorePending = false))
        assertTrue(PositionRestorePolicy.shouldRecord(ReadingPosition(4, 1f, 0), restorePending = true))
    }
}
