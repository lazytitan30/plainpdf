package com.leaf.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The baseline profile is a recorded file, not generated code, so nothing else would notice
 * if it went missing or arrived empty. Regenerate it with
 * `./gradlew :app:generateBaselineProfile` on a connected device.
 */
class BaselineProfileTest {

    private val profile = File("src/main/generated/baselineProfiles/baseline-prof.txt")

    @Test
    fun profileIsPresentAndCoversTheAppsOwnStartup() {
        assertTrue("baseline profile is missing at ${profile.path}", profile.isFile())
        val lines = profile.readLines().filter { it.isNotBlank() }
        assertTrue("baseline profile has only ${lines.size} rules, so it was not recorded properly", lines.size > 5_000)
        assertTrue(
            "baseline profile does not mention MainActivity, so it did not record a real launch",
            lines.any { it.contains("Lcom/leaf/app/MainActivity;") },
        )
    }
}
