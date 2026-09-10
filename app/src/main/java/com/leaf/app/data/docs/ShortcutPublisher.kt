package com.leaf.app.data.docs

import com.leaf.app.data.db.entities.DocumentEntity

/**
 * Mirrors the most recently opened documents to launcher shortcuts. The repository calls
 * [publish] on an IO dispatcher whenever the recent list or a reading position changes;
 * the Android implementation lives in util/ContinueShortcuts.kt so this layer stays testable.
 */
fun interface ShortcutPublisher {
    /** [recent] is every row with lastOpenedAt set, most recent first, readable or not. */
    fun publish(recent: List<DocumentEntity>)

    companion object {
        /** Tests and platforms without a launcher. */
        val None = ShortcutPublisher { }
    }
}
