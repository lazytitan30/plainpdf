package com.leaf.app.data.docs

import com.leaf.app.data.db.DocumentDao
import com.leaf.app.data.db.entities.DocumentEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything the app knows about documents it has seen. Every function that touches a
 * URI goes through [DocumentProbe], which already swallows SecurityException and
 * FileNotFoundException, so callers get nulls and flags rather than crashes.
 */
class DocumentRepository(
    private val dao: DocumentDao,
    private val probe: DocumentProbe,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Display name when nothing better is known; the app passes the localised string. */
    private val fallbackDisplayName: String = "document.pdf",
    /** Told about the recent list whenever it, a title or a reading position changes. */
    private val shortcuts: ShortcutPublisher = ShortcutPublisher.None,
) {

    fun observeRecent(): Flow<List<DocumentEntity>> = dao.observeRecent()
    fun observeFavourites(): Flow<List<DocumentEntity>> = dao.observeFavourites()
    fun observeInFolder(folderId: Long): Flow<List<DocumentEntity>> = dao.observeInFolder(folderId)
    fun observe(id: Long): Flow<DocumentEntity?> = dao.observe(id)

    suspend fun findById(id: Long): DocumentEntity? = dao.findById(id)
    suspend fun findByUri(uri: String): DocumentEntity? = dao.findByUri(uri)

    /**
     * Called when the user opens [uri]. Creates the row on first sight, otherwise refreshes
     * name, size and grant state, and stamps lastOpenedAt. Returns the current row.
     */
    suspend fun openedNow(uri: String, displayNameHint: String? = null): DocumentEntity = withContext(io) {
        val now = clock()
        val info = probe.info(uri)
        val grant = probe.persistedGrant(uri)
        val existing = dao.findByUri(uri)
        val readable = info != null || probe.canRead(uri)

        if (existing == null) {
            val fresh = DocumentEntity(
                uri = uri,
                displayName = info?.displayName ?: displayNameHint ?: fallbackName(uri),
                sizeBytes = info?.sizeBytes,
                pageCount = null,
                addedAt = now,
                lastOpenedAt = now,
                thumbnailPath = null,
                permissionLost = !readable,
                hasWriteGrant = grant.write,
                isPersisted = grant.read,
            )
            val id = dao.insert(fresh)
            // A concurrent insert of the same URI is IGNOREd; re-read either way.
            val row = if (id > 0) fresh.copy(id = id) else (dao.findByUri(uri) ?: fresh)
            refreshShortcuts()
            return@withContext row
        }

        val refreshed = existing.copy(
            displayName = info?.displayName ?: existing.displayName,
            sizeBytes = info?.sizeBytes ?: existing.sizeBytes,
            lastOpenedAt = now,
            permissionLost = !readable,
            hasWriteGrant = grant.write || existing.hasWriteGrant && grant.read,
            isPersisted = grant.read || existing.isPersisted,
        )
        dao.update(refreshed)
        refreshShortcuts()
        refreshed
    }

    /**
     * Index a PDF found inside a folder tree. Never stamps lastOpenedAt, so it does not
     * appear in Recent until the user actually opens it.
     */
    suspend fun indexInFolder(folderId: Long, uri: String, displayName: String, sizeBytes: Long?): DocumentEntity {
        val existing = dao.findByUri(uri)
        if (existing != null) {
            val updated = existing.copy(
                folderId = folderId,
                displayName = displayName,
                sizeBytes = sizeBytes ?: existing.sizeBytes,
                permissionLost = false,
            )
            if (updated != existing) dao.update(updated)
            return updated
        }
        val fresh = DocumentEntity(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            pageCount = null,
            addedAt = clock(),
            lastOpenedAt = null,
            thumbnailPath = null,
            folderId = folderId,
            isPersisted = true,
        )
        val id = dao.insert(fresh)
        return if (id > 0) fresh.copy(id = id) else (dao.findByUri(uri) ?: fresh)
    }

    suspend fun listInFolder(folderId: Long): List<DocumentEntity> = dao.listInFolder(folderId)

    suspend fun setFolderless(id: Long) = dao.setFolder(id, null)

    suspend fun folderRemoved(folderId: Long) {
        dao.deleteUntouchedInFolder(folderId)
        dao.detachFolder(folderId)
        refreshShortcuts()
    }

    suspend fun savePosition(id: Long, page: Int, zoom: Float, scrollY: Int) {
        dao.updatePosition(id, page.coerceAtLeast(0), zoom, scrollY.coerceAtLeast(0))
        // The shortcut's "page N" follows along; the publisher skips the launcher when nothing changed.
        refreshShortcuts()
    }

    suspend fun setPageCount(id: Long, pageCount: Int) = dao.updatePageCount(id, pageCount)

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun setLabel(id: Long, label: String?) {
        dao.setLabel(id, label?.trim()?.takeIf { it.isNotEmpty() })
        refreshShortcuts()
    }

    suspend fun setEncrypted(id: Long, encrypted: Boolean) = dao.setEncrypted(id, encrypted)

    suspend fun setPageDisplayMode(id: Long, mode: String?) = dao.setPageDisplayMode(id, mode)

    suspend fun setThumbnailPath(id: Long, path: String?) = dao.setThumbnailPath(id, path)

    suspend fun listWithThumbnails(): List<DocumentEntity> = dao.listWithThumbnails()

    suspend fun clearAllThumbnailPaths() = dao.clearAllThumbnailPaths()

    /** Re-check a row that failed to open. Flags it rather than dropping it. */
    suspend fun verifyAccess(id: Long): Boolean = withContext(io) {
        val doc = dao.findById(id) ?: return@withContext false
        val ok = probe.canRead(doc.uri)
        if (ok != !doc.permissionLost) {
            dao.setPermissionLost(id, !ok)
            refreshShortcuts()
        }
        ok
    }

    /** "Locate again": bind an existing row to the URI the user just picked. */
    suspend fun rebind(id: Long, newUri: String): DocumentEntity? = withContext(io) {
        val doc = dao.findById(id) ?: return@withContext null
        val info = probe.info(newUri)
        val grant = probe.persistedGrant(newUri)
        val clash = dao.findByUri(newUri)
        if (clash != null && clash.id != id) dao.delete(clash.id)
        val updated = doc.copy(
            uri = newUri,
            displayName = info?.displayName ?: doc.displayName,
            sizeBytes = info?.sizeBytes ?: doc.sizeBytes,
            permissionLost = false,
            hasWriteGrant = grant.write,
            isPersisted = grant.read,
            thumbnailPath = null,
        )
        dao.update(updated)
        refreshShortcuts()
        updated
    }

    /** Remove from recents: ad hoc, unstarred rows go away; anything else just leaves the list. */
    suspend fun removeFromRecents(id: Long) {
        val doc = dao.findById(id) ?: return
        if (doc.folderId == null && !doc.isFavorite) deleteRow(doc) else dao.clearLastOpened(id)
        refreshShortcuts()
    }

    suspend fun remove(id: Long) {
        dao.findById(id)?.let { deleteRow(it) }
        refreshShortcuts()
    }

    private suspend fun deleteRow(doc: DocumentEntity) {
        doc.thumbnailPath?.let { path -> withContext(io) { File(path).delete() } }
        dao.delete(doc.id)
    }

    /** Clear recents: ad hoc, non-favourite rows go away; folder rows just leave the list. */
    suspend fun clearRecents() {
        dao.hideFolderDocumentsFromRecents()
        dao.deleteAdHocNonFavourites()
        refreshShortcuts()
    }

    /** Hands the launcher the current recent list. Off the main thread: it may decode a thumbnail. */
    private suspend fun refreshShortcuts() {
        val recent = dao.observeRecent().first()
        withContext(io) { shortcuts.publish(recent) }
    }

    private fun fallbackName(uri: String): String {
        val tail = uri.substringAfterLast('/').substringAfterLast("%2F").substringAfterLast("%3A")
        return tail.ifBlank { fallbackDisplayName }
    }
}
