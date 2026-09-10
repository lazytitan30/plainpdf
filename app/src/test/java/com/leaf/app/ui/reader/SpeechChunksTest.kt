package com.leaf.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {

    @Test
    fun emptyOrBlankPageYieldsNothing() {
        assertEquals(emptyList<String>(), SpeechChunks.split(""))
        assertEquals(emptyList<String>(), SpeechChunks.split(" \n\t \n"))
    }

    @Test
    fun foldsLineBreaksIntoSpaces() {
        assertEquals(listOf("The quick brown fox jumps."), SpeechChunks.split("The quick\nbrown   fox\r\njumps."))
    }

    @Test
    fun packsShortSentencesIntoOneUtterance() {
        val chunks = SpeechChunks.split("One. Two! Three? Four… \"Five.\" Six")
        assertEquals(listOf("One. Two! Three? Four… \"Five.\" Six"), chunks)
    }

    @Test
    fun startsANewUtteranceWhenTargetIsReached() {
        val sentence = "x".repeat(SpeechChunks.TARGET_LENGTH - 10) + "."
        val chunks = SpeechChunks.split("$sentence Short one. $sentence")
        assertEquals(3, chunks.size)
        assertEquals(sentence, chunks[0])
        assertEquals("Short one.", chunks[1])
        assertEquals(sentence, chunks[2])
    }

    @Test
    fun cutsAnOverlongSentenceAtWordBoundaries() {
        val words = (1..2000).joinToString(" ") { "word$it" }
        val chunks = SpeechChunks.split(words, maxLength = 100)
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue("chunk too long: ${chunk.length}", chunk.length <= 100)
            assertTrue("chunk cut inside a word: $chunk", chunk.matches(Regex("(word\\d+ )*word\\d+")))
        }
        assertEquals(words, chunks.joinToString(" "))
    }

    @Test
    fun cutsASingleGiantWordHard() {
        val chunks = SpeechChunks.split("a".repeat(250), maxLength = 100)
        assertEquals(listOf(100, 100, 50), chunks.map { it.length })
    }

    @Test
    fun neverExceedsMaxLengthEvenWhenTargetIsLarger() {
        val text = (1..50).joinToString(" ") { "Sentence number $it ends here." }
        SpeechChunks.split(text, maxLength = 40).forEach { assertTrue(it.length <= 40) }
    }

    @Test
    fun doesNotSplitOnADotInsideAWord() {
        assertEquals(listOf("Visit example.com today. Then leave."), SpeechChunks.split("Visit example.com today. Then leave."))
    }
}
