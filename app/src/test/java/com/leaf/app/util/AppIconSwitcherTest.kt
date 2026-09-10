package com.leaf.app.util

import com.leaf.app.MainActivity
import com.leaf.app.data.prefs.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The alias class names the switcher hands to the package manager must be exactly what the
 * manifest declares, resolved against the namespace, not the application id. Version 1.0.0
 * (2) crashed on every icon change because the two were mixed up.
 */
class AppIconSwitcherTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private val namespace = MainActivity::class.java.name.substringBeforeLast('.')

    @Test
    fun everyIconHasAnAliasInTheManifest() {
        val declared = Regex("""android:name="(\.launcher\.[A-Za-z]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        AppIcon.entries.forEach { icon ->
            val relative = AppIconSwitcher.aliasClassName(icon).removePrefix(namespace)
            assertTrue("no alias $relative for $icon", relative in declared)
        }
        assertEquals(AppIcon.entries.size, declared.size)
    }

    @Test
    fun aliasNamesUseTheNamespaceNotTheApplicationId() {
        assertEquals("com.leaf.app.launcher.Paper", AppIconSwitcher.aliasClassName(AppIcon.PAPER))
        assertTrue(AppIconSwitcher.aliasClassName(AppIcon.DEFAULT).startsWith("$namespace.launcher."))
    }
}
