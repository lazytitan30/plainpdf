package com.leaf.app.data.docs

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Renders page 0 with the framework PdfRenderer, cheaper than spinning up the viewer.
 * Output is WEBP quality 80 at [WIDTH_PX] wide in cacheDir/thumbs, never filesDir.
 */
class ThumbnailGenerator(context: Context, private val resolver: ContentResolver) {

    private val dir = File(context.cacheDir, "thumbs")
    private val permits = Semaphore(2)

    /** Path of a ready thumbnail, rendering it if needed. Null when the document cannot be read. */
    suspend fun ensure(documentId: Long, uri: Uri, existingPath: String?): String? {
        if (existingPath != null && File(existingPath).exists()) return existingPath
        return withContext(Dispatchers.IO) {
            permits.withPermit { render(documentId, uri) }
        }
    }

    private fun render(documentId: Long, uri: Uri): String? {
        val target = File(dir, "$documentId.webp")
        if (target.exists()) return target.path
        val pfd: ParcelFileDescriptor = try {
            resolver.openFileDescriptor(uri, "r") ?: return null
        } catch (_: FileNotFoundException) {
            return null
        } catch (_: SecurityException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return try {
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val width = WIDTH_PX
                        val height = (width.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        dir.mkdirs()
                        val tmp = File(dir, "$documentId.tmp")
                        tmp.outputStream().use { out ->
                            @Suppress("DEPRECATION")
                            val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                            bitmap.compress(format, QUALITY, out)
                        }
                        bitmap.recycle()
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                        target.path
                    }
                }
            }
        } catch (_: SecurityException) {
            // Password protected: the framework renderer refuses. No thumbnail.
            null
        } catch (_: IOException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun cacheSizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Removes thumbnails no document row refers to any more. */
    fun pruneOrphans(referencedPaths: Set<String>) {
        dir.listFiles()?.forEach { if (it.absolutePath !in referencedPaths) it.delete() }
    }

    companion object {
        const val WIDTH_PX = 320
        const val QUALITY = 80
    }
}
