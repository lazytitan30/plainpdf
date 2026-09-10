package com.leaf.app.ui.tools.split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.pdf.read.PdfEngine
import com.leaf.app.data.pdf.read.PdfOpenException
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.pdf.write.SplitMode
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.util.PageRanges
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SplitChoice { RANGES, EVERY_N, EXTRACT }

data class SplitUiState(
    val uri: Uri? = null,
    val displayName: String = "",
    val pageCount: Int? = null,
    val password: String? = null,
    val needsPassword: Boolean = false,
    val passwordWrong: Boolean = false,
    val unreadable: Boolean = false,
    val choice: SplitChoice = SplitChoice.RANGES,
    val rangeText: String = "",
    val everyNText: String = "1",
    val defaultTree: Uri? = null,
) {
    val parsedRanges: PageRanges.Result? get() = pageCount?.let { if (rangeText.isBlank()) null else PageRanges.parse(rangeText, it) }
    val everyN: Int? get() = everyNText.toIntOrNull()?.takeIf { it >= 1 }

    /** 1-based ranges the current configuration would produce, or null when invalid. */
    val plannedRanges: List<IntRange>?
        get() {
            val count = pageCount ?: return null
            return when (choice) {
                SplitChoice.RANGES -> (parsedRanges as? PageRanges.Result.Ok)?.ranges
                SplitChoice.EVERY_N -> everyN?.let { PageRanges.everyN(count, it) }
                SplitChoice.EXTRACT -> null
            }
        }
    val ready: Boolean get() = uri != null && !needsPassword && !unreadable && (plannedRanges?.isNotEmpty() == true)
}

class SplitViewModel(
    private val engine: PdfEngine,
    private val saf: SafAccess,
    private val settings: SettingsRepository,
    val launcher: OperationLauncher,
    private val fallbackName: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            _state.update { it.copy(defaultTree = s.defaultSaveTreeUri?.let(Uri::parse)) }
        }
    }

    fun setSource(uri: Uri) {
        val info = saf.queryInfo(uri)
        _state.update { it.copy(uri = uri, displayName = info?.displayName ?: fallbackName, pageCount = null, password = null, needsPassword = false, unreadable = false) }
        probe(uri, null)
    }

    private fun probe(uri: Uri, password: String?) = viewModelScope.launch {
        val result = engine.open(uri, password)
        val handle = result.getOrNull()
        if (handle != null) {
            val count = handle.pageCount
            handle.close()
            _state.update { it.copy(pageCount = count, password = password, needsPassword = false, passwordWrong = false) }
        } else {
            val kind = (result.exceptionOrNull() as? PdfOpenException)?.kind
            if (kind == PdfOpenException.Kind.PASSWORD_REQUIRED) _state.update { it.copy(needsPassword = true, passwordWrong = password != null) }
            else _state.update { it.copy(unreadable = true) }
        }
    }

    fun submitPassword(password: String) = _state.value.uri?.let { probe(it, password) }
    fun setChoice(choice: SplitChoice) = _state.update { it.copy(choice = choice) }
    fun setRangeText(text: String) = _state.update { it.copy(rangeText = text) }
    fun setEveryN(text: String) = _state.update { it.copy(everyNText = text.filter(Char::isDigit).take(4)) }

    fun save(tree: Uri) {
        val s = _state.value
        val uri = s.uri ?: return
        val ranges = s.plannedRanges ?: return
        val mode = when (s.choice) {
            SplitChoice.RANGES -> SplitMode.ByRanges(PageRanges.toZeroBased(ranges))
            SplitChoice.EVERY_N -> SplitMode.EveryN(s.everyN ?: return)
            SplitChoice.EXTRACT -> return
        }
        launcher.launch(
            op = DocOperation.Split(uri, mode),
            destination = tree,
            passwords = s.password?.let { mapOf(uri to it) } ?: emptyMap(),
            inputPages = s.pageCount ?: 0,
            summary = s.displayName,
        )
    }
}
