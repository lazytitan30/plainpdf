package com.leaf.app.util.ocr

import java.util.Locale

/** Where a language pack comes from and where it sits in the Settings list. */
enum class PackTier {
    /** Ships inside the app. */
    BUNDLED,
    /** The most widely used languages, listed first under "Get more". */
    COMMON,
    /** Serbia's neighbours and the wider region. */
    NEARBY,
    /** Everything else the app offers. */
    OTHER,
}

/**
 * One Tesseract language pack: its traineddata code, the BCP 47 tag used to name it in the
 * user's own language, a native name as fallback, its tier and the download size.
 */
data class OcrLanguage(
    val code: String,
    val languageTag: String,
    val nativeName: String,
    val tier: PackTier,
    val sizeBytes: Long,
)

/**
 * The Tesseract languages the app knows, keyed by traineddata code. [bundled] ship inside
 * the app; the rest are on-demand packs that Google Play downloads when the user taps Get,
 * because the app itself has no network permission. Any other language can still be
 * imported from a `.traineddata` file.
 */
object OcrLanguages {

    /** Codes joined by this in settings and in the string handed to Tesseract. */
    const val SEPARATOR = "+"

    /** Asset pack names are the code with this prefix; see packs/ in the repository. */
    private const val PACK_PREFIX = "lang_"

    private const val MB = 1024L * 1024L

    val known: List<OcrLanguage> = listOf(
        // Bundled. Sizes are informational here; nothing is downloaded for these.
        OcrLanguage("eng", "en", "English", PackTier.BUNDLED, 4_113_088),
        OcrLanguage("srp_latn", "sr-Latn", "Srpski (latinica)", PackTier.BUNDLED, 3_270_492),
        OcrLanguage("srp", "sr-Cyrl", "Српски (ћирилица)", PackTier.BUNDLED, 2_224_580),
        OcrLanguage("deu", "de", "Deutsch", PackTier.BUNDLED, 1_525_436),
        OcrLanguage("spa", "es", "Español", PackTier.BUNDLED, 2_269_128),
        OcrLanguage("fra", "fr", "Français", PackTier.BUNDLED, 1_090_908),
        OcrLanguage("ita", "it", "Italiano", PackTier.BUNDLED, 2_704_620),
        OcrLanguage("rus", "ru", "Русский", PackTier.BUNDLED, 3_867_960),
        // Most used first.
        OcrLanguage("chi_sim", "zh-Hans", "简体中文", PackTier.COMMON, 2_469_156),
        OcrLanguage("por", "pt", "Português", PackTier.COMMON, 1_982_756),
        OcrLanguage("ara", "ar", "العربية", PackTier.COMMON, 1_432_056),
        OcrLanguage("hin", "hi", "हिन्दी", PackTier.COMMON, 1_122_751),
        OcrLanguage("jpn", "ja", "日本語", PackTier.COMMON, 2_471_260),
        OcrLanguage("kor", "ko", "한국어", PackTier.COMMON, 1_677_415),
        OcrLanguage("tur", "tr", "Türkçe", PackTier.COMMON, 4_550_554),
        OcrLanguage("pol", "pl", "Polski", PackTier.COMMON, 4_765_518),
        OcrLanguage("vie", "vi", "Tiếng Việt", PackTier.COMMON, 531_275),
        OcrLanguage("ind", "id", "Bahasa Indonesia", PackTier.COMMON, 1_122_661),
        // The region.
        OcrLanguage("hrv", "hr", "Hrvatski", PackTier.NEARBY, 4_103_348),
        OcrLanguage("slv", "sl", "Slovenščina", PackTier.NEARBY, 3_003_829),
        OcrLanguage("bul", "bg", "Български", PackTier.NEARBY, 1_675_212),
        OcrLanguage("mkd", "mk", "Македонски", PackTier.NEARBY, 1_600_188),
        OcrLanguage("hun", "hu", "Magyar", PackTier.NEARBY, 5_296_273),
        OcrLanguage("ron", "ro", "Română", PackTier.NEARBY, 2_376_323),
        OcrLanguage("ell", "el", "Ελληνικά", PackTier.NEARBY, 1_419_514),
        // The rest.
        OcrLanguage("ukr", "uk", "Українська", PackTier.OTHER, 3_825_102),
        OcrLanguage("nld", "nl", "Nederlands", PackTier.OTHER, 6_050_296),
        OcrLanguage("ces", "cs", "Čeština", PackTier.OTHER, 3_795_684),
    )

    private val byCode: Map<String, OcrLanguage> = known.associateBy { it.code }

    /** Shipped in the APK under assets/tessdata. */
    val bundled: Set<String> = known.filter { it.tier == PackTier.BUNDLED }.map { it.code }.toSet()

    /** Available as Play asset packs, in the order the Settings list shows them. */
    val downloadable: List<OcrLanguage> = known.filter { it.tier != PackTier.BUNDLED }

    /** ISO 639-1 language to Tesseract code; Serbian is handled by script in [codeFor]. */
    private val byIsoLanguage: Map<String, String> = buildMap {
        known.filter { !it.languageTag.startsWith("sr") }.forEach { put(Locale.forLanguageTag(it.languageTag).language, it.code) }
        put("in", "ind") // Java's legacy code for Indonesian
        put("iw", "heb") // Java's legacy code for Hebrew, no pack yet but keep the mapping honest
    }

    /** The Tesseract code for a locale, or null when the app has no pack for it at all. */
    fun codeFor(locale: Locale): String? {
        val language = locale.language
        if (language == "sr") {
            return if (locale.script == "Cyrl") "srp" else "srp_latn"
        }
        return byIsoLanguage[language]?.takeIf { it in byCode }
    }

    /**
     * The device language when its pack is bundled, otherwise English. Serbian gets both
     * scripts: documents in Serbia come in Latin and Cyrillic alike, and reading Cyrillic
     * with the Latin model turns every word into rubbish.
     */
    fun defaultFor(locale: Locale): String {
        val code = codeFor(locale) ?: return "eng"
        if (code == "srp_latn" || code == "srp") return "srp_latn+srp"
        return if (code in bundled) code else "eng"
    }

    /** The pack a device language needs but the app does not bundle, if any. */
    fun downloadableFor(locale: Locale): OcrLanguage? {
        val code = codeFor(locale) ?: return null
        return byCode[code]?.takeIf { it.tier != PackTier.BUNDLED }
    }

    fun get(code: String): OcrLanguage? = byCode[code]

    /**
     * The language's name in the user's own language ("German" on an English phone,
     * "Nemački" on a Serbian one), falling back to the native name for anything the
     * platform cannot name.
     */
    fun nameOf(code: String, inLocale: Locale = Locale.getDefault()): String {
        val language = byCode[code] ?: return code
        val tag = Locale.forLanguageTag(language.languageTag)
        val named = tag.getDisplayName(inLocale)
        if (named.isBlank() || named.equals(language.languageTag, ignoreCase = true) || named.equals(tag.language, ignoreCase = true)) {
            return language.nativeName
        }
        return named.replaceFirstChar { if (it.isLowerCase()) it.titlecase(inLocale) else it.toString() }
    }

    fun isBundled(code: String): Boolean = code in bundled

    fun isDownloadable(code: String): Boolean = byCode[code]?.tier?.let { it != PackTier.BUNDLED } == true

    fun packName(code: String): String = PACK_PREFIX + code

    fun codeOfPack(packName: String): String? =
        packName.removePrefix(PACK_PREFIX).takeIf { packName.startsWith(PACK_PREFIX) && it in byCode }

    /** "1.5 MB", one decimal, for the Get list. */
    fun sizeText(bytes: Long): String {
        val mb = bytes.toDouble() / MB
        return if (mb < 1.0) String.format(Locale.ROOT, "%.1f MB", mb) else String.format(Locale.ROOT, "%.1f MB", mb)
    }

    /** "eng+srp_latn" to ["eng", "srp_latn"], dropping blanks and duplicates. */
    fun split(joined: String): List<String> = joined.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun join(codes: List<String>): String = codes.distinct().joinToString(SEPARATOR)

    /** A traineddata code is lowercase letters, digits and underscores, like "srp_latn" or "chi_sim". */
    fun isValidCode(code: String): Boolean = code.isNotEmpty() && code.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
}
