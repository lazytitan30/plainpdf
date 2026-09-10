package com.leaf.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.billing.SupportEntry
import com.leaf.app.data.billing.SupporterRepository
import com.leaf.app.data.billing.SupporterStanding
import com.leaf.app.data.prefs.Accent
import com.leaf.app.data.prefs.AppIcon
import com.leaf.app.data.prefs.AppTheme
import com.leaf.app.data.prefs.HighlightSet
import com.leaf.app.data.prefs.LibrarySort
import com.leaf.app.data.prefs.PageDisplayMode
import com.leaf.app.data.prefs.ReadingMode
import com.leaf.app.data.prefs.Settings
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.ocr.TessdataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val settings: Settings,
    val isSupporter: Boolean,
    val standing: SupporterStanding,
    val supportEntry: SupportEntry,
)

/** What the language import ended with; shown once and then cleared. */
sealed interface OcrImportResult {
    data class Imported(val code: String) : OcrImportResult
    data class Failed(val message: String) : OcrImportResult
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val supporter: SupporterRepository,
    private val tessdata: TessdataStore,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState?> = combine(settings.settings, supporter.isSupporter, supporter.standing) { s, sup, standing ->
        SettingsUiState(settings = s, isSupporter = sup, standing = standing, supportEntry = supporter.supportEntry)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _installedOcrLanguages = MutableStateFlow(tessdata.installed())
    /** Tesseract codes with a pack on the device, bundled first. */
    val installedOcrLanguages: StateFlow<List<String>> = _installedOcrLanguages

    private val _ocrImport = MutableStateFlow<OcrImportResult?>(null)
    val ocrImport: StateFlow<OcrImportResult?> = _ocrImport

    private val _ocrImporting = MutableStateFlow(false)
    val ocrImporting: StateFlow<Boolean> = _ocrImporting

    fun setScanOcr(value: Boolean) = launch { settings.setScanOcr(value) }
    fun showTourAgain() = launch { settings.setTourSeen(false) }

    /** An empty pick falls back to the device default rather than turning recognition off. */
    fun setOcrLanguages(codes: Collection<String>) = launch {
        val installed = _installedOcrLanguages.value
        val kept = codes.filter { it in installed }
        settings.setOcrLanguages(if (kept.isEmpty()) "" else OcrLanguages.join(kept))
    }

    fun importOcrLanguage(uri: Uri, resolver: ContentResolver) {
        if (_ocrImporting.value) return
        _ocrImporting.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { tessdata.import(uri, resolver) }
            _installedOcrLanguages.value = tessdata.installed()
            result.onSuccess { code ->
                // A freshly imported language is what the user wants to read next.
                val current = OcrLanguages.split(settings.settings.first().ocrLanguages)
                settings.setOcrLanguages(OcrLanguages.join(current + code))
                _ocrImport.value = OcrImportResult.Imported(code)
            }.onFailure { e ->
                _ocrImport.value = OcrImportResult.Failed(e.message ?: e.javaClass.simpleName)
            }
            _ocrImporting.value = false
        }
    }

    fun deleteOcrLanguage(code: String) = launch {
        withContext(Dispatchers.IO) { tessdata.delete(code) }
        _installedOcrLanguages.value = tessdata.installed()
        val current = OcrLanguages.split(settings.settings.first().ocrLanguages)
        if (code in current) setOcrLanguages(current - code)
    }

    fun clearOcrImportResult() = _ocrImport.update { null }

    fun setTheme(value: AppTheme) = launch { settings.setTheme(value) }
    fun setAccent(value: Accent) = launch { settings.setAccent(value) }
    fun setDynamicColor(value: Boolean) = launch { settings.setDynamicColor(value) }
    fun setAppIcon(value: AppIcon) = launch { settings.setAppIcon(value) }
    fun setHighlightSet(value: HighlightSet) = launch { settings.setHighlightSet(value) }
    fun setReadingMode(value: ReadingMode) = launch { settings.setReadingMode(value) }
    fun setPageDisplayMode(value: PageDisplayMode) = launch { settings.setPageDisplayMode(value) }
    fun setKeepScreenOn(value: Boolean) = launch { settings.setKeepScreenOn(value) }
    fun setEdgeBrightness(value: Boolean) = launch { settings.setEdgeBrightness(value) }
    fun setDoubleTapZoom(value: Boolean) = launch { settings.setDoubleTapZoom(value) }
    fun setVolumeKeysTurnPages(value: Boolean) = launch { settings.setVolumeKeysTurnPages(value) }
    fun setDefaultSaveTreeUri(value: String?) = launch { settings.setDefaultSaveTreeUri(value) }
    fun setOutputNamePattern(value: String) = launch { settings.setOutputNamePattern(value) }
    fun setConfirmOverwrite(value: Boolean) = launch { settings.setConfirmOverwrite(value) }
    fun setLibrarySort(value: LibrarySort) = launch { settings.setLibrarySort(value) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
