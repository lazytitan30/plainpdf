package com.leaf.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.leaf.app.MainActivity
import com.leaf.app.R
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.data.docs.ShortcutPublisher
import com.leaf.app.ui.OpenRequests
import kotlin.math.min

/**
 * "Continue reading" dynamic launcher shortcuts: the last documents opened, each one a VIEW
 * intent for our own already-persisted URI. Sits beside the two static shortcuts in
 * res/xml/shortcuts.xml and never exceeds what the launcher allows.
 */
class ContinueShortcuts(private val context: Context) : ShortcutPublisher {

    /** What the launcher currently holds; unchanged lists skip the IPC and the disk write. */
    @Volatile
    private var published: List<ContinueShortcutPolicy.Entry>? = null

    override fun publish(recent: List<DocumentEntity>) {
        try {
            val room = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - STATIC_SHORTCUTS
            val entries = ContinueShortcutPolicy.choose(recent, room.coerceIn(0, ContinueShortcutPolicy.MAX_DYNAMIC))
            if (entries == published) return
            disableStalePinned(entries)
            if (entries.isEmpty()) {
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            } else {
                ShortcutManagerCompat.setDynamicShortcuts(context, entries.mapIndexed(::shortcut))
            }
            published = entries
        } catch (e: RuntimeException) {
            // Launcher quirks (rate limits, odd OEM shortcut services) must never break the library.
            Log.w("Leaf", "Could not update launcher shortcuts", e)
        }
    }

    private fun shortcut(rank: Int, entry: ContinueShortcutPolicy.Entry): ShortcutInfoCompat {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.uri), context, MainActivity::class.java)
            .putExtra(OpenRequests.EXTRA_OWN_DOCUMENT, true)
        return ShortcutInfoCompat.Builder(context, entry.shortcutId)
            .setShortLabel(entry.shortLabel)
            .setLongLabel(context.getString(R.string.shortcut_continue_long, entry.title, entry.page))
            .setIcon(icon(entry))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    /** A pinned copy of a shortcut that left the list would otherwise keep opening a gone document. */
    private fun disableStalePinned(current: List<ContinueShortcutPolicy.Entry>) {
        val keep = current.map { it.shortcutId }.toSet()
        val stale = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .map { it.id }
            .filter { it.startsWith(ContinueShortcutPolicy.ID_PREFIX) && it !in keep }
        if (stale.isNotEmpty()) {
            ShortcutManagerCompat.disableShortcuts(context, stale, context.getString(R.string.shortcut_continue_gone))
        }
    }

    /** The page thumbnail centred in the adaptive safe zone; the page glyph when there is none yet. */
    private fun icon(entry: ContinueShortcutPolicy.Entry): IconCompat {
        val path = entry.thumbnailPath
        if (path != null) {
            runCatching { adaptiveFromThumbnail(path) }.getOrNull()?.let { return it }
        }
        return IconCompat.createWithResource(context, R.drawable.ic_shortcut_continue)
    }

    private fun adaptiveFromThumbnail(path: String): IconCompat? {
        val thumb = BitmapFactory.decodeFile(path) ?: return null
        val square = Bitmap.createBitmap(ADAPTIVE_PX, ADAPTIVE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        canvas.drawColor(BACKGROUND)
        // Launchers mask the outer 17% on every side; keep the page inside the middle 66%.
        val safe = ADAPTIVE_PX * 0.66f
        val scale = min(safe / thumb.width, safe / thumb.height)
        val w = thumb.width * scale
        val h = thumb.height * scale
        val left = (ADAPTIVE_PX - w) / 2f
        val top = (ADAPTIVE_PX - h) / 2f
        canvas.drawBitmap(thumb, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        thumb.recycle()
        return IconCompat.createWithAdaptiveBitmap(square)
    }

    private companion object {
        /** Scan and Open PDF from res/xml/shortcuts.xml. */
        const val STATIC_SHORTCUTS = 2
        const val ADAPTIVE_PX = 216
        /** Same green as the static shortcut icons. */
        const val BACKGROUND = 0xFF3A6E5F.toInt()
    }
}
