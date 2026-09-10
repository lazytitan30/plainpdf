package com.leaf.app.util.saf

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantFlagsTest {

    @Test
    fun readsFlagsFromIntentBits() {
        val both = GrantFlags.fromIntentFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertTrue(both.read)
        assertTrue(both.write)

        val readOnly = GrantFlags.fromIntentFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertTrue(readOnly.read)
        assertFalse(readOnly.write)

        val none = GrantFlags.fromIntentFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(GrantFlags.None, none)
    }

    @Test
    fun roundTripsThroughIntentFlags() {
        for (flags in listOf(GrantFlags.None, GrantFlags.ReadOnly, GrantFlags.ReadWrite)) {
            assertEquals(flags, GrantFlags.fromIntentFlags(flags.intentFlags))
        }
    }

    @Test
    fun writeWithoutReadIsStillRepresentable() {
        val writeOnly = GrantFlags(read = false, write = true)
        assertEquals(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, writeOnly.intentFlags)
    }
}
