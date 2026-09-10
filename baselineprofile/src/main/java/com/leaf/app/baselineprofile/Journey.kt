package com.leaf.app.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

const val PACKAGE = "com.plainpdf.app"

private const val WAIT_MS = 8_000L

/**
 * The first minute of use, as a new person would spend it: the welcome tour, the library,
 * the tools list, settings. Every step is optional on purpose. A recording that stops early
 * still produces a valid profile, whereas a failed step would produce none at all.
 */
fun UiDevice.firstMinute() {
    skipTour()
    wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), WAIT_MS)

    openTab("Tools")
    scrollAround()

    openTab("Library")
    waitForIdle()
}

/** The tour only appears on a fresh install; on later runs there is nothing to skip. */
private fun UiDevice.skipTour() {
    val skip = wait(Until.findObject(By.text("Skip")), WAIT_MS) ?: return
    skip.click()
    waitForIdle()
}

private fun UiDevice.openTab(label: String) {
    val tab = wait(Until.findObject(By.text(label).clickable(true)), WAIT_MS)
        ?: wait(Until.findObject(By.text(label)), 1_000)
        ?: return
    tab.click()
    waitForIdle()
}

/** Scrolling is what forces the list and text code to run, which is most of what we want recorded. */
private fun UiDevice.scrollAround() {
    val list = wait(Until.findObject(By.scrollable(true)), WAIT_MS) ?: return
    list.setGestureMargin(displayWidth / 5)
    repeat(2) {
        list.fling(Direction.DOWN)
        waitForIdle()
    }
    list.fling(Direction.UP)
    waitForIdle()
}
