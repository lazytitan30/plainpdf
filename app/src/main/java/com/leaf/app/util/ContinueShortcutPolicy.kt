package com.leaf.app.util

import com.leaf.app.data.db.entities.DocumentEntity

/**
 * Which documents become "Continue reading" launcher shortcuts, and what they say. Pure so it
 * can be unit tested; [ContinueShortcuts] turns the entries into ShortcutInfoCompat.
 */
object ContinueShortcutPolicy {

    /** Launchers clip short labels around here; keep the title readable rather than cut mid-word. */
    const val MAX_LABEL_CHARS = 25

    /** Never more than this many, whatever the launcher allows; the static Scan and Open come first. */
    const val MAX_DYNAMIC = 2

    data class Entry(
        val documentId: Long,
        val uri: String,
        val title: String,
        val shortLabel: String,
        /** One-based page for the long label. */
        val page: Int,
        val thumbnailPath: String?,
    ) {
        val shortcutId: String get() = "$ID_PREFIX$documentId"
    }

    const val ID_PREFIX = "continue:"

    /**
     * Most recent first, skipping rows that were never opened or that can no longer be read,
     * capped at [max]. Input order does not matter.
     */
    fun choose(recent: List<DocumentEntity>, max: Int): List<Entry> {
        if (max <= 0) return emptyList()
        return recent
            .filter { it.lastOpenedAt != null && !it.permissionLost }
            .sortedByDescending { it.lastOpenedAt }
            .take(max)
            .map { doc ->
                Entry(
                    documentId = doc.id,
                    uri = doc.uri,
                    title = doc.title,
                    shortLabel = shortLabel(doc.title),
                    page = doc.lastPage + 1,
                    thumbnailPath = doc.thumbnailPath,
                )
            }
    }

    /** At most [MAX_LABEL_CHARS] characters, ending in an ellipsis when something was cut. */
    fun shortLabel(title: String): String {
        val trimmed = title.trim()
        if (trimmed.length <= MAX_LABEL_CHARS) return trimmed
        return trimmed.take(MAX_LABEL_CHARS - 1).trimEnd() + "…"
    }
}
