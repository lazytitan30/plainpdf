package com.leaf.app.ui.reader

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Small in-memory cache of page renders for the slider preview, the page grid and the
 * bookmarks strip. Sized in bytes, not entries, so large pages do not blow the heap.
 */
class PageThumbnailCache(maxBytes: Int = 24 * 1024 * 1024) {

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val inFlight = HashMap<String, Mutex>()
    private val guard = Mutex()

    suspend fun get(pageIndex: Int, widthPx: Int, render: suspend () -> Bitmap?): Bitmap? {
        val key = "$pageIndex@$widthPx"
        cache.get(key)?.let { return it }
        val lock = guard.withLock { inFlight.getOrPut(key) { Mutex() } }
        return lock.withLock {
            cache.get(key) ?: render()?.also { cache.put(key, it) }
        }
    }

    fun peek(pageIndex: Int, widthPx: Int): Bitmap? = cache.get("$pageIndex@$widthPx")

    fun clear() = cache.evictAll()
}
