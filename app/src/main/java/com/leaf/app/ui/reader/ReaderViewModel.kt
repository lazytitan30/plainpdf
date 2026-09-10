package com.leaf.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.db.entities.BookmarkEntity
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.data.docs.BookmarkRepository
import com.leaf.app.data.docs.DocumentRepository
import com.leaf.app.data.pdf.read.SearchMatch
import com.leaf.app.data.prefs.PageDisplayMode
import com.leaf.app.data.prefs.ReadingMode
import com.leaf.app.data.prefs.Settings
import com.leaf.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.leaf.app.util.ocr.OcrLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.leaf.app.util.saf.SafAccess
import java.io.File
import java.io.IOException
import java.util.UUID

data class SearchState(
    val active: Boolean = false,
    val query: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val current: Int = -1,
    val searching: Boolean = false,
)

data class ReaderUiState(
    val uri: Uri,
    val documentId: Long? = null,
    val displayName: String = "",
    val sizeBytes: Long? = null,
    /** False until the document row is known; the viewer waits so restore has data. */
    val ready: Boolean = false,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val chromeVisible: Boolean = true,
    val loadError: Boolean = false,
    val permissionLost: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.CONTINUOUS,
    val pageDisplayMode: PageDisplayMode = PageDisplayMode.NORMAL,
    val keepScreenOn: Boolean = false,
    val edgeBrightness: Boolean = false,
    val doubleTapZoom: Boolean = true,
    val volumeKeysTurnPages: Boolean = false,
    val highlightSet: com.leaf.app.data.prefs.HighlightSet = com.leaf.app.data.prefs.HighlightSet.ACCENT,
    val search: SearchState = SearchState(),
    val hasForm: Boolean = false,
    val formFilling: Boolean = false,
    /** True when this device and document support ink and highlight annotations. */
    val hasAnnotations: Boolean = false,
    val annotating: Boolean = false,
    /** Position the paged reader should start at when it takes over from continuous mode. */
    val pagedStartPage: Int = 0,
    /** Non-null while the phone is reading the document out. */
    val readAloud: ReadAloudState? = null,
    /** A blocking step that deserves a progress dialog. */
    val busy: ReaderBusy? = null,
    /** Whole-document text waiting in the copy-or-share dialog. */
    val extractedText: String? = null,
)

enum class ReaderBusy { EXTRACTING_TEXT, PREPARING_PRINT, SHARING_PAGE }

/** One-off messages for the snackbar. */
enum class ReaderEvent { NO_VOICE, NO_TEXT, PASSWORD_REQUIRED, FAILED, PRINT_UNAVAILABLE }

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    uriString: String,
    /** Application context: PdfBox and TextToSpeech need one, and the view model outlives any activity. */
    private val context: Context,
    private val documents: DocumentRepository,
    private val bookmarksRepo: BookmarkRepository,
    private val settingsRepo: SettingsRepository,
    private val saf: SafAccess,
    private val workDir: File,
    /** Outlives this view model; position writes go here so clearing never drops one. */
    private val appScope: CoroutineScope,
) : ViewModel(), ReaderHost {

    private val uri: Uri = Uri.parse(uriString)
    private val _state = MutableStateFlow(ReaderUiState(uri = uri))
    val state: StateFlow<ReaderUiState> = _state

    val thumbnails = PageThumbnailCache()

    private val documentIdFlow = MutableStateFlow<Long?>(null)
    val bookmarks: StateFlow<List<BookmarkEntity>> = documentIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else bookmarksRepo.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set by the host composable while the fragment is attached. Never held across detach. */
    var controller: PdfController? = null
        set(value) {
            field = value
            value?.setPageDisplayMode(_state.value.pageDisplayMode)
        }

    private var document: DocumentEntity? = null
    private var settings: Settings? = null
    private var restorePending = false
    private var latest: ReadingPosition? = null
    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var highlightColor = 0x59000000.toInt()
    private var currentHighlightColor = 0x99000000.toInt()
    private var readAloudSession: ReadAloudSession? = null
    private var textJob: Job? = null

    private val _events = Channel<ReaderEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Pages exported for the share sheet; the screen opens the chooser for each. */
    private val _pageShares = Channel<SharedPage>(Channel.BUFFERED)
    val pageShares = _pageShares.receiveAsFlow()

    init {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            settings = s
            val doc = documents.openedNow(uriString)
            document = doc
            documentIdFlow.value = doc.id
            restorePending = doc.lastPage > 0 || doc.lastScrollY > 0
            val perDocMode = doc.pageDisplayMode?.let { m -> PageDisplayMode.entries.firstOrNull { it.name == m } }
            _state.update {
                it.copy(
                    documentId = doc.id,
                    displayName = doc.title,
                    sizeBytes = doc.sizeBytes,
                    pageCount = doc.pageCount ?: 0,
                    currentPage = doc.lastPage,
                    pagedStartPage = doc.lastPage,
                    permissionLost = doc.permissionLost,
                    readingMode = s.readingMode,
                    pageDisplayMode = perDocMode ?: s.pageDisplayMode,
                    keepScreenOn = s.keepScreenOn,
                    edgeBrightness = s.edgeBrightness,
                    doubleTapZoom = s.doubleTapZoom,
                    volumeKeysTurnPages = s.volumeKeysTurnPages,
                    highlightSet = s.highlightSet,
                    ready = true,
                )
            }
            controller?.setPageDisplayMode(_state.value.pageDisplayMode)
        }
    }

    // ---- ReaderHost ----

    override fun onDocumentLoaded(pageCount: Int): ReadingPosition? {
        val doc = document
        _state.update { it.copy(pageCount = pageCount, loadError = false) }
        if (doc != null && doc.pageCount != pageCount) {
            viewModelScope.launch { documents.setPageCount(doc.id, pageCount) }
        }
        val target = doc?.let { PositionRestorePolicy.targetFor(it, pageCount) }
        restorePending = target != null
        _state.update { it.copy(hasForm = controller?.hasForm == true, hasAnnotations = controller?.hasAnnotations == true) }
        return target
    }

    fun setFormFilling(on: Boolean) {
        if (on && _state.value.annotating) setAnnotating(false)
        controller?.setFormFilling(on)
        _state.update { it.copy(formFilling = on, chromeVisible = true) }
    }

    /** Writes the form edits to a temp file, verifies it exists, then commits to [destination]. */
    suspend fun saveFilledCopy(destination: Uri): Boolean =
        saveCopy("filled", destination) { c, temp -> c.writeEditedCopy(temp) }

    /** Drawing and form filling are exclusive; the library disables fields while drawing anyway. */
    fun setAnnotating(on: Boolean) {
        // Paged mode has no androidx fragment behind it; never show a drawing bar with no ink layer.
        if (on && controller?.hasAnnotations != true) return
        if (on && _state.value.formFilling) setFormFilling(false)
        controller?.setAnnotating(on)
        _state.update { it.copy(annotating = on, chromeVisible = true) }
    }

    /** Whether leaving drawing mode would throw away strokes. */
    fun hasUnsavedAnnotationEdits(): Boolean = controller?.hasAnnotationEdits == true

    /** Applies the strokes to a temp copy, verifies it exists, then commits to [destination]. */
    suspend fun saveAnnotatedCopy(destination: Uri): Boolean =
        saveCopy("annotated", destination) { c, temp -> c.writeAnnotatedCopy(temp) }

    private suspend fun saveCopy(
        prefix: String,
        destination: Uri,
        write: suspend (PdfController, File) -> Boolean,
    ): Boolean {
        val c = controller ?: return false
        val temp = File(workDir, "$prefix-${UUID.randomUUID()}.pdf")
        val ok = write(c, temp) && temp.length() > 0
        if (!ok) { temp.delete(); return false }
        return withContext(Dispatchers.IO) {
            try { saf.commit(temp, destination); true } catch (_: IOException) { false } finally { temp.delete() }
        }
    }

    override fun onViewportChanged(firstVisiblePage: Int, visiblePagesCount: Int, zoom: Float, scrollY: Int) {
        recordPosition(ReadingPosition(firstVisiblePage, zoom, scrollY))
    }

    /** Paged mode reports whole pages only. */
    fun onPagedPageChanged(page: Int) {
        restorePending = false
        recordPosition(ReadingPosition(page, 1f, 0))
    }

    private fun recordPosition(candidate: ReadingPosition) {
        if (!PositionRestorePolicy.shouldRecord(candidate, restorePending)) return
        restorePending = false
        latest = candidate
        _state.update { if (it.currentPage == candidate.page) it else it.copy(currentPage = candidate.page) }
        scheduleSave()
    }

    override fun onLoadError(error: Throwable) {
        _state.update { it.copy(loadError = true) }
        document?.let { doc -> viewModelScope.launch { documents.verifyAccess(doc.id) } }
    }

    override fun onImmersiveModeRequested(enterImmersive: Boolean) {
        // Scrolling hides chrome. The library also asks to leave immersive mode at the top of
        // the document; ignore that so chrome only returns on a deliberate tap.
        if (enterImmersive) _state.update { it.copy(chromeVisible = false) }
    }

    override fun onCentreTap() {
        _state.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    /** Whether leaving fill mode would throw away typed or ticked values. */
    fun hasUnsavedFormEdits(): Boolean = controller?.hasFormEdits == true

    fun setChromeVisible(visible: Boolean) = _state.update { it.copy(chromeVisible = visible) }

    // ---- Navigation ----

    fun goToPage(page: Int) {
        val count = _state.value.pageCount
        if (count <= 0) return
        val target = page.coerceIn(0, count - 1)
        _state.update { it.copy(currentPage = target, pagedStartPage = target) }
        controller?.scrollToPage(target)
        pagedTarget.value = target
    }

    /** Paged mode observes this to animate to a requested page. */
    val pagedTarget = MutableStateFlow<Int?>(null)

    fun turnPage(delta: Int) = goToPage(_state.value.currentPage + delta)

    // ---- Modes ----

    fun setReadingMode(mode: ReadingMode) {
        flushPosition()
        _state.update { it.copy(readingMode = mode, pagedStartPage = it.currentPage) }
    }

    fun setPageDisplayMode(mode: PageDisplayMode) {
        _state.update { it.copy(pageDisplayMode = mode) }
        controller?.setPageDisplayMode(mode)
        val doc = document ?: return
        val global = settings?.pageDisplayMode
        viewModelScope.launch { documents.setPageDisplayMode(doc.id, if (mode == global) null else mode.name) }
    }

    fun setKeepScreenOn(on: Boolean) = _state.update { it.copy(keepScreenOn = on) }

    // ---- Bookmarks ----

    fun addBookmark(label: String?) {
        val doc = document ?: return
        val page = _state.value.currentPage
        viewModelScope.launch { bookmarksRepo.add(doc.id, page, label) }
    }

    fun removeBookmark(id: Long) = viewModelScope.launch { bookmarksRepo.remove(id) }

    fun relabelBookmark(id: Long, label: String?) = viewModelScope.launch { bookmarksRepo.relabel(id, label) }

    // ---- Search ----

    fun setHighlightColors(color: Int, current: Int) {
        highlightColor = color
        currentHighlightColor = current
    }

    fun openSearch() = _state.update { it.copy(search = SearchState(active = true), chromeVisible = true) }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = SearchState()) }
        controller?.highlight(emptyList(), -1, 0, 0)
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(search = it.search.copy(query = query, searching = query.isNotBlank())) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val c = controller
            val matches = if (query.isBlank() || c == null) emptyList() else c.search(query)
            val current = if (matches.isEmpty()) -1 else nearestMatch(matches, _state.value.currentPage)
            _state.update { it.copy(search = it.search.copy(matches = matches, current = current, searching = false)) }
            applyHighlights()
            if (current >= 0) c?.scrollToMatch(matches[current])
        }
    }

    fun nextMatch() = moveMatch(+1)
    fun previousMatch() = moveMatch(-1)

    private fun moveMatch(delta: Int) {
        val s = _state.value.search
        if (s.matches.isEmpty()) return
        val next = ((s.current + delta) % s.matches.size + s.matches.size) % s.matches.size
        _state.update { it.copy(search = s.copy(current = next)) }
        applyHighlights()
        controller?.scrollToMatch(s.matches[next])
    }

    private fun applyHighlights() {
        val s = _state.value.search
        controller?.highlight(s.matches, s.current, highlightColor, currentHighlightColor)
    }

    private fun nearestMatch(matches: List<SearchMatch>, page: Int): Int {
        val idx = matches.indexOfFirst { it.pageIndex >= page }
        return if (idx < 0) 0 else idx
    }

    // ---- Thumbnails ----

    suspend fun pageThumbnail(pageIndex: Int, widthPx: Int): Bitmap? {
        val c = controller ?: return null
        return thumbnails.get(pageIndex, widthPx) { c.renderPage(pageIndex, widthPx) }
    }

    // ---- Text: read aloud, copy, print ----

    private suspend fun newTextSource(): PdfTextSource {
        val prefs = settingsRepo.settings.first()
        val languages = if (prefs.scanOcr) OcrLanguages.split(prefs.ocrLanguages) else emptyList()
        val fallback = if (languages.isEmpty()) null else OcrTextFallback(context, uri, languages)
        return PdfTextSource(context, uri, workDir, fallback)
    }

    /** Starts speaking at the current page; the reader follows along as pages finish. */
    fun startReadAloud() {
        stopReadAloud()
        viewModelScope.launch { startReadAloud(newTextSource()) }
    }

    private fun startReadAloud(source: PdfTextSource) {
        val session = ReadAloudSession(
            context = context,
            scope = viewModelScope,
            source = source,
            onPage = { goToPage(it) },
            onEvent = { event ->
                _events.trySend(
                    when (event) {
                        ReadAloudEvent.NO_VOICE -> ReaderEvent.NO_VOICE
                        ReadAloudEvent.NO_TEXT -> ReaderEvent.NO_TEXT
                        ReadAloudEvent.PASSWORD_REQUIRED -> ReaderEvent.PASSWORD_REQUIRED
                        ReadAloudEvent.FAILED -> ReaderEvent.FAILED
                    },
                )
            },
            onState = { s -> _state.update { it.copy(readAloud = s) } },
        )
        readAloudSession = session
        session.start(_state.value.currentPage)
    }

    fun pauseReadAloud() = readAloudSession?.pause()
    fun resumeReadAloud() = readAloudSession?.resume()

    fun stopReadAloud() {
        readAloudSession?.stop()
        readAloudSession = null
    }

    /** Pulls the whole text out for the copy-or-share dialog. */
    fun extractText() {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = ReaderBusy.EXTRACTING_TEXT, extractedText = null) }
        textJob = viewModelScope.launch {
            val text = withTextSource { it.allText().trim() }
            when {
                text == null -> Unit
                text.isEmpty() -> _events.send(ReaderEvent.NO_TEXT)
                else -> _state.update { it.copy(extractedText = text) }
            }
            _state.update { it.copy(busy = null) }
        }
    }

    fun cancelBusy() {
        textJob?.cancel()
        _state.update { it.copy(busy = null) }
    }

    /**
     * Exports the current page as a picture or a one-page PDF and hands it to [pageShares].
     * The viewer never shares a document password with us, so a protected file gets the
     * password message instead of a share sheet.
     */
    fun shareCurrentPage(format: PageShareExporter.Format) {
        if (_state.value.busy != null) return
        val page = _state.value.currentPage
        val name = _state.value.displayName
        _state.update { it.copy(busy = ReaderBusy.SHARING_PAGE) }
        textJob = viewModelScope.launch {
            val result = PageShareExporter(context, workDir).export(uri, page, name, format)
            _state.update { it.copy(busy = null) }
            when (result) {
                is PageShareExporter.Result.Ok -> _pageShares.send(result.page)
                PageShareExporter.Result.PasswordRequired -> _events.send(ReaderEvent.PASSWORD_REQUIRED)
                PageShareExporter.Result.Failed -> _events.send(ReaderEvent.FAILED)
            }
        }
    }

    fun dismissExtractedText() = _state.update { it.copy(extractedText = null) }

    /**
     * Runs [print] unless the document needs a user password: the viewer asked for one but
     * never shares it, and a print service would only get an unreadable file.
     */
    fun printAfterPasswordCheck(print: () -> Boolean) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = ReaderBusy.PREPARING_PRINT) }
        textJob = viewModelScope.launch {
            // Any other parse failure is PdfBox's problem, not the printer's: go ahead anyway.
            val printable = withTextSource(onFailure = { true }) { true } ?: false
            _state.update { it.copy(busy = null) }
            if (printable && !print()) _events.send(ReaderEvent.PRINT_UNAVAILABLE)
        }
    }

    /** Opens the document with PdfBox, runs [block], and closes it off the main thread. Null when it cannot be opened. */
    private suspend fun <T> withTextSource(onFailure: suspend () -> T? = { _events.send(ReaderEvent.FAILED); null }, block: suspend (PdfTextSource) -> T): T? {
        val source = newTextSource()
        return try {
            source.open()
            block(source)
        } catch (_: InvalidPasswordException) {
            _events.send(ReaderEvent.PASSWORD_REQUIRED)
            null
        } catch (_: IOException) {
            onFailure()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { source.close() }
        }
    }

    // ---- Persistence ----

    /**
     * Write back on pause, not on every scroll event. Runs on the app scope: viewModelScope
     * is already cancelled by the time [onCleared] runs, and a pause right before clearing
     * would lose the write too.
     */
    fun flushPosition() {
        saveJob?.cancel()
        val doc = document ?: return
        val pos = latest ?: return
        appScope.launch { documents.savePosition(doc.id, pos.page, pos.zoom, pos.scrollY) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val doc = document ?: return@launch
            val pos = latest ?: return@launch
            documents.savePosition(doc.id, pos.page, pos.zoom, pos.scrollY)
        }
    }

    /** "Locate again" picked a new URI for this row. */
    suspend fun relocate(newUri: String): Boolean {
        val doc = document ?: return false
        return documents.rebind(doc.id, newUri) != null
    }

    override fun onCleared() {
        flushPosition()
        stopReadAloud()
        thumbnails.clear()
        super.onCleared()
    }

    companion object {
        const val SAVE_DEBOUNCE_MS = 500L
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
