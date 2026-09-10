package com.leaf.app.data.pdf.write

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The JSON shape is persisted by WorkManager, so specs written before a field existed must still decode. */
class OperationSpecTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun specWithoutOcrLanguagesDecodesToNone() {
        val old = """{"type":"IMAGES_TO_PDF","images":[{"uri":"content://x/1","rotation":90}],"pageSize":"A4","fit":"FIT","margin":"SMALL"}"""
        val spec = json.decodeFromString(OperationSpec.serializer(), old)
        assertEquals(OperationCodec.IMAGES_TO_PDF, spec.type)
        assertEquals(1, spec.images.size)
        assertEquals(90, spec.images[0].rotation)
        assertTrue(spec.ocrLanguages.isEmpty())
    }

    @Test
    fun specWithoutNewToolFieldsDecodesToDefaults() {
        val old = """{"type":"PAGE_NUMBERS","sources":["content://x/1"]}"""
        val spec = json.decodeFromString(OperationSpec.serializer(), old)
        assertEquals(PageNumberPosition.BOTTOM_CENTRE.name, spec.numberPosition)
        assertEquals(1, spec.numberStartAt)
        assertEquals("{n}", spec.numberFormat)
        assertTrue(spec.boxes.isEmpty())
    }

    @Test
    fun pageNumbersSpecFieldsRoundTrip() {
        val spec = OperationSpec(type = OperationCodec.PAGE_NUMBERS, sources = listOf("content://x/1"), numberPosition = "TOP_RIGHT", numberStartAt = 4, numberFormat = "{n} / {total}")
        val text = json.encodeToString(OperationSpec.serializer(), spec)
        val back = json.decodeFromString(OperationSpec.serializer(), text)
        assertEquals("TOP_RIGHT", back.numberPosition)
        assertEquals(4, back.numberStartAt)
        assertEquals("{n} / {total}", back.numberFormat)
    }

    @Test
    fun ocrPdfSpecCarriesLanguages() {
        val spec = OperationSpec(type = OperationCodec.OCR_PDF, sources = listOf("content://x/1"), ocrLanguages = listOf("eng", "srp"))
        val text = json.encodeToString(OperationSpec.serializer(), spec)
        assertTrue(text, text.contains("\"type\":\"OCR_PDF\""))
        val back = json.decodeFromString(OperationSpec.serializer(), text)
        assertEquals(listOf("eng", "srp"), back.ocrLanguages)
    }

    @Test
    fun redactBoxesRoundTrip() {
        val boxes = listOf(OperationSpec.BoxSpec(0, 0.1f, 0.2f, 0.5f, 0.25f), OperationSpec.BoxSpec(3, 0f, 0f, 1f, 1f))
        val spec = OperationSpec(type = OperationCodec.REDACT, sources = listOf("content://x/1"), boxes = boxes)
        val text = json.encodeToString(OperationSpec.serializer(), spec)
        assertEquals(boxes, json.decodeFromString(OperationSpec.serializer(), text).boxes)
    }

    @Test
    fun ocrLanguagesRoundTrip() {
        val spec = OperationSpec(type = OperationCodec.IMAGES_TO_PDF, ocrLanguages = listOf("eng", "srp_latn"))
        val text = json.encodeToString(OperationSpec.serializer(), spec)
        assertTrue(text, text.contains("\"ocrLanguages\":[\"eng\",\"srp_latn\"]"))
        assertEquals(listOf("eng", "srp_latn"), json.decodeFromString(OperationSpec.serializer(), text).ocrLanguages)
    }

    @Test
    fun noteSpecFieldsRoundTripAndDefault() {
        val old = """{"type":"TEXT_TO_PDF"}"""
        val defaults = json.decodeFromString(OperationSpec.serializer(), old)
        assertEquals("", defaults.noteTitle)
        assertEquals("", defaults.noteBody)
        assertEquals(12f, defaults.noteFontSize)

        val spec = OperationSpec(type = OperationCodec.TEXT_TO_PDF, noteTitle = "Уговор", noteBody = "prva\ndruga", noteFontSize = 14f)
        val back = json.decodeFromString(OperationSpec.serializer(), json.encodeToString(OperationSpec.serializer(), spec))
        assertEquals("Уговор", back.noteTitle)
        assertEquals("prva\ndruga", back.noteBody)
        assertEquals(14f, back.noteFontSize)
    }
}
