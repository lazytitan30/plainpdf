package com.leaf.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space to leave under a list or an empty state so the two stacked floating buttons never
 * cover the last row. The buttons grow with the font size, so the clearance does too: at
 * the largest font they are almost twice as tall as at the default.
 */
@Composable
fun fabClearance(): Dp {
    val fontScale = LocalDensity.current.fontScale
    return (96f + 96f * (fontScale - 1f).coerceAtLeast(0f)).dp
}
