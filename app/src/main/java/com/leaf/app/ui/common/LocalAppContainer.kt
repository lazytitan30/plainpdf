package com.leaf.app.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.leaf.app.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
