package com.leaf.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.leaf.app.data.prefs.Accent
import com.leaf.app.data.prefs.AppTheme

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun QuireTheme(
    theme: AppTheme,
    accent: Accent,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val palette = when (theme) {
        AppTheme.SYSTEM -> if (systemDark) DarkPalette else LightPalette
        AppTheme.LIGHT -> LightPalette
        AppTheme.DARK -> DarkPalette
        AppTheme.BLACK -> BlackPalette
        AppTheme.SEPIA -> SepiaPalette
    }
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = remember(palette, accent, theme, useDynamic, context) {
        val accentColor = accent.tone(dark = palette.isDark, sepia = theme == AppTheme.SEPIA)
        if (useDynamic) {
            val dynamic = if (palette.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.withSurfaces(palette)
        } else {
            quireColorScheme(palette, accentColor)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !palette.isDark
            controller.isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = QuireTypography,
            shapes = QuireShapes,
            content = content,
        )
    }
}

private fun quireColorScheme(p: Palette, accent: Color): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    val onAccent = if (p.isDark) p.background else Color.White
    val accentContainer = accent.copy(alpha = 0.16f).compositeOver(p.surface)
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = p.onBackground,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentContainer,
        onSecondaryContainer = p.onBackground,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = p.onBackground,
        surfaceTint = accent,
        inverseSurface = p.onBackground,
        inverseOnSurface = p.background,
    ).withSurfaces(p)
}

/** Overlays the palette's surface tokens on any scheme, dynamic or not. */
private fun ColorScheme.withSurfaces(p: Palette): ColorScheme = copy(
    background = p.background,
    onBackground = p.onBackground,
    surface = p.surface,
    onSurface = p.onBackground,
    surfaceVariant = p.surfaceVariant,
    onSurfaceVariant = p.onSurfaceVariant,
    outline = p.outline,
    outlineVariant = p.outline,
    surfaceBright = if (p.isDark) p.surfaceVariant else p.surface,
    surfaceDim = p.background,
    surfaceContainerLowest = if (p.isDark) p.background else p.surface,
    surfaceContainerLow = if (p.isDark) p.surface else p.background,
    surfaceContainer = if (p.isDark) p.surface else p.surfaceVariant,
    surfaceContainerHigh = p.surfaceVariant,
    surfaceContainerHighest = p.surfaceVariant,
    scrim = Color.Black,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
