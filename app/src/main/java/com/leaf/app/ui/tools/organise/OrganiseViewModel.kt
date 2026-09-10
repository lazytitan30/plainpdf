package com.leaf.app.ui.tools.organise

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.docs.DocumentRepository
import com.leaf.app.data.pdf.read.PdfEngine
import com.leaf.app.data.pdf.read.PdfHandle
import com.leaf.app.data.pdf.read.PdfOpenException
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.pdf.write.PageOp
import com.leaf.app.data.pdf.write.PagePlan
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.ui.reader.PageThumbnailCache
import com.leaf.app.util.saf.OutputNames
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One tile in the organiser. [uid] survives reorders; [sourceIndex] is the page it came from. */
data class PageItem(
    val uid: Int,
    val sourceIndex: Int,
    val rotation: Int = 0,
    val deleted: Boolean = false,
)

data class OrganiseUiState(
    val uri: Uri,
    val displayName: String = "",
    val pageCount: Int = 0,
    val items: List<PageItem> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val changes: Int = 0,
    val hasWriteGrant: Boolean = false,
    val confirmOverwrite: Boolean = true,
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
    val loading: Boolean = true,
    /** The document is encrypted and we have not been given its password yet. */
    val needsPassword: Boolean = false,
    val passwordWrong: Boolean = false,
    val loadError: Boolean = false,
) {
    val livePages: Int get() = items.count { !it.deleted }
    val plan: PagePlan get() = PagePlan(items.filter { !it.deleted }.map { PageOp(it.sourceIndex, it.rotation) })
    val selectedDeleted: Boolean get() = items.any { it.uid in selected && it.deleted }
    /** Nothing selected would survive an extract, so the plan would be empty. */
    val selectedAllDeleted: Boolean get() = items.none { it.uid in selected && !it.deleted }
}

class OrganiseViewModel(
    uriString: String,
    private val documents: DocumentRepository,
    private val engine: PdfEngine,
    private val saf: SafAccess,
    private val settings: SettingsRepository,
    val launcher: OperationLauncher,
    /** Shown when the provider gives no display name; the UI passes the localised string. */
    private val fallbackName: String,
) : ViewModel() {

    private val uri = Uri.parse(uriString)
    private val _state = MutableStateFlow(OrganiseUiState(uri = uri))
    val state: StateFlow<OrganiseUiState> = _state

    private var handle: PdfHandle? = null
    private var password: String? = null
    private val undo = ArrayDeque<List<PageItem>>()
    private val redo = ArrayDeque<List<PageItem>>()
    private var nextUid = 0
    private val thumbnails = PageThumbnailCache(16 * 1024 * 1024)

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            val doc = documents.findByUri(uriString)
            val info = saf.queryInfo(uri)
            _state.update {
                it.copy(
                    displayName = doc?.title ?: info?.displayName ?: fallbackName,
                    hasWriteGrant = saf.persistedGrant(uri).write,
                    confirmOverwrite = s.confirmOverwrite,
                    outputPattern = s.outputNamePattern,
                )
            }
            open(null)
        }
    }

    private suspend fun open(pw: String?) {
        val result = engine.open(uri, pw)
        val h = result.getOrNull()
        if (h == null) {
            val kind = (result.exceptionOrNull() as? PdfOpenException)?.kind
            if (kind == PdfOpenException.Kind.PASSWORD_REQUIRED) {
                _state.update { it.copy(loading = false, needsPassword = true, passwordWrong = pw != null) }
            } else {
                _state.update { it.copy(loading = false, loadError = true) }
            }
            return
        }
        handle?.close()
        handle = h
        password = pw
        val items = List(h.pageCount) { PageItem(uid = nextUid++, sourceIndex = it) }
        _state.update { it.copy(loading = false, needsPassword = false, passwordWrong = false, pageCount = h.pageCount, items = items) }
    }

    fun submitPassword(pw: String) = viewModelScope.launch { open(pw) }

    fun passwords(): Map<Uri, String> = password?.let { mapOf(uri to it) } ?: emptyMap()

    suspend fun thumbnail(sourceIndex: Int, widthPx: Int): Bitmap? {
        val h = handle ?: return null
        return thumbnails.get(sourceIndex, widthPx) { runCatching { h.renderPage(sourceIndex, widthPx) }.getOrNull() }
    }

    // ---- Selection ----

    fun toggle(uid: Int) = _state.update { s ->
        s.copy(selected = if (uid in s.selected) s.selected - uid else s.selected + uid)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectAll() = _state.update { s -> s.copy(selected = s.items.map { it.uid }.toSet()) }

    // ---- Edits, all through one history ----

    private fun edit(transform: (List<PageItem>) -> List<PageItem>) {
        val before = _state.value.items
        val after = transform(before)
        if (after == before) return
        undo.addLast(before)
        if (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        _state.update { it.copy(items = after, canUndo = true, canRedo = false, changes = it.changes + 1) }
    }

    fun rotateSelected(delta: Int) = edit { items ->
        items.map { if (it.uid in _state.value.selected) it.copy(rotation = ((it.rotation + delta) % 360 + 360) % 360) else it }
    }

    fun deleteSelected() {
        edit { items -> items.map { if (it.uid in _state.value.selected) it.copy(deleted = true) else it } }
        clearSelection()
    }

    fun restoreSelected() {
        edit { items -> items.map { if (it.uid in _state.value.selected) it.copy(deleted = false) else it } }
        clearSelection()
    }

    fun duplicateSelected() {
        val selected = _state.value.selected
        edit { items ->
            buildList {
                for (item in items) {
                    add(item)
                    if (item.uid in selected) add(item.copy(uid = nextUid++, deleted = false))
                }
            }
        }
        clearSelection()
    }

    fun move(fromUid: Int, toUid: Int) {
        if (fromUid == toUid) return
        edit { items ->
            val from = items.indexOfFirst { it.uid == fromUid }
            val to = items.indexOfFirst { it.uid == toUid }
            if (from < 0 || to < 0) return@edit items
            items.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(_state.value.items)
        _state.update { it.copy(items = previous, canUndo = undo.isNotEmpty(), canRedo = true, changes = (it.changes - 1).coerceAtLeast(0), selected = emptySet()) }
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(_state.value.items)
        _state.update { it.copy(items = next, canUndo = true, canRedo = redo.isNotEmpty(), changes = it.changes + 1, selected = emptySet()) }
    }

    // ---- Save ----

    fun suggestedName(opLabel: String): String = OutputNames.build(_state.value.outputPattern, _state.value.displayName, opLabel)

    /** Plan for "Extract to new file": only the selected pages, in their current order. */
    fun extractPlan(): PagePlan {
        val s = _state.value
        return PagePlan(s.items.filter { it.uid in s.selected && !it.deleted }.map { PageOp(it.sourceIndex, it.rotation) })
    }

    fun save(plan: PagePlan, destination: Uri) {
        if (plan.isEmpty) return
        launcher.launch(
            op = DocOperation.Organise(uri, plan),
            destination = destination,
            passwords = passwords(),
            inputPages = _state.value.pageCount,
            summary = _state.value.displayName,
        )
    }

    override fun onCleared() {
        handle?.close()
        thumbnails.clear()
        super.onCleared()
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
