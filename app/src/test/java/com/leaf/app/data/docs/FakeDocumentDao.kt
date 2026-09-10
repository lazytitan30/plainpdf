package com.leaf.app.data.docs

import com.leaf.app.data.db.DocumentDao
import com.leaf.app.data.db.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory DAO with the same semantics as the Room one, including IGNORE on unique uri. */
class FakeDocumentDao : DocumentDao {

    private val rows = MutableStateFlow<Map<Long, DocumentEntity>>(emptyMap())
    private var nextId = 1L

    val all: List<DocumentEntity> get() = rows.value.values.sortedBy { it.id }

    private fun mutate(id: Long, f: (DocumentEntity) -> DocumentEntity) {
        val row = rows.value[id] ?: return
        rows.value = rows.value + (id to f(row))
    }

    override suspend fun findByUri(uri: String) = rows.value.values.firstOrNull { it.uri == uri }
    override suspend fun findById(id: Long) = rows.value[id]
    override fun observe(id: Long): Flow<DocumentEntity?> = rows.map { it[id] }
    override fun observeRecent(): Flow<List<DocumentEntity>> =
        rows.map { m -> m.values.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt } }
    override fun observeFavourites(): Flow<List<DocumentEntity>> =
        rows.map { m -> m.values.filter { it.isFavorite }.sortedBy { it.displayName.lowercase() } }
    override fun observeInFolder(folderId: Long): Flow<List<DocumentEntity>> =
        rows.map { m -> m.values.filter { it.folderId == folderId }.sortedBy { it.displayName.lowercase() } }
    override suspend fun listInFolder(folderId: Long) = rows.value.values.filter { it.folderId == folderId }
    override suspend fun listWithThumbnails() = rows.value.values.filter { it.thumbnailPath != null }

    override suspend fun insert(document: DocumentEntity): Long {
        if (rows.value.values.any { it.uri == document.uri }) return -1
        val id = if (document.id == 0L) nextId++ else document.id
        rows.value = rows.value + (id to document.copy(id = id))
        return id
    }

    override suspend fun update(document: DocumentEntity) {
        if (rows.value.containsKey(document.id)) rows.value = rows.value + (document.id to document)
    }

    override suspend fun updatePosition(id: Long, page: Int, zoom: Float, scrollY: Int) =
        mutate(id) { it.copy(lastPage = page, lastZoom = zoom, lastScrollY = scrollY) }
    override suspend fun updatePageCount(id: Long, pageCount: Int) = mutate(id) { it.copy(pageCount = pageCount) }
    override suspend fun touch(id: Long, at: Long) = mutate(id) { it.copy(lastOpenedAt = at) }
    override suspend fun clearLastOpened(id: Long) = mutate(id) { it.copy(lastOpenedAt = null) }
    override suspend fun setLabel(id: Long, label: String?) = mutate(id) { it.copy(label = label) }
    override suspend fun setFavorite(id: Long, favorite: Boolean) = mutate(id) { it.copy(isFavorite = favorite) }
    override suspend fun setPermissionLost(id: Long, lost: Boolean) = mutate(id) { it.copy(permissionLost = lost) }
    override suspend fun setThumbnailPath(id: Long, path: String?) = mutate(id) { it.copy(thumbnailPath = path) }
    override suspend fun clearAllThumbnailPaths() {
        rows.value = rows.value.mapValues { (_, d) -> d.copy(thumbnailPath = null) }
    }
    override suspend fun setPageDisplayMode(id: Long, mode: String?) = mutate(id) { it.copy(pageDisplayMode = mode) }
    override suspend fun setEncrypted(id: Long, encrypted: Boolean) = mutate(id) { it.copy(isEncrypted = encrypted) }
    override suspend fun setFolder(id: Long, folderId: Long?) = mutate(id) { it.copy(folderId = folderId) }

    override suspend fun deleteUntouchedInFolder(folderId: Long) {
        rows.value = rows.value.filterValues { !(it.folderId == folderId && !it.isFavorite && it.lastOpenedAt == null) }
    }

    override suspend fun detachFolder(folderId: Long) {
        rows.value = rows.value.mapValues { (_, d) -> if (d.folderId == folderId) d.copy(folderId = null) else d }
    }

    override suspend fun hideFolderDocumentsFromRecents() {
        rows.value = rows.value.mapValues { (_, d) -> if (!d.isFavorite && d.folderId != null) d.copy(lastOpenedAt = null) else d }
    }

    override suspend fun deleteAdHocNonFavourites() {
        rows.value = rows.value.filterValues { it.isFavorite || it.folderId != null }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value - id
    }
}
