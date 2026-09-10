package com.leaf.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * System font on purpose: zero APK bytes, instant cold start, and it follows the user's
 * own font size setting. Sentence case everywhere, letter spacing 0 except labelMedium.
 */
private fun style(size: Int, line: Int, weight: FontWeight, spacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing,
)

val QuireTypography = Typography(
    displayLarge = style(40, 48, FontWeight.Medium),
    displayMedium = style(34, 40, FontWeight.Medium),
    displaySmall = style(28, 34, FontWeight.Medium),
    headlineLarge = style(26, 32, FontWeight.Medium),
    headlineMedium = style(24, 30, FontWeight.Medium),
    headlineSmall = style(22, 28, FontWeight.Medium),
    titleLarge = style(20, 26, FontWeight.Medium),
    titleMedium = style(16, 22, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(15, 22, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(13, 16, FontWeight.Medium, spacing = 0.1.sp),
    labelSmall = style(13, 18, FontWeight.Normal),
)
