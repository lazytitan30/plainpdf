package com.leaf.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.leaf.app.data.prefs.Accent

/** The six tokens each theme defines. Everything else in the ColorScheme derives from these. */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val isDark: Boolean,
)

val LightPalette = Palette(
    background = Color(0xFFFAFAF8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFEFEA),
    onBackground = Color(0xFF1A1A17),
    onSurfaceVariant = Color(0xFF5C5C55),
    outline = Color(0xFFD6D6D0),
    isDark = false,
)

/** Slightly cool so long sessions do not feel like a void. */
val DarkPalette = Palette(
    background = Color(0xFF16181A),
    surface = Color(0xFF1E2124),
    surfaceVariant = Color(0xFF2A2E32),
    onBackground = Color(0xFFE4E4E0),
    onSurfaceVariant = Color(0xFF9BA1A6),
    outline = Color(0xFF3A3F44),
    isDark = true,
)

/** OLED. A distinct theme, not a dark variant. */
val BlackPalette = Palette(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF141414),
    onBackground = Color(0xFFE0E0DC),
    onSurfaceVariant = Color(0xFF8A8A85),
    outline = Color(0xFF262626),
    isDark = true,
)

val SepiaPalette = Palette(
    background = Color(0xFFF4ECD8),
    surface = Color(0xFFFBF5E6),
    surfaceVariant = Color(0xFFE8DEC5),
    onBackground = Color(0xFF4A3F2F),
    onSurfaceVariant = Color(0xFF7A6A52),
    outline = Color(0xFFD4C6A8),
    isDark = false,
)

/** Accent tone for a palette. Teal has a warmer sepia variant; the others have light/dark only. */
fun Accent.tone(dark: Boolean, sepia: Boolean): Color = when (this) {
    Accent.TEAL -> when {
        sepia -> Color(0xFF2F6B57)
        dark -> Color(0xFF7FB6A4)
        else -> Color(0xFF3A6E5F)
    }
    Accent.AMBER -> if (dark) Color(0xFFE0A85C) else Color(0xFFC8862A)
    Accent.CRIMSON -> if (dark) Color(0xFFD97169) else Color(0xFFB4443C)
    Accent.INDIGO -> if (dark) Color(0xFF8494D4) else Color(0xFF4A5A9E)
    Accent.VIOLET -> if (dark) Color(0xFFAE93D6) else Color(0xFF7A5AA8)
    Accent.FOREST -> if (dark) Color(0xFF74B486) else Color(0xFF3D7A4E)
    Accent.SLATE -> if (dark) Color(0xFF97A8B4) else Color(0xFF5A6B78)
}
