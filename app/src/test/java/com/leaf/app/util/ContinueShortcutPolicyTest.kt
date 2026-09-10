package com.leaf.app.util

import com.leaf.app.data.db.entities.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueShortcutPolicyTest {

    private fun doc(
        id: Long,
        openedAt: Long?,
        name: String = "doc$id.pdf",
        label: String? = null,
        lost: Boolean = false,
        page: Int = 0,
        thumb: String? = null,
    ) = DocumentEntity(
        id = id,
        uri = "content://p/doc/$id",
        displayName = name,
        label = label,
        sizeBytes = null,
        pageCount = null,
        addedAt = 0L,
        lastOpenedAt = openedAt,
        lastPage = page,
        thumbnailPath = thumb,
        permissionLost = lost,
    )

    @Test
    fun mostRecentFirstWhateverTheInputOrder() {
        val chosen = ContinueShortcutPolicy.choose(listOf(doc(1, 100), doc(2, 300), doc(3, 200)), max = 3)
        assertEquals(listOf(2L, 3L, 1L), chosen.map { it.documentId })
    }

    @Test
    fun cappedAtMaxAndNothingWhenMaxIsZero() {
        val docs = listOf(doc(1, 100), doc(2, 300), doc(3, 200))
        assertEquals(listOf(2L, 3L), ContinueShortcutPolicy.choose(docs, max = 2).map { it.documentId })
        assertTrue(ContinueShortcutPolicy.choose(docs, max = 0).isEmpty())
        assertTrue(ContinueShortcutPolicy.choose(docs, max = -1).isEmpty())
    }

    @Test
    fun skipsUnreadableAndNeverOpenedRows() {
        val docs = listOf(doc(1, 400, lost = true), doc(2, null), doc(3, 200), doc(4, 300))
        assertEquals(listOf(4L, 3L), ContinueShortcutPolicy.choose(docs, max = 2).map { it.documentId })
    }

    @Test
    fun entryCarriesOneBasedPageLabelUriAndThumbnail() {
        val entry = ContinueShortcutPolicy.choose(
            listOf(doc(7, 1, name = "report.pdf", label = "Q3 report", page = 41, thumb = "/cache/7.webp")),
            max = 2,
        ).single()
        assertEquals("continue:7", entry.shortcutId)
        assertEquals("content://p/doc/7", entry.uri)
        assertEquals("Q3 report", entry.title)
        assertEquals("Q3 report", entry.shortLabel)
        assertEquals(42, entry.page)
        assertEquals("/cache/7.webp", entry.thumbnailPath)
    }

    @Test
    fun shortLabelKeepsShortTitlesAndTrimsLongOnesWithEllipsis() {
        assertEquals("Short title.pdf", ContinueShortcutPolicy.shortLabel("  Short title.pdf  "))
        val exact = "a".repeat(ContinueShortcutPolicy.MAX_LABEL_CHARS)
        assertEquals(exact, ContinueShortcutPolicy.shortLabel(exact))

        val long = "Annual financial statement 2025 final.pdf"
        val label = ContinueShortcutPolicy.shortLabel(long)
        assertTrue(label.length <= ContinueShortcutPolicy.MAX_LABEL_CHARS)
        assertTrue(label.endsWith("…"))
        assertEquals("Annual financial stateme…", label)
        // A space just before the cut does not leave a dangling gap before the ellipsis.
        assertEquals("Annual financial report…", ContinueShortcutPolicy.shortLabel("Annual financial report for you.pdf"))
    }
}
