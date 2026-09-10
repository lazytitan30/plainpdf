package com.leaf.app.data.prefs

import com.leaf.app.util.ocr.OcrLanguages
import java.util.Locale

enum class AppTheme { SYSTEM, LIGHT, DARK, BLACK, SEPIA }

/** [free] accents are available to everyone; the rest unlock with Supporter in the play flavour. */
enum class Accent(val free: Boolean) {
    TEAL(free = true),
    AMBER(free = false),
    CRIMSON(free = false),
    INDIGO(free = false),
    VIOLET(free = false),
    FOREST(free = false),
    SLATE(free = false),
}

enum class AppIcon(val free: Boolean) {
    DEFAULT(free = true),
    MONO(free = false),
    PAPER(free = false),
    NIGHT(free = false),
}

/** Search highlight colour. ACCENT follows the theme accent; the rest unlock with Supporter. */
enum class HighlightSet(val free: Boolean) {
    ACCENT(free = true),
    AMBER(free = false),
    ROSE(free = false),
    SKY(free = false),
}

enum class ReadingMode { CONTINUOUS, PAGED }

enum class PageDisplayMode { NORMAL, NIGHT, SEPIA, GRAYSCALE }

enum class LibrarySort { RECENTLY_OPENED, RECENTLY_ADDED, NAME, SIZE }

enum class LibraryLayout { LIST, GRID }

data class Settings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val accent: Accent = Accent.TEAL,
    val dynamicColor: Boolean = false,
    val appIcon: AppIcon = AppIcon.DEFAULT,
    val highlightSet: HighlightSet = HighlightSet.ACCENT,
    val readingMode: ReadingMode = ReadingMode.CONTINUOUS,
    val pageDisplayMode: PageDisplayMode = PageDisplayMode.NORMAL,
    val keepScreenOn: Boolean = false,
    val edgeBrightness: Boolean = false,
    val doubleTapZoom: Boolean = true,
    val volumeKeysTurnPages: Boolean = false,
    /** Persisted tree URI for tool output, or null to ask every time. */
    val defaultSaveTreeUri: String? = null,
    val outputNamePattern: String = DEFAULT_OUTPUT_PATTERN,
    val confirmOverwrite: Boolean = true,
    val librarySort: LibrarySort = LibrarySort.RECENTLY_OPENED,
    val libraryLayout: LibraryLayout = LibraryLayout.LIST,
    /** Google Play holds the Supporter pack for this account. The play flavour rewrites it after every check. */
    val supporterUnlockOwned: Boolean = false,
    /** The big tip was given on this phone. Kept locally: tips are consumed, so Play has no record to restore. */
    val supporterTipped: Boolean = false,
    /** Run OCR on scanned pages so the PDF is searchable. */
    val scanOcr: Boolean = true,
    /** The welcome tour has been seen or skipped. */
    val tourSeen: Boolean = false,
    /** Tesseract codes joined by "+", for example "eng+srp_latn". */
    val ocrLanguages: String = defaultOcrLanguages(),
    /** Language codes whose "get this pack" suggestion was answered with Not now, joined by +. */
    val ocrPackDismissed: String = "",
) {
    /** Every cosmetic is unlocked. Either gesture earns it; foss never reads this. */
    val isSupporter: Boolean get() = supporterUnlockOwned || supporterTipped

    companion object {
        const val DEFAULT_OUTPUT_PATTERN = "{name}_{op}"

        /** The device language when its pack ships with the app, otherwise English. */
        fun defaultOcrLanguages(): String = OcrLanguages.defaultFor(Locale.getDefault())
    }
}
