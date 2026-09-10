package com.leaf.app.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start, measured three ways, so the profile's worth can be seen rather than claimed.
 * Numbers from an emulator show the direction reliably but are not the times a real phone gives.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Nothing compiled ahead of time: the slowest the app can be. */
    @Test
    fun startupNoCompilation() = measure(CompilationMode.None())

    /** Profiles ignored, which is what a phone sees when an app ships without one. */
    @Test
    fun startupWithoutProfile() = measure(CompilationMode.Partial(BaselineProfileMode.Disable, warmupIterations = 3))

    /** The profile in the app is used, which is what a phone does after installing from Play. */
    @Test
    fun startupWithProfile() = measure(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measure(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
