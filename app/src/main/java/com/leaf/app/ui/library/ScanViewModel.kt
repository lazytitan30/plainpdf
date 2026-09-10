package com.leaf.app.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.docs.DocumentRepository
import com.leaf.app.data.ops.OperationsRepository
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.Fit
import com.leaf.app.data.pdf.write.ImageInput
import com.leaf.app.data.pdf.write.Margin
import com.leaf.app.data.pdf.write.OperationErrors
import com.leaf.app.data.pdf.write.OperationOutput
import com.leaf.app.data.pdf.write.OperationProgress
import com.leaf.app.data.pdf.write.PageSize
import com.leaf.app.data.pdf.write.PdfBoxOperationRunner
import com.leaf.app.R
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.util.ocr.LanguagePackSource
import com.leaf.app.util.ocr.OcrLanguage
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.ocr.OcrQualityCheck
import com.leaf.app.util.ocr.PackState
import com.leaf.app.util.ocr.PackSuggester
import com.leaf.app.util.ocr.TessdataStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One scanned page: the camera capture and, once adjusted, the cropped and cleaned version. */
data class ScanPage(val original: Uri, val processed: Uri? = null) {
    val effective: Uri get() = processed ?: original
}

data class ScanUiState(
    val pages: List<ScanPage> = emptyList(),
    /** A capture waiting for the crop step; the sheet stays hidden until it is answered. */
    val pendingCrop: Uri? = null,
    /** Index of the page being re-adjusted, or -1 for a new capture. */
    val cropIndex: Int = -1,
    val building: Boolean = false,
    /** A localised label for the current build step, when it is worth naming. */
    val progressMessage: String? = null,
    val output: OperationOutput? = null,
    val error: String? = null,
    /** Tesseract codes the current build recognises with; empty when recognition is off. */
    val ocrLanguages: List<String> = emptyList(),
    val copyingText: Boolean = false,
    /** Set right after [ScanViewModel.copyText] succeeds; the sheet clears it once shown. */
    val textCopied: Boolean = false,
    /** A pack for the phone's language that is not on the phone yet; null once answered. */
    val packSuggestion: OcrLanguage? = null,
    /** The last build's recognised text looked like rubbish. */
    val garbledHint: Boolean = false,
) {
    val active: Boolean get() = pages.isNotEmpty() || pendingCrop != null
    val searchable: Boolean get() = ocrLanguages.isNotEmpty()
}

/**
 * "Take a photo, done, share." Every capture goes through the crop step, then the PDF is
 * rebuilt in the app's own files so the result is shareable at once.
 */
class ScanViewModel(
    private val context: Context,
    private val runner: PdfBoxOperationRunner,
    private val operations: OperationsRepository,
    private val documents: DocumentRepository,
    private val settings: SettingsRepository,
    private val tessdata: TessdataStore,
    private val packs: LanguagePackSource,
) : ViewModel() {

    private val suggester = PackSuggester(tessdata, settings, packs)

    private fun refreshSuggestion() = viewModelScope.launch {
        _state.update { it.copy(packSuggestion = suggester.current()) }
    }

    fun getSuggestedLanguage() {
        val code = _state.value.packSuggestion?.code ?: return
        suggester.download(code)
    }

    fun dismissSuggestion() {
        val code = _state.value.packSuggestion?.code ?: return
        viewModelScope.launch {
            suggester.dismiss(code)
            _state.update { it.copy(packSuggestion = null) }
        }
    }

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state

    init {
        // When the suggested pack lands, the card goes away and an existing scan is read again.
        viewModelScope.launch {
            packs.states.collect { states ->
                val code = _state.value.packSuggestion?.code ?: return@collect
                if (states[code] is PackState.Installed) {
                    _state.update { it.copy(packSuggestion = null) }
                    if (_state.value.pages.isNotEmpty()) rebuild()
                }
            }
        }
    }

    private var target: File? = null
    private var buildJob: Job? = null

    /** Photos handed over by the share sheet, waiting for their turn in the crop step. */
    private val sharedQueue = ArrayDeque<Uri>()

    /** A fresh capture: open the crop step before anything is built. */
    fun onPhotoTaken(photo: Uri) {
        _state.update { it.copy(pendingCrop = photo, cropIndex = -1, error = null) }
        refreshSuggestion()
    }

    /**
     * Photos shared from another app. Each goes through the crop step in turn, exactly like a
     * capture, so it gets edge detection, cleanup and recognition; the PDF is built once the
     * last one is answered.
     */
    fun onPhotosShared(photos: List<Uri>) {
        if (photos.isEmpty()) return
        refreshSuggestion()
        sharedQueue.addAll(photos)
        if (_state.value.pendingCrop == null) nextShared()
    }

    /** Moves the next shared photo into the crop step; false when the queue is empty. */
    private fun nextShared(): Boolean {
        val next = sharedQueue.removeFirstOrNull() ?: return false
        _state.update { it.copy(pendingCrop = next, cropIndex = -1, error = null) }
        return true
    }

    /** Re-adjust an existing page from its original capture. */
    fun adjust(index: Int) = _state.update { s -> s.pages.getOrNull(index)?.let { s.copy(pendingCrop = it.original, cropIndex = index) } ?: s }

    fun cropOutputFile(): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "scan_${System.currentTimeMillis()}_clean.jpg")
    }

    fun onCropDone(file: File) {
        val processed = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        _state.update { s ->
            val original = s.pendingCrop ?: return@update s
            val pages = if (s.cropIndex in s.pages.indices) {
                s.pages.mapIndexed { i, p -> if (i == s.cropIndex) p.copy(processed = processed) else p }
            } else {
                s.pages + ScanPage(original, processed)
            }
            s.copy(pages = pages, pendingCrop = null, cropIndex = -1)
        }
        if (!nextShared()) rebuild()
    }

    /** Crop cancelled: a new capture is kept as-is, an adjustment is abandoned. */
    fun onCropCancelled() {
        _state.update { s ->
            val original = s.pendingCrop ?: return@update s
            if (s.cropIndex in s.pages.indices) s.copy(pendingCrop = null, cropIndex = -1)
            else s.copy(pages = s.pages + ScanPage(original), pendingCrop = null, cropIndex = -1)
        }
        if (nextShared()) return
        if (_state.value.pages.isNotEmpty()) rebuild()
    }

    fun removePage(index: Int) {
        _state.value.pages.getOrNull(index)?.let { discard(it) }
        _state.update { s -> s.copy(pages = s.pages.filterIndexed { i, _ -> i != index }, error = null) }
        if (_state.value.pages.isEmpty()) reset() else rebuild()
    }

    /** Ends the scan. The PDF stays; the photos it was built from are removed. */
    fun reset() {
        buildJob?.cancel()
        target = null
        sharedQueue.clear()
        val old = _state.value
        _state.value = ScanUiState()
        old.pages.forEach { discard(it) }
        old.pendingCrop?.let { if (old.cropIndex !in old.pages.indices) discardUri(it) }
    }

    private fun discard(page: ScanPage) {
        discardUri(page.original)
        page.processed?.let { discardUri(it) }
    }

    /** Only our own captures and crops are removed; a photo shared from the gallery is the user's. */
    private fun discardUri(uri: Uri) {
        if (uri.authority != "${context.packageName}.files") return
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun rebuild() {
        buildJob?.cancel()
        val pages = _state.value.pages
        if (pages.isEmpty()) return
        val file = target ?: newTarget().also { target = it }
        val destination = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        _state.update { it.copy(building = true, progressMessage = null, output = null, error = null, textCopied = false, garbledHint = false) }
        buildJob = viewModelScope.launch {
            val prefs = settings.settings.first()
            val languages = if (prefs.scanOcr) OcrLanguages.split(prefs.ocrLanguages).ifEmpty { listOf("eng") } else emptyList()
            _state.update { it.copy(ocrLanguages = languages) }
            val op = DocOperation.ImagesToPdf(pages.map { ImageInput(it.effective) }, PageSize.FIT_TO_IMAGE, Fit.FIT, Margin.NONE, languages)
            runner.run(op, destination)
                .onEach { progress ->
                    when (progress) {
                        is OperationProgress.Done -> {
                            val out = progress.outputs.first()
                            documents.openedNow(destination.toString(), out.displayName)
                            operations.recordSuccess("SCAN", "${pages.size} photos", progress.outputs)
                            _state.update { it.copy(building = false, progressMessage = null, output = out) }
                            if (languages.isNotEmpty()) {
                                viewModelScope.launch {
                                    val garbled = OcrQualityCheck.looksGarbled(context, file)
                                    _state.update { it.copy(garbledHint = garbled) }
                                }
                            }
                        }
                        is OperationProgress.Failed -> _state.update {
                            it.copy(building = false, progressMessage = null, error = OperationErrors.describe(context, progress.reason))
                        }
                        is OperationProgress.Working -> {
                            val message = progress.recognising?.let { context.getString(R.string.scan_recognising, it.page, it.total) }
                            _state.update { it.copy(progressMessage = message) }
                        }
                    }
                }
                .collect()
        }
    }

    /** Puts the recognised text of the finished PDF on the clipboard. */
    fun copyText() {
        val file = target ?: return
        if (_state.value.copyingText || _state.value.output == null) return
        _state.update { it.copy(copyingText = true, textCopied = false) }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { PDDocument.load(file).use { doc -> PDFTextStripper().getText(doc) } }.getOrNull()
            }
            if (text == null) {
                _state.update { it.copy(copyingText = false, error = context.getString(R.string.ops_error_unknown)) }
                return@launch
            }
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Scan", text.trim()))
            _state.update { it.copy(copyingText = false, textCopied = clipboard != null) }
        }
    }

    fun textCopiedShown() = _state.update { it.copy(textCopied = false) }

    private fun newTarget(): File {
        val dir = File(context.filesDir, "scans").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date())
        return File(dir, "Scan $stamp.pdf")
    }
}
