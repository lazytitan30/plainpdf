package com.leaf.app.util.ocr

import com.leaf.app.data.prefs.SettingsRepository
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * Decides whether to offer a language pack before recognition runs: the phone's language
 * has a pack, it is not on this phone, recognition is switched on, and the person has not
 * already said "Not now" for that language.
 */
class PackSuggester(
    private val tessdata: TessdataStore,
    private val settings: SettingsRepository,
    private val packs: LanguagePackSource,
) {
    suspend fun current(locale: Locale = Locale.getDefault()): OcrLanguage? {
        if (!packs.canDownload) return null
        val prefs = settings.settings.first()
        if (!prefs.scanOcr) return null
        val language = OcrLanguages.downloadableFor(locale) ?: return null
        if (tessdata.isInstalled(language.code)) return null
        if (language.code in OcrLanguages.split(prefs.ocrPackDismissed)) return null
        return language
    }

    suspend fun dismiss(code: String) {
        val dismissed = OcrLanguages.split(settings.settings.first().ocrPackDismissed)
        if (code !in dismissed) settings.setOcrPackDismissed(OcrLanguages.join(dismissed + code))
    }

    fun download(code: String) = packs.download(code)
}
