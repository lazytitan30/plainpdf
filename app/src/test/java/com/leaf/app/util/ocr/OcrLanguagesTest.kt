package com.leaf.app.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OcrLanguagesTest {

    @Test
    fun appLanguagesMapToTesseractCodes() {
        assertEquals("eng", OcrLanguages.codeFor(Locale.ENGLISH))
        assertEquals("eng", OcrLanguages.codeFor(Locale.US))
        assertEquals("srp_latn", OcrLanguages.codeFor(Locale.forLanguageTag("sr-Latn")))
        assertEquals("srp_latn", OcrLanguages.codeFor(Locale.forLanguageTag("sr")))
        assertEquals("srp", OcrLanguages.codeFor(Locale.forLanguageTag("sr-Cyrl-RS")))
        assertEquals("deu", OcrLanguages.codeFor(Locale.GERMANY))
        assertEquals("por", OcrLanguages.codeFor(Locale.forLanguageTag("pt-BR")))
        assertEquals("ind", OcrLanguages.codeFor(Locale.forLanguageTag("id")))
        assertEquals("ind", OcrLanguages.codeFor(Locale.forLanguageTag("in")))
        assertEquals("chi_sim", OcrLanguages.codeFor(Locale.SIMPLIFIED_CHINESE))
        assertEquals("ara", OcrLanguages.codeFor(Locale.forLanguageTag("ar-EG")))
        assertEquals("hrv", OcrLanguages.codeFor(Locale.forLanguageTag("hr-HR")))
        assertEquals("hun", OcrLanguages.codeFor(Locale.forLanguageTag("hu")))
        assertNull(OcrLanguages.codeFor(Locale.forLanguageTag("sw")))
    }

    @Test
    fun everyKnownCodeHasANativeNameAndBundledOnesAreKnown() {
        val codes = OcrLanguages.known.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        OcrLanguages.known.forEach { assertTrue(it.code, it.nativeName.isNotBlank()) }
        OcrLanguages.known.forEach { assertTrue(it.code, it.sizeBytes > 100_000) }
        OcrLanguages.bundled.forEach { assertTrue(it, it in codes) }
        assertEquals(setOf("eng", "srp_latn", "srp", "deu", "spa", "fra", "ita", "rus"), OcrLanguages.bundled)
        assertEquals(20, OcrLanguages.downloadable.size)
        assertTrue(OcrLanguages.downloadable.none { it.code in OcrLanguages.bundled })
    }

    @Test
    fun defaultIsDeviceLanguageWhenBundledElseEnglish() {
        assertEquals("eng", OcrLanguages.defaultFor(Locale.US))
        assertEquals("srp_latn+srp", OcrLanguages.defaultFor(Locale.forLanguageTag("sr-Latn-RS")))
        assertEquals("srp_latn+srp", OcrLanguages.defaultFor(Locale.forLanguageTag("sr-Cyrl")))
        assertEquals("deu", OcrLanguages.defaultFor(Locale.GERMANY))
        assertEquals("eng", OcrLanguages.defaultFor(Locale.forLanguageTag("pt-BR")))
        assertEquals("eng", OcrLanguages.defaultFor(Locale.forLanguageTag("sw")))
    }

    @Test
    fun suggestsAPackOnlyForLanguagesThatAreNotBundled() {
        assertNull(OcrLanguages.downloadableFor(Locale.GERMANY))
        assertNull(OcrLanguages.downloadableFor(Locale.forLanguageTag("sw")))
        assertEquals("por", OcrLanguages.downloadableFor(Locale.forLanguageTag("pt-BR"))?.code)
        assertEquals("hun", OcrLanguages.downloadableFor(Locale.forLanguageTag("hu-HU"))?.code)
    }

    @Test
    fun namesFollowTheUserLanguageAndFallBackToTheCode() {
        assertEquals("English", OcrLanguages.nameOf("eng", Locale.ENGLISH))
        assertEquals("German", OcrLanguages.nameOf("deu", Locale.ENGLISH))
        assertEquals("Deutsch", OcrLanguages.nameOf("deu", Locale.GERMAN))
        assertTrue(OcrLanguages.nameOf("srp", Locale.ENGLISH).startsWith("Serbian"))
        assertEquals("lat", OcrLanguages.nameOf("lat", Locale.ENGLISH))
    }

    @Test
    fun packNamesRoundTrip() {
        assertEquals("lang_chi_sim", OcrLanguages.packName("chi_sim"))
        assertEquals("chi_sim", OcrLanguages.codeOfPack("lang_chi_sim"))
        assertNull(OcrLanguages.codeOfPack("lang_klingon"))
        assertNull(OcrLanguages.codeOfPack("other_pack"))
        assertNotNull(OcrLanguages.get("por"))
        assertTrue(OcrLanguages.isDownloadable("por"))
        assertFalse(OcrLanguages.isDownloadable("eng"))
    }

    @Test
    fun sizesReadAsMegabytes() {
        assertEquals("1.5 MB", OcrLanguages.sizeText(1_525_436))
        assertEquals("0.5 MB", OcrLanguages.sizeText(531_275))
    }

    @Test
    fun splitAndJoinRoundTripAndDropBlanksAndDuplicates() {
        assertEquals(listOf("eng", "srp_latn"), OcrLanguages.split("eng+srp_latn"))
        assertEquals(listOf("eng"), OcrLanguages.split("eng++eng+ "))
        assertEquals(emptyList<String>(), OcrLanguages.split(""))
        assertEquals("eng+deu", OcrLanguages.join(listOf("eng", "deu", "eng")))
    }

    @Test
    fun codeValidation() {
        assertTrue(OcrLanguages.isValidCode("eng"))
        assertTrue(OcrLanguages.isValidCode("chi_sim"))
        assertFalse(OcrLanguages.isValidCode(""))
        assertFalse(OcrLanguages.isValidCode("../eng"))
        assertFalse(OcrLanguages.isValidCode("Eng"))
    }
}
