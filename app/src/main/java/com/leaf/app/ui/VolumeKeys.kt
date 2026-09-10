package com.leaf.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Volume key presses forwarded from the activity. The reader sets [consume] while it wants
 * them; otherwise the system adjusts volume as usual.
 */
class VolumeKeys {
    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** +1 for volume down (next page), -1 for volume up (previous page). */
    val events: SharedFlow<Int> = _events

    @Volatile
    var consume: Boolean = false

    fun offer(delta: Int): Boolean {
        if (!consume) return false
        _events.tryEmit(delta)
        return true
    }
}

val LocalVolumeKeys = staticCompositionLocalOf { VolumeKeys() }
