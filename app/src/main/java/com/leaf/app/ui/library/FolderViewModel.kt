package com.leaf.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.data.docs.DocumentRepository
import com.leaf.app.data.docs.FolderRepository
import com.leaf.app.data.docs.ThumbnailGenerator
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderUiState(
    val name: String = "",
    val documents: List<DocumentEntity> = emptyList(),
    val refreshing: Boolean = true,
    /** The tree could not be read: grant revoked or storage gone. */
    val unavailable: Boolean = false,
)

class FolderViewModel(
    private val folderId: Long,
    private val folders: FolderRepository,
    private val documents: DocumentRepository,
    private val thumbnails: ThumbnailGenerator,
    private val saf: SafAccess,
) : ViewModel() {

    private val name = MutableStateFlow("")
    private val refreshing = MutableStateFlow(true)
    private val unavailable = MutableStateFlow(false)
    private val requestedThumbnails = HashSet<Long>()

    val state: StateFlow<FolderUiState> = combine(
        name,
        documents.observeInFolder(folderId),
        refreshing,
        unavailable,
    ) { n, docs, r, u -> FolderUiState(name = n, documents = docs, refreshing = r, unavailable = u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderUiState())

    init {
        viewModelScope.launch {
            name.value = folders.findById(folderId)?.displayName ?: ""
            refresh()
        }
    }

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        val count = folders.refresh(folderId)
        unavailable.value = count == null
        refreshing.value = false
    }

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

    /** "Locate again": rebind the row so bookmarks and position follow the new URI. */
    fun relocate(document: DocumentEntity, newUri: Uri, resultFlags: Int, onDone: (DocumentEntity?) -> Unit) =
        viewModelScope.launch {
            saf.takePersistable(newUri, resultFlags)
            requestedThumbnails.remove(document.id)
            onDone(documents.rebind(document.id, newUri.toString()))
        }
}
