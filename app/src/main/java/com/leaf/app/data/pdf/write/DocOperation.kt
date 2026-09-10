package com.leaf.app.data.pdf.write

import android.net.Uri
import java.io.File

/** Every document-changing feature in the app is one of these. Implemented at M4 and M5. */
sealed interface DocOperation {
    data class Merge(val sources: List<MergeSource>) : DocOperation
    data class Split(val source: Uri, val mode: SplitMode) : DocOperation
    data class Organise(val source: Uri, val plan: PagePlan) : DocOperation
    /** [ocrLanguages] are Tesseract codes; when given, every page gets an invisible searchable text layer. */
    data class ImagesToPdf(
        val images: List<ImageInput>,
        val pageSize: PageSize,
        val fit: Fit,
        val margin: Margin,
        val ocrLanguages: List<String> = emptyList(),
    ) : DocOperation

    data class PdfToImages(
        val source: Uri,
        val pages: IntRange,
        val format: ImageFormat,
        val dpi: Int,
        val jpegQuality: Int = 90,
    ) : DocOperation

    /** [password] null removes protection. [currentPassword] is required when the source is encrypted. */
    data class SetPassword(val source: Uri, val password: String?, val currentPassword: String? = null) : DocOperation
    data class Watermark(val source: Uri, val text: String, val opts: WatermarkOptions) : DocOperation

    /** Re-encode the pictures inside a PDF smaller. Text and vectors are untouched. */
    data class Compress(val source: Uri, val level: CompressLevel) : DocOperation

    /** Draw a PNG, typically a drawn signature, onto one page. Fractions are of the displayed page, top-left origin. */
    data class Stamp(val source: Uri, val pageIndex: Int, val image: File, val leftFrac: Float, val topFrac: Float, val widthFrac: Float) : DocOperation

    /**
     * Print a number on every page. [format] takes "{n}" for the page's number and "{total}"
     * for the last number, so "{n} / {total}" reads "3 / 12".
     */
    data class PageNumbers(
        val source: Uri,
        val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTRE,
        val startAt: Int = 1,
        val format: String = "{n}",
    ) : DocOperation

    /** Recognise the text of pages that have none and add an invisible searchable layer. [languages] are Tesseract codes. */
    data class OcrPdf(val source: Uri, val languages: List<String>) : DocOperation

    /**
     * Black out parts of pages for good. Every page with a box is replaced by a picture of
     * itself with the boxes painted over, so nothing underneath survives.
     */
    data class Redact(val source: Uri, val boxes: List<RedactionBox>) : DocOperation

    /** Typed text saved as a fresh PDF. No source document: the note itself is the input. */
    data class TextToPdf(val title: String, val body: String, val fontSizePt: Float = 12f) : DocOperation
}

enum class PageNumberPosition { BOTTOM_CENTRE, BOTTOM_RIGHT, TOP_RIGHT }

/** One area to black out, as fractions of the displayed page with the origin at the top-left. */
data class RedactionBox(val pageIndex: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Every URI the operation reads. A destination that is one of these is an overwrite, never a fresh file. */
fun DocOperation.inputUris(): List<Uri> = when (this) {
    is DocOperation.Merge -> sources.map { it.uri }
    is DocOperation.Split -> listOf(source)
    is DocOperation.Organise -> listOf(source)
    is DocOperation.ImagesToPdf -> images.map { it.uri }
    is DocOperation.PdfToImages -> listOf(source)
    is DocOperation.SetPassword -> listOf(source)
    is DocOperation.Watermark -> listOf(source)
    is DocOperation.Stamp -> listOf(source)
    is DocOperation.Compress -> listOf(source)
    is DocOperation.PageNumbers -> listOf(source)
    is DocOperation.OcrPdf -> listOf(source)
    is DocOperation.Redact -> listOf(source)
    is DocOperation.TextToPdf -> emptyList()
}

/** True when the destination is one document from ACTION_CREATE_DOCUMENT rather than a tree. */
fun DocOperation.writesSingleFile(): Boolean = this !is DocOperation.Split && this !is DocOperation.PdfToImages

/** A merge input. [pages] null means every page; otherwise 0-based inclusive ranges in order. */
data class MergeSource(val uri: Uri, val pages: List<IntRange>? = null)

data class ImageInput(val uri: Uri, val rotationDegrees: Int = 0)

sealed interface SplitMode {
    /** 0-based inclusive ranges, one output file per range. */
    data class ByRanges(val ranges: List<IntRange>) : SplitMode
    data class EveryN(val pagesPerFile: Int) : SplitMode
}

enum class PageSize { FIT_TO_IMAGE, A4, LETTER }
enum class Fit { FIT, FILL }
enum class Margin { NONE, SMALL, MEDIUM }
enum class ImageFormat { PNG, JPEG }

/**
 * How hard Compress squeezes pictures: the longest edge they are scaled down to, the JPEG
 * quality, and whether pictures that are already JPEG get re-encoded (which costs quality
 * every time, so only the strongest level does it).
 */
enum class CompressLevel(val maxEdge: Int, val jpegQuality: Float, val recompressJpeg: Boolean) {
    LIGHT(2400, 0.85f, false),
    BALANCED(1600, 0.75f, false),
    STRONG(1200, 0.6f, true),
}

data class WatermarkOptions(
    val opacity: Float = 0.3f,
    val rotationDegrees: Float = 45f,
    val fontSizePt: Float = 48f,
)
