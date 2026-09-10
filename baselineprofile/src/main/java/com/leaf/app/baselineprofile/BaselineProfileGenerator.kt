package com.leaf.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the profile. Run with `./gradlew :app:generateBaselineProfile` and commit the result;
 * rerun it when the startup path changes noticeably, not on every edit.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        // Android compiles the startup part before the app is first opened, and the rest in
        // the background afterwards, so the launch itself gets the full benefit.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.firstMinute()
    }
}
