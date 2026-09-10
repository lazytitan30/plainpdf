package com.leaf.app.util.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Four corners in normalised image coordinates (0..1), clockwise from top-left. */
data class Quad(val tl: PointF, val tr: PointF, val br: PointF, val bl: PointF) {
    fun points(): List<PointF> = listOf(tl, tr, br, bl)

    companion object {
        fun full(inset: Float = 0f) = Quad(PointF(inset, inset), PointF(1f - inset, inset), PointF(1f - inset, 1f - inset), PointF(inset, 1f - inset))

        fun from(outline: Outline) = Quad(outline.tl.toPoint(), outline.tr.toPoint(), outline.br.toPoint(), outline.bl.toPoint())

        private fun Corner.toPoint() = PointF(x, y)
    }
}

enum class ScanLook { COLOR, GRAY, BLACK_WHITE }

/**
 * The Android side of document cleanup: EXIF-correct decode, perspective warp and JPEG
 * output. The detection and the filters live in [PageOutlineDetector] and [DocumentFilters],
 * which are plain Kotlin so they are unit tested on the JVM.
 */
class DocumentImageProcessor(private val resolver: ContentResolver) {

    /** Decodes with the long edge capped at [maxEdge] and EXIF orientation applied. */
    @Throws(IOException::class)
    fun decode(uri: Uri, maxEdge: Int = MAX_EDGE): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode returns null by design, so only the stream itself is checked.
        val probe = resolver.openInputStream(uri) ?: throw IOException("Cannot open image")
        probe.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) throw IOException("Not an image")
        var sample = 1
        while (longEdge / sample > maxEdge * 2) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Cannot decode image")
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            runCatching { ExifInterface(stream).rotationDegrees }.getOrDefault(0)
        } ?: 0
        val scale = maxEdge.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        if (matrix.isIdentity) return decoded
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (out !== decoded) decoded.recycle()
        return out
    }

    /** Finds the page outline on a small grayscale copy. Falls back to an inset frame. */
    fun detectQuad(bitmap: Bitmap): Quad {
        val scale = DETECT_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(8)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        val gray = IntArray(w * h) { DocumentFilters.luma(px[it]) }
        return Quad.from(PageOutlineDetector.detect(GrayImage(w, h, gray)))
    }

    /** Perspective-corrects [quad] out of [source] into an upright rectangle. */
    fun warp(source: Bitmap, quad: Quad): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val tl = PointF(quad.tl.x * w, quad.tl.y * h)
        val tr = PointF(quad.tr.x * w, quad.tr.y * h)
        val br = PointF(quad.br.x * w, quad.br.y * h)
        val bl = PointF(quad.bl.x * w, quad.bl.y * h)
        val cap = adaptiveMaxEdge()
        val outW = ((dist(tl, tr) + dist(bl, br)) / 2f).roundToInt().coerceIn(64, cap)
        val outH = ((dist(tl, bl) + dist(tr, br)) / 2f).roundToInt().coerceIn(64, cap)
        val matrix = Matrix()
        matrix.setPolyToPoly(
            floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y), 0,
            floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat()), 0,
            4,
        )
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Applies the chosen look to [bitmap] in place. It must be mutable, as [warp] output is. */
    fun enhanceInPlace(bitmap: Bitmap, look: ScanLook) {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        when (look) {
            ScanLook.COLOR -> {
                DocumentFilters.whitenBackground(px, w, h)
                DocumentFilters.sharpen(px, w, h)
            }
            ScanLook.GRAY -> {
                DocumentFilters.toGray(px)
                DocumentFilters.whitenBackground(px, w, h)
                DocumentFilters.sharpen(px, w, h)
            }
            ScanLook.BLACK_WHITE -> {
                DocumentFilters.toGray(px)
                DocumentFilters.adaptiveThreshold(px, w, h)
            }
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h)
    }

    /**
     * Full pipeline to a JPEG file. At most two page-sized buffers are alive at any moment:
     * the photo is dropped as soon as the warped page exists, and the filters work in place.
     */
    @Throws(IOException::class)
    fun process(source: Uri, quad: Quad, look: ScanLook, rotationDegrees: Int, target: File): File {
        var bitmap = decode(source, adaptiveMaxEdge())
        if (rotationDegrees != 0) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
            if (rotated !== bitmap) { bitmap.recycle(); bitmap = rotated }
        }
        val page = try { warp(bitmap, quad) } finally { bitmap.recycle() }
        try {
            enhanceInPlace(page, look)
            target.parentFile?.mkdirs()
            target.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            return target
        } finally {
            page.recycle()
        }
    }

    private fun dist(a: PointF, b: PointF) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    companion object {
        /**
         * Long edge of the processed page. 3000 px across an A4 page is about 260 dpi,
         * enough for small print to stay legible; the camera photo is usually 4000 px.
         */
        const val MAX_EDGE = 3000
        const val MIN_EDGE = 1200
        const val DETECT_EDGE = 400
        const val JPEG_QUALITY = 92

        /**
         * The long edge this device can afford: the pipeline needs about three page-sized
         * ARGB buffers at its peak, and half the heap is left for everything else.
         */
        fun adaptiveMaxEdge(): Int {
            val budget = Runtime.getRuntime().maxMemory() / 2
            val edge = Math.sqrt(budget / (3.0 * 4 * 0.75)).toInt()
            return edge.coerceIn(MIN_EDGE, MAX_EDGE)
        }
    }
}
