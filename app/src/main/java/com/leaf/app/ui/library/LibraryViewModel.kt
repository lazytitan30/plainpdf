package com.leaf.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.data.db.entities.FolderEntity
import com.leaf.app.data.docs.DocumentRepository
import com.leaf.app.data.docs.FolderRepository
import com.leaf.app.data.docs.ThumbnailGenerator
import com.leaf.app.data.prefs.LibraryLayout
import com.leaf.app.data.prefs.LibrarySort
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { RECENT, FOLDERS, FAVOURITES }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.RECENT,
    val query: String = "",
    val searching: Boolean = false,
    val sort: LibrarySort = LibrarySort.RECENTLY_OPENED,
    val layout: LibraryLayout = LibraryLayout.LIST,
    val recent: List<DocumentEntity> = emptyList(),
    val favourites: List<DocumentEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    /** Last document opened that can still be read; null while searching or when there is none. */
    val continueReading: DocumentEntity? = null,
    val loaded: Boolean = false,
)

class LibraryViewModel(
    private val documents: DocumentRepository,
    private val folders: FolderRepository,
    private val thumbnails: ThumbnailGenerator,
    private val settings: SettingsRepository,
    private val saf: SafAccess,
) : ViewModel() {

    private val tab = MutableStateFlow(LibraryTab.RECENT)
    private val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    /** Session override of the sort; null follows the default from Settings. */
    private val sortOverride = MutableStateFlow<LibrarySort?>(null)
    private val requestedThumbnails = HashSet<Long>()

    val state: StateFlow<LibraryUiState> = combine(
        combine(tab, query, searching, sortOverride) { t, q, s, so -> Quad(t, q, s, so) },
        settings.settings.map { it.librarySort to it.libraryLayout },
        documents.observeRecent(),
        documents.observeFavourites(),
        folders.observeAll(),
    ) { (t, q, s, so), (defaultSort, layout), recent, favourites, folderList ->
        val sort = so ?: defaultSort
        LibraryUiState(
            tab = t,
            query = q,
            searching = s,
            sort = sort,
            layout = layout,
            recent = recent.filterBy(q).sortedWith(sort),
            favourites = favourites.filterBy(q).sortedWith(sort),
            folders = folderList.filter { q.isBlank() || it.displayName.contains(q, ignoreCase = true) },
            // The DAO already orders by lastOpenedAt; the user's sort choice does not move the card.
            continueReading = if (q.isBlank()) recent.firstOrNull { !it.permissionLost && it.lastOpenedAt != null } else null,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun selectTab(value: LibraryTab) { tab.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setSearching(value: Boolean) {
        searching.value = value
        if (!value) query.value = ""
    }
    fun setSort(value: LibrarySort) { sortOverride.value = value }
    fun setLayout(value: LibraryLayout) = viewModelScope.launch { settings.setLibraryLayout(value) }

    /** Called by rows as they become visible. Idempotent per document. */
    fun requestThumbnail(document: DocumentEntity) {
        if (document.permissionLost || !requestedThumbnails.add(document.id)) return
        viewModelScope.launch {
            val path = thumbnails.ensure(document.id, Uri.parse(document.uri), document.thumbnailPath)
            if (path != document.thumbnailPath) documents.setThumbnailPath(document.id, path)
            if (path == null) requestedThumbnails.remove(document.id)
        }
    }

    fun toggleFavourite(document: DocumentEntity) = viewModelScope.launch {
        documents.setFavorite(document.id, !document.isFavorite)
    }

    fun rename(document: DocumentEntity, label: String) = viewModelScope.launch {
        documents.setLabel(document.id, label)
    }

    fun removeFromRecents(document: DocumentEntity) = viewModelScope.launch {
        documents.removeFromRecents(document.id)
    }

    /** Deletes the file through its provider, then the row. Returns false if the provider refused. */
    fun deleteFile(document: DocumentEntity, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = saf.delete(Uri.parse(document.uri))
        if (ok) documents.remove(document.id)
        onResult(ok)
    }

    /** "Locate again": rebind a lost row to a freshly picked URI. */
    fun relocate(document: DocumentEntity, newUri: Uri, resultFlags: Int, onDone: (DocumentEntity?) -> Unit) =
        viewModelScope.launch {
            saf.takePersistable(newUri, resultFlags)
            requestedThumbnails.remove(document.id)
            onDone(documents.rebind(document.id, newUri.toString()))
        }

    fun addFolder(treeUri: Uri, onAdded: (FolderEntity?) -> Unit) = viewModelScope.launch {
        val folder = folders.add(treeUri)
        if (folder != null) folders.refresh(folder.id)
        onAdded(folder)
    }

    fun removeFolder(folder: FolderEntity) = viewModelScope.launch { folders.remove(folder.id) }

    private fun List<DocumentEntity>.filterBy(query: String): List<DocumentEntity> =
        if (query.isBlank()) this else filter { it.title.contains(query, ignoreCase = true) }

    private fun List<DocumentEntity>.sortedWith(sort: LibrarySort): List<DocumentEntity> = when (sort) {
        LibrarySort.RECENTLY_OPENED -> sortedByDescending { it.lastOpenedAt ?: 0L }
        LibrarySort.RECENTLY_ADDED -> sortedByDescending { it.addedAt }
        LibrarySort.NAME -> sortedBy { it.title.lowercase() }
        LibrarySort.SIZE -> sortedByDescending { it.sizeBytes ?: -1L }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
