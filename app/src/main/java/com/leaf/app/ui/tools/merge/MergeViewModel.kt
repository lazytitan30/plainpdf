package com.leaf.app.ui.tools.merge

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.pdf.read.PdfEngine
import com.leaf.app.data.pdf.read.PdfOpenException
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.MergeSource
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.util.PageRanges
import com.leaf.app.util.saf.OutputNames
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the password prompt derivation needs from an item; kept small so it is testable without [Uri]. */
interface PasswordProbe {
    val encrypted: Boolean
    val pageCount: Int?
    val password: String?
    val passwordWrong: Boolean
}

/**
 * The item to ask a password for: encrypted, not yet readable, and without an accepted
 * password. Derived from the list rather than stored, so concurrent probes cannot
 * overwrite each other and the next encrypted item is prompted as soon as one is done.
 */
fun <T : PasswordProbe> List<T>.firstAwaitingPassword(): T? =
    firstOrNull { it.encrypted && it.pageCount == null && it.password == null }

data class MergeItem(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    /** null while probing, -1 when unreadable. */
    override val pageCount: Int? = null,
    override val encrypted: Boolean = false,
    override val password: String? = null,
    /** The last password tried for this item was rejected. */
    override val passwordWrong: Boolean = false,
    /** User text like "1-4, 8"; blank means every page. */
    val rangeText: String = "",
    val expanded: Boolean = false,
) : PasswordProbe {
    val parsed: PageRanges.Result? get() = if (rangeText.isBlank() || pageCount == null || pageCount < 0) null else PageRanges.parse(rangeText, pageCount)
    val selectedPages: Int
        get() {
            val count = pageCount ?: return 0
            if (count < 0) return 0
            val p = parsed ?: return count
            return (p as? PageRanges.Result.Ok)?.let { PageRanges.pageCountOf(it.ranges).sum() } ?: 0
        }
    val rangeError: PageRanges.Result.Error? get() = parsed as? PageRanges.Result.Error
}

data class MergeUiState(
    val items: List<MergeItem> = emptyList(),
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
) {
    /** Item waiting for a password before the merge can start. */
    val passwordFor: Uri? get() = items.firstAwaitingPassword()?.uri
    val passwordWrong: Boolean get() = items.firstAwaitingPassword()?.passwordWrong ?: false
    val totalPages: Int get() = items.sumOf { it.selectedPages }
    val ready: Boolean get() = items.size >= 2 && items.all { it.pageCount != null && it.pageCount >= 0 && it.rangeError == null } && totalPages > 0
}

class MergeViewModel(
    private val engine: PdfEngine,
    private val saf: SafAccess,
    settings: SettingsRepository,
    val launcher: OperationLauncher,
    private val fallbackName: String,
) : ViewModel() {

    private val _state = MutableStateFlow(MergeUiState())
    val state: StateFlow<MergeUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(outputPattern = settings.settings.first().outputNamePattern) } }
    }

    fun add(uris: List<Uri>) {
        val existing = _state.value.items.map { it.uri }.toSet()
        val fresh = uris.filter { it !in existing }.map { uri ->
            val info = saf.queryInfo(uri)
            MergeItem(uri, info?.displayName ?: fallbackName, info?.sizeBytes)
        }
        if (fresh.isEmpty()) return
        _state.update { it.copy(items = it.items + fresh) }
        fresh.forEach { probe(it.uri, null) }
    }

    private fun probe(uri: Uri, password: String?) = viewModelScope.launch {
        val result = engine.open(uri, password)
        val handle = result.getOrNull()
        if (handle != null) {
            val count = handle.pageCount
            handle.close()
            update(uri) { it.copy(pageCount = count, encrypted = password != null, password = password, passwordWrong = false) }
        } else {
            val kind = (result.exceptionOrNull() as? PdfOpenException)?.kind
            if (kind == PdfOpenException.Kind.PASSWORD_REQUIRED) {
                update(uri) { it.copy(encrypted = true, passwordWrong = password != null) }
            } else {
                update(uri) { it.copy(pageCount = -1) }
            }
        }
    }

    fun submitPassword(uri: Uri, password: String) = probe(uri, password)

    fun dismissPassword() = _state.update { s -> s.copy(items = s.items.filter { it.uri != s.passwordFor }) }

    fun remove(uri: Uri) = _state.update { s -> s.copy(items = s.items.filter { it.uri != uri }) }

    fun move(from: Int, to: Int) = _state.update { s ->
        if (from !in s.items.indices || to !in s.items.indices) s
        else s.copy(items = s.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun setRange(uri: Uri, text: String) = update(uri) { it.copy(rangeText = text) }

    fun toggleExpanded(uri: Uri) = update(uri) { it.copy(expanded = !it.expanded) }

    private fun update(uri: Uri, f: (MergeItem) -> MergeItem) = _state.update { s -> s.copy(items = s.items.map { if (it.uri == uri) f(it) else it }) }

    fun suggestedName(): String {
        val first = _state.value.items.firstOrNull()?.displayName ?: fallbackName
        return OutputNames.build(_state.value.outputPattern, first, "merged")
    }

    fun save(destination: Uri) {
        val s = _state.value
        if (!s.ready) return
        val sources = s.items.map { item ->
            val ranges = (item.parsed as? PageRanges.Result.Ok)?.ranges?.let { PageRanges.toZeroBased(it) }
            MergeSource(item.uri, ranges)
        }
        val passwords = s.items.mapNotNull { item -> item.password?.let { item.uri to it } }.toMap()
        launcher.launch(
            op = DocOperation.Merge(sources),
            destination = destination,
            passwords = passwords,
            inputPages = s.totalPages,
            summary = "${s.items.size} documents",
        )
    }
}
