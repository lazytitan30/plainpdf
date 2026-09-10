package com.leaf.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CrashReportsTest {

    @Test
    fun `content uris and storage paths never reach the report`() {
        val trace = """
            java.io.IOException: Cannot open content://com.android.providers.media.documents/document/image%3A12
                at com.leaf.app.Foo.bar(Foo.kt:1)
            Caused by: java.io.FileNotFoundException: /storage/emulated/0/Documents/Contract with Bank.pdf (No such file)
        """.trimIndent()
        val scrubbed = CrashReports.scrub(trace)
        assertFalse(scrubbed.contains("providers.media"))
        assertFalse(scrubbed.contains("Contract"))
        assertEquals(
            """
            java.io.IOException: Cannot open content://…
                at com.leaf.app.Foo.bar(Foo.kt:1)
            Caused by: java.io.FileNotFoundException: /… (No such file)
            """.trimIndent(),
            scrubbed,
        )
    }
}
