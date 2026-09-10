package com.leaf.app.util.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextQualityTest {

    @Test
    fun `ordinary latin and cyrillic text is fine`() {
        assertFalse(TextQuality.looksGarbled("Ugovor o zakupu stana, član 1. Zakupodavac daje u zakup."))
        assertFalse(TextQuality.looksGarbled("Уговор о закупу стана, члан 1. Закуподавац даје у закуп."))
        assertFalse(TextQuality.looksGarbled("契約書 第1条 賃貸人は賃借人に対し、本物件を賃貸する。"))
    }

    @Test
    fun `private use codes from a font without a unicode map are garbled`() {
        val fromBrokenFont = "   2024.  1"
        assertTrue(TextQuality.looksGarbled(fromBrokenFont))
    }

    @Test
    fun `replacement characters and symbols are garbled`() {
        assertTrue(TextQuality.looksGarbled("��� 12 ����� ���� 2024 ������"))
        assertTrue(TextQuality.looksGarbled("#\$%&*@!^~|<>{}[]()+=-_ 12 34 56 78 90 ##\$\$%%&&"))
    }

    @Test
    fun `short fragments are never judged`() {
        assertFalse(TextQuality.looksGarbled(" 12"))
        assertFalse(TextQuality.looksGarbled(""))
    }

    @Test
    fun `numbers with a few words are fine`() {
        assertFalse(TextQuality.looksGarbled("Iznos: 12.500,00 RSD, 2024-01-15, faktura 0042, PDV 20%"))
    }
}
