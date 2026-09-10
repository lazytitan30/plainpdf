package com.leaf.app.ui.tools.merge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The merge password prompt is derived from item state, never from a single slot. */
class PasswordPromptTest {

    private data class Item(
        val name: String,
        override val encrypted: Boolean = false,
        override val pageCount: Int? = null,
        override val password: String? = null,
        override val passwordWrong: Boolean = false,
    ) : PasswordProbe

    @Test
    fun nothingToAskWhenNoItemIsEncrypted() {
        val items = listOf(Item("a", pageCount = 3), Item("b"))
        assertNull(items.firstAwaitingPassword())
    }

    @Test
    fun twoEncryptedItemsAreAskedOneAfterTheOther() {
        val first = Item("a", encrypted = true)
        val second = Item("b", encrypted = true)
        val items = listOf(Item("plain", pageCount = 2), first, second)
        assertEquals(first, items.firstAwaitingPassword())

        // The first password was accepted: the second must be prompted automatically.
        val afterFirst = items.map { if (it == first) it.copy(pageCount = 5, password = "pw") else it }
        assertEquals(second, afterFirst.firstAwaitingPassword())

        val afterBoth = afterFirst.map { if (it == second) it.copy(pageCount = 1, password = "pw2") else it }
        assertNull(afterBoth.firstAwaitingPassword())
    }

    @Test
    fun wrongPasswordKeepsTheSameItemPrompted() {
        val items = listOf(Item("a", encrypted = true, passwordWrong = true), Item("b", encrypted = true))
        assertEquals("a", items.firstAwaitingPassword()?.name)
        assertEquals(true, items.firstAwaitingPassword()?.passwordWrong)
    }

    @Test
    fun unreadableItemIsNotPrompted() {
        val items = listOf(Item("a", encrypted = true, pageCount = -1), Item("b", encrypted = true))
        assertEquals("b", items.firstAwaitingPassword()?.name)
    }
}
