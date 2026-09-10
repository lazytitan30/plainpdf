package com.leaf.app.util.ocr

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** FOSS flavour: no Play, so no downloads. Settings shows the file import instead. */
class NoLanguagePackSource(private val tessdata: TessdataStore) : LanguagePackSource {
    private val _states = MutableStateFlow<Map<String, PackState>>(emptyMap())
    override val states: StateFlow<Map<String, PackState>> = _states
    override val canDownload: Boolean = false

    init {
        refresh()
    }

    override fun download(code: String) = Unit
    override fun cancel(code: String) = Unit

    override fun refresh() {
        _states.value = OcrLanguages.downloadable.associate { language ->
            language.code to if (tessdata.isInstalled(language.code)) PackState.Installed else PackState.NotInstalled
        }
    }
}

fun createLanguagePackSource(
    @Suppress("UNUSED_PARAMETER") context: Context,
    tessdata: TessdataStore,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    @Suppress("UNUSED_PARAMETER") onInstalled: (String) -> Unit,
): LanguagePackSource = NoLanguagePackSource(tessdata)
