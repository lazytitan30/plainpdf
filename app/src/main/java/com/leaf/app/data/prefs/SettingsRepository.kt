package com.leaf.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * All user preferences. Backed by DataStore so it is safe to read from any thread and
 * survives process death. Takes the [DataStore] rather than a Context so unit tests can
 * point it at a temp file.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<Settings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }

    suspend fun setTheme(value: AppTheme) = edit { it[Keys.THEME] = value.name }
    suspend fun setAccent(value: Accent) = edit { it[Keys.ACCENT] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setAppIcon(value: AppIcon) = edit { it[Keys.APP_ICON] = value.name }
    suspend fun setHighlightSet(value: HighlightSet) = edit { it[Keys.HIGHLIGHT_SET] = value.name }
    suspend fun setReadingMode(value: ReadingMode) = edit { it[Keys.READING_MODE] = value.name }
    suspend fun setPageDisplayMode(value: PageDisplayMode) = edit { it[Keys.PAGE_DISPLAY] = value.name }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setEdgeBrightness(value: Boolean) = edit { it[Keys.EDGE_BRIGHTNESS] = value }
    suspend fun setDoubleTapZoom(value: Boolean) = edit { it[Keys.DOUBLE_TAP_ZOOM] = value }
    suspend fun setVolumeKeysTurnPages(value: Boolean) = edit { it[Keys.VOLUME_KEYS] = value }
    suspend fun setDefaultSaveTreeUri(value: String?) = edit {
        if (value == null) it.remove(Keys.SAVE_TREE) else it[Keys.SAVE_TREE] = value
    }
    suspend fun setOutputNamePattern(value: String) = edit {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) it.remove(Keys.OUTPUT_PATTERN) else it[Keys.OUTPUT_PATTERN] = trimmed
    }
    suspend fun setConfirmOverwrite(value: Boolean) = edit { it[Keys.CONFIRM_OVERWRITE] = value }
    suspend fun setLibrarySort(value: LibrarySort) = edit { it[Keys.LIBRARY_SORT] = value.name }
    suspend fun setLibraryLayout(value: LibraryLayout) = edit { it[Keys.LIBRARY_LAYOUT] = value.name }
    suspend fun setSupporterUnlockOwned(value: Boolean) = edit { it[Keys.SUPPORTER_UNLOCK_OWNED] = value }
    suspend fun setSupporterTipped(value: Boolean) = edit { it[Keys.SUPPORTER_TIPPED] = value }
    suspend fun setScanOcr(value: Boolean) = edit { it[Keys.SCAN_OCR] = value }
    suspend fun setOcrPackDismissed(value: String) = edit { it[Keys.OCR_PACK_DISMISSED] = value }
    suspend fun setTourSeen(value: Boolean) = edit { it[Keys.TOUR_SEEN] = value }

    /** Blank restores the device-language default. */
    suspend fun setOcrLanguages(value: String) = edit {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) it.remove(Keys.OCR_LANGUAGES) else it[Keys.OCR_LANGUAGES] = trimmed
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun Preferences.toSettings(): Settings = Settings(
        theme = enumOr(Keys.THEME, AppTheme.SYSTEM),
        accent = enumOr(Keys.ACCENT, Accent.TEAL),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        appIcon = enumOr(Keys.APP_ICON, AppIcon.DEFAULT),
        highlightSet = enumOr(Keys.HIGHLIGHT_SET, HighlightSet.ACCENT),
        readingMode = enumOr(Keys.READING_MODE, ReadingMode.CONTINUOUS),
        pageDisplayMode = enumOr(Keys.PAGE_DISPLAY, PageDisplayMode.NORMAL),
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: false,
        edgeBrightness = this[Keys.EDGE_BRIGHTNESS] ?: false,
        doubleTapZoom = this[Keys.DOUBLE_TAP_ZOOM] ?: true,
        volumeKeysTurnPages = this[Keys.VOLUME_KEYS] ?: false,
        defaultSaveTreeUri = this[Keys.SAVE_TREE],
        outputNamePattern = this[Keys.OUTPUT_PATTERN] ?: Settings.DEFAULT_OUTPUT_PATTERN,
        confirmOverwrite = this[Keys.CONFIRM_OVERWRITE] ?: true,
        librarySort = enumOr(Keys.LIBRARY_SORT, LibrarySort.RECENTLY_OPENED),
        libraryLayout = enumOr(Keys.LIBRARY_LAYOUT, LibraryLayout.LIST),
        // Test builds before version code 3 kept a single flag; it counts as the pack until Play rewrites it.
        supporterUnlockOwned = this[Keys.SUPPORTER_UNLOCK_OWNED] ?: this[Keys.IS_SUPPORTER] ?: false,
        supporterTipped = this[Keys.SUPPORTER_TIPPED] ?: false,
        scanOcr = this[Keys.SCAN_OCR] ?: true,
        tourSeen = this[Keys.TOUR_SEEN] ?: false,
        ocrLanguages = this[Keys.OCR_LANGUAGES] ?: Settings.defaultOcrLanguages(),
        ocrPackDismissed = this[Keys.OCR_PACK_DISMISSED] ?: "",
    )

    private inline fun <reified T : Enum<T>> Preferences.enumOr(key: Preferences.Key<String>, default: T): T {
        val raw = this[key] ?: return default
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val OCR_PACK_DISMISSED = stringPreferencesKey("ocr_pack_dismissed")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val APP_ICON = stringPreferencesKey("app_icon")
        val HIGHLIGHT_SET = stringPreferencesKey("highlight_set")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val PAGE_DISPLAY = stringPreferencesKey("page_display_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val EDGE_BRIGHTNESS = booleanPreferencesKey("edge_brightness")
        val DOUBLE_TAP_ZOOM = booleanPreferencesKey("double_tap_zoom")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys_turn_pages")
        val SAVE_TREE = stringPreferencesKey("default_save_tree_uri")
        val OUTPUT_PATTERN = stringPreferencesKey("output_name_pattern")
        val CONFIRM_OVERWRITE = booleanPreferencesKey("confirm_overwrite")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
        val IS_SUPPORTER = booleanPreferencesKey("is_supporter")
        val SUPPORTER_UNLOCK_OWNED = booleanPreferencesKey("supporter_unlock_owned")
        val SUPPORTER_TIPPED = booleanPreferencesKey("supporter_tipped")
        val SCAN_OCR = booleanPreferencesKey("scan_ocr")
        val TOUR_SEEN = booleanPreferencesKey("tour_seen")
        val OCR_LANGUAGES = stringPreferencesKey("ocr_languages")
    }
}
