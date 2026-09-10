package com.leaf.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages the app ships, for the in-app picker. Android already follows the phone
 * language on its own; this lets someone choose differently for Leaf alone, which older
 * users find easier than the per-app language screen buried in system settings.
 */
object AppLanguages {

    /** Empty tag means "follow the system". */
    const val SYSTEM = ""

    /** BCP 47 tag to the language's own name, in the order shown. */
    val supported: List<Pair<String, String>> = listOf(
        "en" to "English",
        "sr-Latn" to "Srpski",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "pl" to "Polski",
        "tr" to "Türkçe",
        "id" to "Bahasa Indonesia",
        "vi" to "Tiếng Việt",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh-CN" to "简体中文",
        "hi" to "हिन्दी",
        "ar" to "العربية",
    )

    /** The tag the user chose, or [SYSTEM]. Matches on language plus script or region when set. */
    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return SYSTEM
        val chosen = locales[0] ?: return SYSTEM
        return supported.map { it.first }.firstOrNull { tag ->
            val t = LocaleListCompat.forLanguageTags(tag)[0] ?: return@firstOrNull false
            t.language == chosen.language &&
                (t.script.isEmpty() || t.script == chosen.script) &&
                (t.country.isEmpty() || t.country == chosen.country)
        } ?: SYSTEM
    }

    fun nameOf(tag: String): String? = supported.firstOrNull { it.first == tag }?.second

    /** Applies immediately; AppCompat recreates the activity and stores the choice. */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
