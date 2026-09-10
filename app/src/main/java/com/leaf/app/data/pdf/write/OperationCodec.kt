package com.leaf.app.data.pdf.write

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON shape of a [DocOperation] for WorkManager input data. Passwords are deliberately
 * not part of it: WorkManager persists input to disk, and passwords are never stored.
 */
@Serializable
data class OperationSpec(
    val type: String,
    val sources: List<String> = emptyList(),
    val sourceRanges: List<List<PageRangeSpec>?> = emptyList(),
    val plan: List<PlanOp> = emptyList(),
    val splitRanges: List<PageRangeSpec> = emptyList(),
    val everyN: Int = 0,
    val images: List<ImageSpec> = emptyList(),
    val pageSize: String = PageSize.A4.name,
    val fit: String = Fit.FIT.name,
    val margin: String = Margin.NONE.name,
    val pageRange: PageRangeSpec? = null,
    val imageFormat: String = ImageFormat.PNG.name,
    val dpi: Int = 150,
    val jpegQuality: Int = 90,
    val password: String? = null,
    val text: String = "",
    val opacity: Float = 0.3f,
    val rotation: Float = 45f,
    val fontSize: Float = 48f,
    val stampPage: Int = 0,
    val stampImage: String = "",
    val stampLeft: Float = 0f,
    val stampTop: Float = 0f,
    val stampWidth: Float = 0f,
    val compressLevel: String = CompressLevel.BALANCED.name,
    val ocrLanguages: List<String> = emptyList(),
    val numberPosition: String = PageNumberPosition.BOTTOM_CENTRE.name,
    val numberStartAt: Int = 1,
    val numberFormat: String = "{n}",
    val boxes: List<BoxSpec> = emptyList(),
    val noteTitle: String = "",
    val noteBody: String = "",
    val noteFontSize: Float = 12f,
) {
    @Serializable
    data class PlanOp(val source: Int, val rotation: Int)

    @Serializable
    data class ImageSpec(val uri: String, val rotation: Int)

    @Serializable
    data class BoxSpec(val page: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)
}

@Serializable
data class PageRangeSpec(val first: Int, val last: Int) {
    fun toKotlin(): IntRange = first..last

    companion object {
        fun of(r: IntRange) = PageRangeSpec(r.first, r.last)
    }
}

object OperationCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    const val MERGE = "MERGE"
    const val SPLIT = "SPLIT"
    const val ORGANISE = "ORGANISE"
    const val IMAGES_TO_PDF = "IMAGES_TO_PDF"
    const val PDF_TO_IMAGES = "PDF_TO_IMAGES"
    const val PASSWORD = "PASSWORD"
    const val WATERMARK = "WATERMARK"
    const val STAMP = "STAMP"
    const val COMPRESS = "COMPRESS"
    const val PAGE_NUMBERS = "PAGE_NUMBERS"
    const val OCR_PDF = "OCR_PDF"
    const val REDACT = "REDACT"
    const val TEXT_TO_PDF = "TEXT_TO_PDF"

    fun typeOf(op: DocOperation): String = when (op) {
        is DocOperation.Merge -> MERGE
        is DocOperation.Split -> SPLIT
        is DocOperation.Organise -> ORGANISE
        is DocOperation.ImagesToPdf -> IMAGES_TO_PDF
        is DocOperation.PdfToImages -> PDF_TO_IMAGES
        is DocOperation.SetPassword -> PASSWORD
        is DocOperation.Watermark -> WATERMARK
        is DocOperation.Stamp -> STAMP
        is DocOperation.Compress -> COMPRESS
        is DocOperation.PageNumbers -> PAGE_NUMBERS
        is DocOperation.OcrPdf -> OCR_PDF
        is DocOperation.Redact -> REDACT
        is DocOperation.TextToPdf -> TEXT_TO_PDF
    }

    /** The new password for SetPassword is part of the spec because it is the output, not a secret input. */
    fun encode(op: DocOperation): String = json.encodeToString(OperationSpec.serializer(), toSpec(op))

    fun decode(text: String): DocOperation = fromSpec(json.decodeFromString(OperationSpec.serializer(), text))

    private fun toSpec(op: DocOperation): OperationSpec = when (op) {
        is DocOperation.Merge -> OperationSpec(
            type = MERGE,
            sources = op.sources.map { it.uri.toString() },
            sourceRanges = op.sources.map { s -> s.pages?.map { PageRangeSpec.of(it) } },
        )
        is DocOperation.Split -> when (val mode = op.mode) {
            is SplitMode.ByRanges -> OperationSpec(type = SPLIT, sources = listOf(op.source.toString()), splitRanges = mode.ranges.map { PageRangeSpec.of(it) })
            is SplitMode.EveryN -> OperationSpec(type = SPLIT, sources = listOf(op.source.toString()), everyN = mode.pagesPerFile)
        }
        is DocOperation.Organise -> OperationSpec(
            type = ORGANISE,
            sources = listOf(op.source.toString()),
            plan = op.plan.pages.map { OperationSpec.PlanOp(it.sourceIndex, it.rotationDelta) },
        )
        is DocOperation.ImagesToPdf -> OperationSpec(
            type = IMAGES_TO_PDF,
            images = op.images.map { OperationSpec.ImageSpec(it.uri.toString(), it.rotationDegrees) },
            pageSize = op.pageSize.name,
            fit = op.fit.name,
            margin = op.margin.name,
            ocrLanguages = op.ocrLanguages,
        )
        is DocOperation.PdfToImages -> OperationSpec(
            type = PDF_TO_IMAGES,
            sources = listOf(op.source.toString()),
            pageRange = PageRangeSpec.of(op.pages),
            imageFormat = op.format.name,
            dpi = op.dpi,
            jpegQuality = op.jpegQuality,
        )
        is DocOperation.SetPassword -> OperationSpec(type = PASSWORD, sources = listOf(op.source.toString()), password = op.password)
        is DocOperation.Stamp -> OperationSpec(
            type = STAMP,
            sources = listOf(op.source.toString()),
            stampPage = op.pageIndex,
            stampImage = op.image.path,
            stampLeft = op.leftFrac,
            stampTop = op.topFrac,
            stampWidth = op.widthFrac,
        )
        is DocOperation.Compress -> OperationSpec(type = COMPRESS, sources = listOf(op.source.toString()), compressLevel = op.level.name)
        is DocOperation.PageNumbers -> OperationSpec(
            type = PAGE_NUMBERS,
            sources = listOf(op.source.toString()),
            numberPosition = op.position.name,
            numberStartAt = op.startAt,
            numberFormat = op.format,
        )
        is DocOperation.OcrPdf -> OperationSpec(type = OCR_PDF, sources = listOf(op.source.toString()), ocrLanguages = op.languages)
        is DocOperation.Redact -> OperationSpec(
            type = REDACT,
            sources = listOf(op.source.toString()),
            boxes = op.boxes.map { OperationSpec.BoxSpec(it.pageIndex, it.left, it.top, it.right, it.bottom) },
        )
        is DocOperation.Watermark -> OperationSpec(
            type = WATERMARK,
            sources = listOf(op.source.toString()),
            text = op.text,
            opacity = op.opts.opacity,
            rotation = op.opts.rotationDegrees,
            fontSize = op.opts.fontSizePt,
        )
        is DocOperation.TextToPdf -> OperationSpec(type = TEXT_TO_PDF, noteTitle = op.title, noteBody = op.body, noteFontSize = op.fontSizePt)
    }

    private fun fromSpec(s: OperationSpec): DocOperation = when (s.type) {
        MERGE -> DocOperation.Merge(
            s.sources.mapIndexed { i, uri -> MergeSource(Uri.parse(uri), s.sourceRanges.getOrNull(i)?.map { it.toKotlin() }) },
        )
        SPLIT -> DocOperation.Split(
            Uri.parse(s.sources.first()),
            if (s.everyN > 0) SplitMode.EveryN(s.everyN) else SplitMode.ByRanges(s.splitRanges.map { it.toKotlin() }),
        )
        ORGANISE -> DocOperation.Organise(Uri.parse(s.sources.first()), PagePlan(s.plan.map { PageOp(it.source, it.rotation) }))
        IMAGES_TO_PDF -> DocOperation.ImagesToPdf(
            images = s.images.map { ImageInput(Uri.parse(it.uri), it.rotation) },
            pageSize = PageSize.valueOf(s.pageSize),
            fit = Fit.valueOf(s.fit),
            margin = Margin.valueOf(s.margin),
            ocrLanguages = s.ocrLanguages,
        )
        PDF_TO_IMAGES -> DocOperation.PdfToImages(
            source = Uri.parse(s.sources.first()),
            pages = checkNotNull(s.pageRange).toKotlin(),
            format = ImageFormat.valueOf(s.imageFormat),
            dpi = s.dpi,
            jpegQuality = s.jpegQuality,
        )
        PASSWORD -> DocOperation.SetPassword(Uri.parse(s.sources.first()), s.password)
        STAMP -> DocOperation.Stamp(Uri.parse(s.sources.first()), s.stampPage, java.io.File(s.stampImage), s.stampLeft, s.stampTop, s.stampWidth)
        COMPRESS -> DocOperation.Compress(Uri.parse(s.sources.first()), CompressLevel.valueOf(s.compressLevel))
        PAGE_NUMBERS -> DocOperation.PageNumbers(
            Uri.parse(s.sources.first()),
            PageNumberPosition.valueOf(s.numberPosition),
            s.numberStartAt,
            s.numberFormat,
        )
        OCR_PDF -> DocOperation.OcrPdf(Uri.parse(s.sources.first()), s.ocrLanguages)
        REDACT -> DocOperation.Redact(
            Uri.parse(s.sources.first()),
            s.boxes.map { RedactionBox(it.page, it.left, it.top, it.right, it.bottom) },
        )
        WATERMARK -> DocOperation.Watermark(
            Uri.parse(s.sources.first()),
            s.text,
            WatermarkOptions(opacity = s.opacity, rotationDegrees = s.rotation, fontSizePt = s.fontSize),
        )
        TEXT_TO_PDF -> DocOperation.TextToPdf(s.noteTitle, s.noteBody, s.noteFontSize)
        else -> throw IllegalArgumentException("Unknown operation type ${s.type}")
    }
}
