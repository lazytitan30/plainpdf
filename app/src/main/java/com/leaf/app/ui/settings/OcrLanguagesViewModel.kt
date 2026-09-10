package com.leaf.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.util.ocr.LanguagePackSource
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.ocr.PackState
import com.leaf.app.util.ocr.TessdataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-off notices the screen shows once and clears. */
sealed interface OcrLanguagesNotice {
    data class Ready(val code: String) : OcrLanguagesNotice
    data class ImportFailed(val message: String) : OcrLanguagesNotice
    data object KeepOne : OcrLanguagesNotice
}

/**
 * The "Languages for scanned text" screen: which packs are on the phone, which are ticked
 * for recognition, and the state of every downloadable pack.
 */
class OcrLanguagesViewModel(
    private val settings: SettingsRepository,
    private val tessdata: TessdataStore,
    private val packs: LanguagePackSource,
    private val resolver: ContentResolver,
) : ViewModel() {

    val canDownload: Boolean = packs.canDownload

    val chosen: StateFlow<Set<String>> = settings.settings
        .map { OcrLanguages.split(it.ocrLanguages).toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _installed = MutableStateFlow(tessdata.installed())
    val installed: StateFlow<List<String>> = _installed

    val packStates: StateFlow<Map<String, PackState>> = packs.states

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _notice = MutableStateFlow<OcrLanguagesNotice?>(null)
    val notice: StateFlow<OcrLanguagesNotice?> = _notice

    init {
        // A pack that just finished downloading shows up under "On this phone" at once.
        viewModelScope.launch {
            var previous = emptySet<String>()
            packs.states.collect { states ->
                val ready = states.filterValues { it is PackState.Installed }.keys
                val fresh = ready - previous
                if (fresh.isNotEmpty() && previous.isNotEmpty()) fresh.firstOrNull()?.let { _notice.value = OcrLanguagesNotice.Ready(it) }
                if (ready != previous) _installed.value = withContext(Dispatchers.IO) { tessdata.installed() }
                previous = ready
            }
        }
    }

    fun setChosen(code: String, on: Boolean) {
        viewModelScope.launch {
            val current = OcrLanguages.split(settings.settings.first().ocrLanguages)
            val next = if (on) current + code else current - code
            if (next.isEmpty()) {
                _notice.value = OcrLanguagesNotice.KeepOne
                return@launch
            }
            settings.setOcrLanguages(OcrLanguages.join(next))
        }
    }

    fun download(code: String) = packs.download(code)

    fun cancel(code: String) = packs.cancel(code)

    fun remove(code: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { tessdata.delete(code) }
            val current = OcrLanguages.split(settings.settings.first().ocrLanguages)
            if (code in current) {
                val next = (current - code).ifEmpty { listOf("eng") }
                settings.setOcrLanguages(OcrLanguages.join(next))
            }
            refresh()
        }
    }

    fun import(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { tessdata.import(uri, resolver) }
            result.onSuccess { code ->
                val current = OcrLanguages.split(settings.settings.first().ocrLanguages)
                if (code !in current) settings.setOcrLanguages(OcrLanguages.join(current + code))
                _notice.value = OcrLanguagesNotice.Ready(code)
            }.onFailure { e ->
                _notice.value = OcrLanguagesNotice.ImportFailed(e.message ?: "Import failed")
            }
            refresh()
            _busy.value = false
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun refresh() {
        _installed.value = tessdata.installed()
        packs.refresh()
    }
}
