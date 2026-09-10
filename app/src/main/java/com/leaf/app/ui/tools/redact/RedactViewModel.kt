package com.leaf.app.ui.tools.redact

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
import com.leaf.app.data.pdf.write.RedactionBox
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.ui.reader.PageThumbnailCache
import com.leaf.app.util.saf.OutputNames
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A box being dragged out, as fractions of the displayed page. */
data class DraftBox(val startX: Float, val startY: Float, val endX: Float, val endY: Float)

data class RedactUiState(
    val uri: Uri,
    val displayName: String = "",
    val pageCount: Int = 0,
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val needsPassword: Boolean = false,
    val passwordWrong: Boolean = false,
    val page: Int = 0,
    val boxes: List<RedactionBox> = emptyList(),
    val draft: DraftBox? = null,
    val canUndo: Boolean = false,
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
) {
    val boxesOnPage: List<RedactionBox> get() = boxes.filter { it.pageIndex == page }
    val pagesWithBoxes: Int get() = boxes.distinctBy { it.pageIndex }.size
    val ready: Boolean get() = boxes.isNotEmpty() && !loading && !needsPassword && !loadError
}

class RedactViewModel(
    uriString: String,
    private val documents: DocumentRepository,
    private val engine: PdfEngine,
    private val saf: SafAccess,
    private val settings: SettingsRepository,
    val launcher: OperationLauncher,
    private val fallbackName: String,
) : ViewModel() {

    private val uri = Uri.parse(uriString)
    private val _state = MutableStateFlow(RedactUiState(uri = uri))
    val state: StateFlow<RedactUiState> = _state

    private var handle: PdfHandle? = null
    private var password: String? = null
    private val thumbnails = PageThumbnailCache(16 * 1024 * 1024)
    private val undo = ArrayDeque<List<RedactionBox>>()

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            val doc = documents.findByUri(uriString)
            val info = saf.queryInfo(uri)
            _state.update {
                it.copy(
                    displayName = doc?.title ?: info?.displayName ?: fallbackName,
                    page = doc?.lastPage ?: 0,
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
        _state.update { it.copy(loading = false, needsPassword = false, pageCount = h.pageCount, page = it.page.coerceIn(0, (h.pageCount - 1).coerceAtLeast(0))) }
    }

    fun submitPassword(pw: String) = viewModelScope.launch { open(pw) }

    suspend fun thumbnail(page: Int, widthPx: Int): Bitmap? {
        val h = handle ?: return null
        return thumbnails.get(page, widthPx) {
            runCatching { h.renderPage(page, widthPx) }
                .onFailure { android.util.Log.w("Redact", "render page $page at $widthPx failed", it) }
                .getOrNull()
        }
    }

    fun selectPage(page: Int) = _state.update { it.copy(page = page.coerceIn(0, (it.pageCount - 1).coerceAtLeast(0)), draft = null) }

    // ---- Boxes ----

    fun draftStart(x: Float, y: Float) = _state.update { it.copy(draft = DraftBox(x.clamp(), y.clamp(), x.clamp(), y.clamp())) }

    fun draftMove(x: Float, y: Float) = _state.update { s -> s.copy(draft = s.draft?.copy(endX = x.clamp(), endY = y.clamp())) }

    fun draftCancel() = _state.update { it.copy(draft = null) }

    /** Turns the drag into a box unless it was too small to mean anything. */
    fun draftCommit() = _state.update { s ->
        val d = s.draft ?: return@update s
        if (abs(d.endX - d.startX) < MIN_SIZE || abs(d.endY - d.startY) < MIN_SIZE) return@update s.copy(draft = null)
        undo.addLast(s.boxes)
        val box = RedactionBox(s.page, min(d.startX, d.endX), min(d.startY, d.endY), max(d.startX, d.endX), max(d.startY, d.endY))
        s.copy(boxes = s.boxes + box, draft = null, canUndo = true)
    }

    /** Removes the box under a tap, the most recent first when they overlap. */
    fun removeAt(x: Float, y: Float) = _state.update { s ->
        val hit = s.boxes.lastOrNull { it.pageIndex == s.page && x in it.left..it.right && y in it.top..it.bottom } ?: return@update s
        undo.addLast(s.boxes)
        s.copy(boxes = s.boxes - hit, canUndo = true)
    }

    fun undo() = _state.update { s ->
        val previous = undo.removeLastOrNull() ?: return@update s
        s.copy(boxes = previous, draft = null, canUndo = undo.isNotEmpty())
    }

    // ---- Save ----

    fun suggestedName(): String = OutputNames.build(_state.value.outputPattern, _state.value.displayName, "redacted")

    fun save(destination: Uri) {
        val s = _state.value
        if (!s.ready) return
        launcher.launch(
            op = DocOperation.Redact(uri, s.boxes),
            destination = destination,
            passwords = password?.let { mapOf(uri to it) } ?: emptyMap(),
            inputPages = s.pageCount,
            summary = s.displayName,
        )
    }

    override fun onCleared() {
        handle?.close()
        thumbnails.clear()
        super.onCleared()
    }

    private fun Float.clamp() = coerceIn(0f, 1f)

    companion object {
        /** A drag shorter than this in either direction is a tap, not a box. */
        const val MIN_SIZE = 0.01f
    }
}
