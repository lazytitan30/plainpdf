package com.leaf.app.data.docs

import com.leaf.app.util.saf.GrantFlags
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentRepositoryTest {

    private class FakeProbe : DocumentProbe {
        val infos = mutableMapOf<String, DocumentProbe.Info>()
        val readable = mutableSetOf<String>()
        val grants = mutableMapOf<String, GrantFlags>()

        fun add(uri: String, name: String, size: Long? = 1234, grant: GrantFlags = GrantFlags.ReadOnly) {
            infos[uri] = DocumentProbe.Info(name, size)
            readable += uri
            grants[uri] = grant
        }

        fun lose(uri: String) {
            infos.remove(uri)
            readable.remove(uri)
            grants.remove(uri)
        }

        override fun info(uri: String) = infos[uri]
        override fun canRead(uri: String) = uri in readable
        override fun persistedGrant(uri: String) = grants[uri] ?: GrantFlags.None
    }

    private val dao = FakeDocumentDao()
    private val probe = FakeProbe()
    private var now = 1_000L
    private val repo = DocumentRepository(dao, probe, clock = { now })

    private val uri = "content://provider/document/primary%3Areport.pdf"

    @Test
    fun firstOpenCreatesRowWithProviderMetadata() = runTest {
        probe.add(uri, "report.pdf", size = 2048, grant = GrantFlags.ReadWrite)
        val doc = repo.openedNow(uri)
        assertTrue(doc.id > 0)
        assertEquals("report.pdf", doc.displayName)
        assertEquals(2048L, doc.sizeBytes)
        assertEquals(1_000L, doc.addedAt)
        assertEquals(1_000L, doc.lastOpenedAt)
        assertTrue(doc.hasWriteGrant)
        assertTrue(doc.isPersisted)
        assertFalse(doc.permissionLost)
    }

    @Test
    fun secondOpenUpdatesRatherThanDuplicates() = runTest {
        probe.add(uri, "report.pdf")
        val first = repo.openedNow(uri)
        now = 2_000L
        probe.infos[uri] = DocumentProbe.Info("report (renamed).pdf", 999)
        val second = repo.openedNow(uri)
        assertEquals(first.id, second.id)
        assertEquals(2_000L, second.lastOpenedAt)
        assertEquals("report (renamed).pdf", second.displayName)
        assertEquals(1, dao.all.size)
    }

    @Test
    fun oneShotGrantIsRecordedAsNotPersisted() = runTest {
        probe.infos[uri] = DocumentProbe.Info("shared.pdf", null)
        probe.readable += uri
        val doc = repo.openedNow(uri)
        assertFalse(doc.isPersisted)
        assertFalse(doc.hasWriteGrant)
        assertFalse(doc.permissionLost)
    }

    @Test
    fun unreadableUriIsFlaggedNotDropped() = runTest {
        probe.add(uri, "report.pdf")
        val doc = repo.openedNow(uri)
        probe.lose(uri)
        assertFalse(repo.verifyAccess(doc.id))
        val after = repo.findById(doc.id)
        assertNotNull(after)
        assertTrue(after?.permissionLost == true)
        // Opening again keeps the row and the flag, with the old name as fallback.
        val reopened = repo.openedNow(uri)
        assertEquals(doc.id, reopened.id)
        assertTrue(reopened.permissionLost)
        assertEquals("report.pdf", reopened.displayName)
    }

    @Test
    fun unknownUriUsesHintThenPathTail() = runTest {
        val hinted = repo.openedNow("content://p/doc/abc", displayNameHint = "hint.pdf")
        assertEquals("hint.pdf", hinted.displayName)
        assertTrue(hinted.permissionLost)
        val tail = repo.openedNow("content://p/document/primary%3ADownload%2Fx.pdf")
        assertEquals("x.pdf", tail.displayName)
    }

    @Test
    fun savePositionClampsNegatives() = runTest {
        probe.add(uri, "report.pdf")
        val doc = repo.openedNow(uri)
        repo.savePosition(doc.id, page = -3, zoom = 2f, scrollY = -10)
        val saved = repo.findById(doc.id)
        assertEquals(0, saved?.lastPage)
        assertEquals(0, saved?.lastScrollY)
        assertEquals(2f, saved?.lastZoom ?: 0f, 0.0001f)
    }

    @Test
    fun rebindMovesRowToNewUriAndRemovesClash() = runTest {
        probe.add(uri, "report.pdf")
        val original = repo.openedNow(uri)
        repo.savePosition(original.id, 5, 1f, 0)
        val newUri = "content://provider/document/primary%3Amoved%2Freport.pdf"
        probe.add(newUri, "report.pdf", grant = GrantFlags.ReadWrite)
        val clash = repo.openedNow(newUri)
        assertTrue(clash.id != original.id)

        val rebound = repo.rebind(original.id, newUri)
        assertNotNull(rebound)
        assertEquals(newUri, rebound?.uri)
        assertEquals(5, rebound?.lastPage)
        assertTrue(rebound?.hasWriteGrant == true)
        assertFalse(rebound?.permissionLost == true)
        assertNull(repo.findById(clash.id))
        assertEquals(1, dao.all.size)
    }

    @Test
    fun clearRecentsKeepsFavouritesAndFolderRows() = runTest {
        probe.add("content://a", "a.pdf")
        probe.add("content://b", "b.pdf")
        probe.add("content://c", "c.pdf")
        val a = repo.openedNow("content://a")
        val b = repo.openedNow("content://b")
        repo.setFavorite(b.id, true)
        val c = repo.openedNow("content://c")
        dao.update(checkNotNull(dao.findById(c.id)).copy(folderId = 9))

        repo.clearRecents()

        assertNull(repo.findById(a.id))
        assertNotNull(repo.findById(b.id))
        val folderRow = repo.findById(c.id)
        assertNotNull(folderRow)
        assertNull(folderRow?.lastOpenedAt)
    }

    @Test
    fun labelOverridesTitleButNotProviderName() = runTest {
        probe.add(uri, "report.pdf")
        val doc = repo.openedNow(uri)
        repo.setLabel(doc.id, "  Q3 report  ")
        val labelled = checkNotNull(repo.findById(doc.id))
        assertEquals("Q3 report", labelled.title)
        assertEquals("report.pdf", labelled.displayName)
        // Reopening refreshes the provider name but keeps the label.
        probe.infos[uri] = DocumentProbe.Info("report-v2.pdf", 1)
        val reopened = repo.openedNow(uri)
        assertEquals("Q3 report", reopened.title)
        assertEquals("report-v2.pdf", reopened.displayName)
        repo.setLabel(doc.id, "   ")
        assertEquals("report-v2.pdf", checkNotNull(repo.findById(doc.id)).title)
    }

    @Test
    fun removeFromRecentsDeletesAdHocButKeepsStarredAndFolderRows() = runTest {
        probe.add("content://a", "a.pdf")
        probe.add("content://b", "b.pdf")
        probe.add("content://c", "c.pdf")
        val a = repo.openedNow("content://a")
        val b = repo.openedNow("content://b")
        repo.setFavorite(b.id, true)
        val c = repo.indexInFolder(7, "content://c", "c.pdf", 10)
        repo.openedNow("content://c")

        repo.removeFromRecents(a.id)
        repo.removeFromRecents(b.id)
        repo.removeFromRecents(c.id)

        assertNull(repo.findById(a.id))
        assertNull(checkNotNull(repo.findById(b.id)).lastOpenedAt)
        assertNull(checkNotNull(repo.findById(c.id)).lastOpenedAt)
        assertEquals(7L, checkNotNull(repo.findById(c.id)).folderId)
    }

    @Test
    fun indexInFolderNeverStampsLastOpened() = runTest {
        val row = repo.indexInFolder(3, "content://tree/doc/x", "x.pdf", 500)
        assertNull(row.lastOpenedAt)
        assertEquals(3L, row.folderId)
        assertEquals(500L, row.sizeBytes)
        // Indexing an already-opened ad hoc document adopts it into the folder.
        probe.add("content://tree/doc/y", "y.pdf")
        val opened = repo.openedNow("content://tree/doc/y")
        val adopted = repo.indexInFolder(3, "content://tree/doc/y", "y.pdf", null)
        assertEquals(opened.id, adopted.id)
        assertEquals(3L, adopted.folderId)
        assertEquals(opened.lastOpenedAt, adopted.lastOpenedAt)
    }

    @Test
    fun shortcutPublisherSeesRecentListAfterOpenRemoveAndClear() = runTest {
        val published = mutableListOf<List<String>>()
        val repo = DocumentRepository(dao, probe, clock = { now }, shortcuts = { recent -> published += recent.map { it.uri } })
        probe.add("content://a", "a.pdf")
        probe.add("content://b", "b.pdf")
        val a = repo.openedNow("content://a")
        now = 2_000L
        repo.openedNow("content://b")
        assertEquals(listOf("content://b", "content://a"), published.last())

        repo.removeFromRecents(a.id)
        assertEquals(listOf("content://b"), published.last())

        repo.clearRecents()
        assertTrue(published.last().isEmpty())
    }

    @Test
    fun folderRemovedKeepsTouchedRowsAsAdHoc() = runTest {
        repo.indexInFolder(5, "content://f/untouched", "u.pdf", null)
        val opened = repo.indexInFolder(5, "content://f/opened", "o.pdf", null)
        probe.add("content://f/opened", "o.pdf")
        repo.openedNow("content://f/opened")
        val starred = repo.indexInFolder(5, "content://f/starred", "s.pdf", null)
        repo.setFavorite(starred.id, true)

        repo.folderRemoved(5)

        assertNull(repo.findByUri("content://f/untouched"))
        assertNull(checkNotNull(repo.findById(opened.id)).folderId)
        assertNull(checkNotNull(repo.findById(starred.id)).folderId)
    }
}
