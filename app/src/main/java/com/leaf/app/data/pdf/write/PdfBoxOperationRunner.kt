package com.leaf.app.data.pdf.write

import android.app.ActivityManager
import android.content.Context
import com.leaf.app.R
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.leaf.app.util.ocr.OcrEngine
import com.leaf.app.util.ocr.OcrPage
import com.leaf.app.util.ocr.TessdataStore
import com.leaf.app.util.saf.OutputNames
import com.leaf.app.util.scan.DocumentImageProcessor
import com.leaf.app.util.saf.SafAccess
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Android half of the write path. Copies sources into cacheDir/work, hands files to
 * [PdfBoxEngine], verifies the result, and only then streams it to the destination.
 * A crash or kill mid-way leaves the user's files untouched.
 */
class PdfBoxOperationRunner(
    private val context: Context,
    private val saf: SafAccess,
    private val outputPattern: () -> String,
) : DocOperationRunner {

    private val workDir: File get() = File(context.cacheDir, "work")

    private fun engine(): PdfBoxEngine = engineFor(context, workDir)

    override fun run(op: DocOperation, destination: Uri): Flow<OperationProgress> = run(op, destination, emptyMap())

    /** [passwords] keyed by source URI, gathered by the UI up front and never persisted. */
    fun run(op: DocOperation, destination: Uri, passwords: Map<Uri, String>): Flow<OperationProgress> = flow {
        val session = File(workDir, UUID.randomUUID().toString()).apply { mkdirs() }
        // Only a destination write failure leaves a verified temp file worth keeping for a retry.
        var keepSession = false
        try {
            emit(OperationProgress.Working(null, "Preparing"))
            PdfBoxInitializer.ensure(context)
            when (op) {
                is DocOperation.Merge -> mergeOp(op, destination, passwords, session)
                is DocOperation.Organise -> singleFileOp(op.source, destination, passwords, session) { engine, src, out ->
                    engine.organise(src, passwords[op.source], op.plan, out)
                }
                is DocOperation.SetPassword -> singleFileOp(op.source, destination, passwords, session, verifyPassword = op.password) { engine, src, out ->
                    engine.setPassword(src, op.currentPassword ?: passwords[op.source], op.password, out)
                }
                is DocOperation.Stamp -> singleFileOp(op.source, destination, passwords, session) { engine, src, out ->
                    engine.stamp(src, passwords[op.source], op.pageIndex, op.image, op.leftFrac, op.topFrac, op.widthFrac, out)
                }
                is DocOperation.Watermark -> singleFileOp(op.source, destination, passwords, session) { engine, src, out ->
                    engine.watermark(src, passwords[op.source], op.text, op.opts, out)
                }
                is DocOperation.Compress -> singleFileOp(op.source, destination, passwords, session) { engine, src, out ->
                    engine.compress(src, passwords[op.source], op.level, out)
                }
                is DocOperation.PageNumbers -> singleFileOp(op.source, destination, passwords, session) { engine, src, out ->
                    engine.pageNumbers(src, passwords[op.source], op.position, op.startAt, op.format, out)
                }
                is DocOperation.Split -> splitOp(op, destination, passwords, session)
                is DocOperation.PdfToImages -> pdfToImagesOp(op, destination, session)
                is DocOperation.ImagesToPdf -> imagesToPdfOp(op, destination, session)
                is DocOperation.OcrPdf -> ocrPdfOp(op, destination, passwords, session)
                is DocOperation.Redact -> redactOp(op, destination, passwords, session)
                is DocOperation.TextToPdf -> textToPdfOp(op, destination, session)
            }
        } catch (e: DocOperationException) {
            if (e.error is OperationError.DestinationWriteFailed) keepSession = true
            emit(OperationProgress.Failed(e.error))
        } catch (e: OutOfMemoryError) {
            emit(OperationProgress.Failed(OperationError.OutOfMemory))
        } catch (e: SecurityException) {
            emit(OperationProgress.Failed(OperationError.Unknown(e.message)))
        } catch (e: FileNotFoundException) {
            emit(OperationProgress.Failed(OperationError.Corrupt))
        } catch (e: IOException) {
            emit(OperationProgress.Failed(OperationError.Unknown(e.message)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // PdfBox throws IllegalArgument, ClassCast and NPE on malformed files; none may escape.
            emit(OperationProgress.Failed(OperationError.Unknown(e.message)))
        } finally {
            if (!keepSession) session.deleteRecursively()
            // The drawn signature is only needed for this one run.
            if (op is DocOperation.Stamp) op.image.delete()
        }
    }.flowOn(Dispatchers.IO)

    /** Retry the commit step with a different destination after a write failure. */
    fun commitAgain(temp: File, destination: Uri, pages: Int): Flow<OperationProgress> = flow {
        emit(OperationProgress.Working(null, "Saving"))
        try {
            saf.commit(temp, destination)
        } catch (e: IOException) {
            emit(OperationProgress.Failed(OperationError.DestinationWriteFailed(temp, pages)))
            return@flow
        }
        emit(OperationProgress.Done(listOf(describe(destination, pages))))
        temp.parentFile?.deleteRecursively()
    }.flowOn(Dispatchers.IO)

    /** Remove work files older than [maxAgeMs]. Called on app start. */
    fun cleanWorkDir(maxAgeMs: Long = 24L * 60 * 60 * 1000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        workDir.listFiles()?.forEach { f -> if (f.lastModified() < cutoff) f.deleteRecursively() }
    }

    // ---- Single-source operations ----

    private suspend fun FlowCollector<OperationProgress>.singleFileOp(
        source: Uri,
        destination: Uri,
        passwords: Map<Uri, String>,
        session: File,
        verifyPassword: String? = null,
        block: (PdfBoxEngine, File, File) -> Int,
    ) {
        emit(OperationProgress.Working(0.1f, "Reading document"))
        val src = copyIn(source, session, "source.pdf")
        val out = File(session, "keep-output.pdf")
        emit(OperationProgress.Working(0.4f, "Writing"))
        val engine = engine()
        val pages = block(engine, src, out)
        currentCoroutineContext().ensureActive()
        emit(OperationProgress.Working(0.8f, "Verifying"))
        engine.verify(out, pages, verifyPassword ?: passwords[source])
        emit(OperationProgress.Working(0.9f, "Saving"))
        commit(out, destination, pages, restore = src.takeIf { destination == source })
    }

    private suspend fun FlowCollector<OperationProgress>.mergeOp(
        op: DocOperation.Merge,
        destination: Uri,
        passwords: Map<Uri, String>,
        session: File,
    ) {
        val inputs = ArrayList<PdfBoxEngine.MergeInput>()
        op.sources.forEachIndexed { i, s ->
            emit(OperationProgress.Working(0.1f + 0.3f * i / op.sources.size, "Reading document ${i + 1} of ${op.sources.size}"))
            inputs += PdfBoxEngine.MergeInput(copyIn(s.uri, session, "source_$i.pdf"), passwords[s.uri], s.pages)
        }
        val out = File(session, "keep-output.pdf")
        emit(OperationProgress.Working(0.5f, "Merging"))
        val engine = engine()
        val pages = engine.merge(inputs, out)
        emit(OperationProgress.Working(0.85f, "Verifying"))
        engine.verify(out, pages)
        emit(OperationProgress.Working(0.95f, "Saving"))
        val overwritten = op.sources.indexOfFirst { it.uri == destination }
        commit(out, destination, pages, restore = inputs.getOrNull(overwritten)?.file)
    }

    private suspend fun FlowCollector<OperationProgress>.splitOp(
        op: DocOperation.Split,
        tree: Uri,
        passwords: Map<Uri, String>,
        session: File,
    ) {
        emit(OperationProgress.Working(0.1f, "Reading document"))
        val src = copyIn(op.source, session, "source.pdf")
        val engine = engine()
        val password = passwords[op.source]
        val pageCount = engine.pageCount(src, password)
        val ranges = when (val mode = op.mode) {
            is SplitMode.ByRanges -> mode.ranges
            is SplitMode.EveryN -> (0 until pageCount step mode.pagesPerFile).map { start -> start..minOf(start + mode.pagesPerFile - 1, pageCount - 1) }
        }
        val baseName = sourceName(op.source)
        emit(OperationProgress.Working(0.3f, "Splitting into ${ranges.size} files"))
        val files = engine.split(src, password, ranges, File(session, "parts")) { r ->
            OutputNames.build(outputPattern(), baseName, OutputNames.pagesOp(listOf(r)))
        }
        val outputs = ArrayList<OperationOutput>()
        files.forEachIndexed { i, file ->
            emit(OperationProgress.Working(0.5f + 0.5f * i / files.size, "Saving ${file.name}"))
            engine.verify(file, ranges[i].last - ranges[i].first + 1, password)
            val child = saf.createChild(tree, SafAccess.PDF_MIME, file.name)
                ?: throw DocOperationException(OperationError.DestinationWriteFailed(file))
            try {
                saf.commit(file, child)
            } catch (e: IOException) {
                throw DocOperationException(OperationError.DestinationWriteFailed(file), e)
            }
            outputs += describe(child, ranges[i].last - ranges[i].first + 1, file.length())
        }
        emit(OperationProgress.Done(outputs))
    }

    // ---- Raster operations use the framework renderer, not PdfBox ----

    private suspend fun FlowCollector<OperationProgress>.pdfToImagesOp(op: DocOperation.PdfToImages, tree: Uri, session: File) {
        emit(OperationProgress.Working(0.05f, "Reading document"))
        val src = copyIn(op.source, session, "source.pdf")
        val baseName = sourceName(op.source)
        val outputs = ArrayList<OperationOutput>()
        val pfd = ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: SecurityException) {
            pfd.close()
            throw DocOperationException(OperationError.PasswordRequired, e)
        } catch (e: IOException) {
            pfd.close()
            throw DocOperationException(OperationError.Corrupt, e)
        }
        try {
            val pages = op.pages.first.coerceAtLeast(0)..op.pages.last.coerceAtMost(renderer.pageCount - 1)
            if (pages.isEmpty()) throw DocOperationException(OperationError.Unknown("No pages in range"))
            val total = pages.last - pages.first + 1
            val ext = if (op.format == ImageFormat.PNG) "png" else "jpg"
            val mime = if (op.format == ImageFormat.PNG) "image/png" else "image/jpeg"
            for ((i, index) in pages.withIndex()) {
                currentCoroutineContext().ensureActive()
                emit(OperationProgress.Working(i.toFloat() / total, "Rendering page ${index + 1} of ${renderer.pageCount}"))
                val file = File(session, "page_$index.$ext")
                renderer.openPage(index).use { page ->
                    val width = (page.width * op.dpi / 72f).toInt().coerceIn(1, MAX_RASTER_EDGE)
                    val height = (page.height * op.dpi / 72f).toInt().coerceIn(1, MAX_RASTER_EDGE)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    file.outputStream().use { out ->
                        if (op.format == ImageFormat.PNG) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        else bitmap.compress(Bitmap.CompressFormat.JPEG, op.jpegQuality.coerceIn(30, 100), out)
                    }
                    bitmap.recycle()
                }
                val name = OutputNames.build(outputPattern(), baseName, "page_${OutputNames.pageNumberLabel(index + 1, renderer.pageCount)}", ext)
                val child = saf.createChild(tree, mime, name) ?: throw DocOperationException(OperationError.DestinationWriteFailed(file))
                try {
                    saf.commit(file, child)
                } catch (e: IOException) {
                    throw DocOperationException(OperationError.DestinationWriteFailed(file), e)
                }
                outputs += OperationOutput(child, name, 1, file.length())
                file.delete()
            }
        } finally {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
        emit(OperationProgress.Done(outputs))
    }

    private suspend fun FlowCollector<OperationProgress>.imagesToPdfOp(op: DocOperation.ImagesToPdf, destination: Uri, session: File) {
        if (op.images.isEmpty()) throw DocOperationException(OperationError.Unknown("No images"))
        val out = File(session, "keep-output.pdf")
        val engine = engine()
        val ocr = if (op.ocrLanguages.isEmpty()) null else openOcrEngine(op.ocrLanguages)
        val doc = PDDocument()
        val textLayer = if (ocr == null) null else InvisibleTextLayer(doc)
        try {
            val marginPt = when (op.margin) {
                Margin.NONE -> 0f
                Margin.SMALL -> 18f
                Margin.MEDIUM -> 36f
            }
            val n = op.images.size
            // Recognition dominates the time when it is on, so it takes most of the bar.
            val perImage = 0.8f / n
            val drawShare = if (ocr == null) 1f else 0.15f
            op.images.forEachIndexed { i, image ->
                currentCoroutineContext().ensureActive()
                emit(OperationProgress.Working(perImage * i, "Adding image ${i + 1} of $n"))
                val bitmap = decodeDownscaled(image.uri, image.rotationDegrees)
                    ?: throw DocOperationException(OperationError.Unknown("Image ${i + 1} could not be read"))
                try {
                    val pageRect = when (op.pageSize) {
                        PageSize.FIT_TO_IMAGE -> ptPerPx(bitmap).let { pt -> PDRectangle(bitmap.width * pt + marginPt * 2, bitmap.height * pt + marginPt * 2) }
                        PageSize.A4 -> PDRectangle.A4
                        PageSize.LETTER -> PDRectangle.LETTER
                    }
                    val page = PDPage(pageRect)
                    doc.addPage(page)
                    val pdImage = if (bitmap.hasAlpha()) LosslessFactory.createFromImage(doc, bitmap) else JPEGFactory.createFromImage(doc, bitmap, 0.88f)
                    val innerW = pageRect.width - marginPt * 2
                    val innerH = pageRect.height - marginPt * 2
                    val scale = when (op.fit) {
                        Fit.FIT -> minOf(innerW / bitmap.width, innerH / bitmap.height)
                        Fit.FILL -> maxOf(innerW / bitmap.width, innerH / bitmap.height)
                    }
                    val drawW = bitmap.width * scale
                    val drawH = bitmap.height * scale
                    val x = marginPt + (innerW - drawW) / 2f
                    val y = marginPt + (innerH - drawH) / 2f
                    PDPageContentStream(doc, page).use { cs ->
                        if (op.fit == Fit.FILL) {
                            cs.addRect(marginPt, marginPt, innerW, innerH)
                            cs.clip()
                        }
                        cs.drawImage(pdImage, x, y, drawW, drawH)
                    }
                    if (ocr != null && textLayer != null) {
                        currentCoroutineContext().ensureActive()
                        emit(
                            OperationProgress.Working(
                                perImage * (i + drawShare),
                                "Recognising text, page ${i + 1} of $n",
                                OperationProgress.Recognising(i + 1, n),
                            ),
                        )
                        val recognised = recogniseCancellable(ocr, bitmap)
                        textLayer.write(page, recognised, x, y, drawW / bitmap.width)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            emit(OperationProgress.Working(0.85f, "Writing"))
            doc.save(out)
        } finally {
            runCatching { doc.close() }
            ocr?.close()
        }
        emit(OperationProgress.Working(0.9f, "Verifying"))
        engine.verify(out, op.images.size)
        commit(out, destination, op.images.size, restore = null)
    }

    /** A note has no source to copy in: the engine writes the pages straight into the session. */
    private suspend fun FlowCollector<OperationProgress>.textToPdfOp(op: DocOperation.TextToPdf, destination: Uri, session: File) {
        if (op.title.isBlank() && op.body.isBlank()) throw DocOperationException(OperationError.Unknown("The note is empty"))
        val out = File(session, "keep-output.pdf")
        emit(OperationProgress.Working(0.3f, "Writing"))
        val engine = engine()
        val pages = engine.textToPdf(op.title, op.body, op.fontSizePt, { context.assets.open(NOTE_FONT_ASSET) }, out)
        currentCoroutineContext().ensureActive()
        emit(OperationProgress.Working(0.8f, "Verifying"))
        engine.verify(out, pages)
        emit(OperationProgress.Working(0.9f, "Saving"))
        commit(out, destination, pages, restore = null)
    }

    /**
     * Adds an invisible text layer to every page that has none. The page is rendered with
     * the framework renderer as the reader would show it, recognised, and the words are
     * written back over the original content, which is never replaced. Pages that already
     * carry text are left alone and counted.
     */
    private suspend fun FlowCollector<OperationProgress>.ocrPdfOp(
        op: DocOperation.OcrPdf,
        destination: Uri,
        passwords: Map<Uri, String>,
        session: File,
    ) {
        if (op.languages.isEmpty()) throw DocOperationException(OperationError.Unknown("No recognition language"))
        emit(OperationProgress.Working(0.05f, "Reading document"))
        val src = copyIn(op.source, session, "source.pdf")
        val out = File(session, "keep-output.pdf")
        val engine = engine()
        val password = passwords[op.source]
        val renderSrc = renderableCopy(engine, src, password, session)
        val doc = engine.open(src, password)
        val total = doc.numberOfPages
        var ocr: OcrEngine? = null
        var renderer: PdfRenderer? = null
        var pfd: ParcelFileDescriptor? = null
        try {
            val perms = doc.currentAccessPermission
            if (!perms.isOwnerPermission && !perms.canModify()) throw DocOperationException(OperationError.AssemblyRestricted)
            if (total == 0) throw DocOperationException(OperationError.Unknown("The document has no pages"))
            val (fd, pages) = openRenderer(renderSrc)
            pfd = fd
            renderer = pages
            if (pages.pageCount != total) throw DocOperationException(OperationError.Corrupt)
            val recogniser = openOcrEngine(op.languages)
            ocr = recogniser
            val layer = InvisibleTextLayer(doc)
            val stripper = PDFTextStripper()
            var skipped = 0
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val page = doc.getPage(i)
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val existing = runCatching { stripper.getText(doc) }.getOrDefault("")
                if (existing.count { !it.isWhitespace() } > TEXT_PAGE_THRESHOLD) {
                    skipped++
                    emit(OperationProgress.Working(0.05f + 0.8f * (i + 1) / total, "Page ${i + 1} of $total already has text, skipped $skipped"))
                    continue
                }
                emit(
                    OperationProgress.Working(
                        0.05f + 0.8f * i / total,
                        "Recognising text, page ${i + 1} of $total",
                        OperationProgress.Recognising(i + 1, total),
                    ),
                )
                val bitmap = pages.openPage(i).use { rendered -> renderPage(rendered, OCR_DPI) }
                try {
                    val recognised = recogniseCancellable(recogniser, bitmap)
                    if (recognised.isEmpty) continue
                    // The bitmap is the page as displayed: the crop box, turned by the page rotation.
                    val box = page.cropBox ?: page.mediaBox
                    val rot = ((page.rotation % 360) + 360) % 360
                    val displayW = if (rot == 90 || rot == 270) box.height else box.width
                    if (rot != 0) appendContent(doc, page, "q ${rotationMatrix(rot, box.width, box.height)} cm\n")
                    layer.write(page, recognised, imageLeftPt = box.lowerLeftX, imageBottomPt = box.lowerLeftY, ptPerPx = displayW / bitmap.width)
                    if (rot != 0) appendContent(doc, page, "Q\n")
                } finally {
                    bitmap.recycle()
                }
            }
            currentCoroutineContext().ensureActive()
            emit(OperationProgress.Working(0.88f, if (skipped > 0) "Writing, $skipped of $total pages already had text" else "Writing"))
            if (doc.isEncrypted) engine.keepProtection(doc, password)
            doc.save(out)
        } finally {
            runCatching { doc.close() }
            ocr?.close()
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
        emit(OperationProgress.Working(0.92f, "Verifying"))
        engine.verify(out, total, password)
        emit(OperationProgress.Working(0.96f, "Saving"))
        commit(out, destination, total, restore = null)
    }

    /**
     * Pages with boxes are rendered as displayed, the boxes painted solid black on the
     * bitmap, and the engine swaps each such page for one that shows only that picture.
     * Nothing of the old page survives; the other pages are untouched.
     */
    private suspend fun FlowCollector<OperationProgress>.redactOp(
        op: DocOperation.Redact,
        destination: Uri,
        passwords: Map<Uri, String>,
        session: File,
    ) {
        if (op.boxes.isEmpty()) throw DocOperationException(OperationError.Unknown("No redaction boxes"))
        emit(OperationProgress.Working(0.05f, "Reading document"))
        val src = copyIn(op.source, session, "source.pdf")
        val engine = engine()
        val password = passwords[op.source]
        val renderSrc = renderableCopy(engine, src, password, session)
        val byPage = op.boxes.groupBy { it.pageIndex }.toSortedMap()
        val images = HashMap<Int, File>()
        val (pfd, renderer) = openRenderer(renderSrc)
        try {
            for ((k, entry) in byPage.entries.withIndex()) {
                val (index, boxes) = entry
                currentCoroutineContext().ensureActive()
                emit(OperationProgress.Working(0.1f + 0.5f * k / byPage.size, "Redacting page ${index + 1}"))
                if (index !in 0 until renderer.pageCount) throw DocOperationException(OperationError.Unknown("Page ${index + 1} does not exist"))
                val file = File(session, "redacted_$index.jpg")
                renderer.openPage(index).use { rendered ->
                    val bitmap = renderPage(rendered, REDACT_DPI)
                    try {
                        val canvas = Canvas(bitmap)
                        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
                        val w = bitmap.width.toFloat()
                        val h = bitmap.height.toFloat()
                        for (b in boxes) {
                            // Grow to whole pixels so no sliver of the edge shows through.
                            canvas.drawRect(floor(b.left * w), floor(b.top * h), ceil(b.right * w), ceil(b.bottom * h), paint)
                        }
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, REDACT_JPEG_QUALITY, it) }
                    } finally {
                        bitmap.recycle()
                    }
                }
                images[index] = file
            }
        } finally {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
        currentCoroutineContext().ensureActive()
        emit(OperationProgress.Working(0.65f, "Writing"))
        val out = File(session, "keep-output.pdf")
        val pages = engine.redact(src, password, images, out)
        currentCoroutineContext().ensureActive()
        emit(OperationProgress.Working(0.85f, "Verifying"))
        engine.verify(out, pages, password)
        emit(OperationProgress.Working(0.95f, "Saving"))
        commit(out, destination, pages, restore = null)
    }

    // ---- Page rendering for OCR and redaction ----

    /**
     * The framework renderer cannot open a protected file, so an encrypted source gets a
     * copy without its encryption, inside the session directory, used for rendering only.
     * The output is written from the original and keeps its protection.
     */
    private fun renderableCopy(engine: PdfBoxEngine, src: File, password: String?, session: File): File {
        engine.open(src, password).use { doc ->
            if (!doc.isEncrypted) return src
            val plain = File(session, "render.pdf")
            doc.isAllSecurityToBeRemoved = true
            try {
                doc.save(plain)
            } catch (e: IOException) {
                throw DocOperationException(OperationError.Unknown(e.message), e)
            }
            return plain
        }
    }

    private fun openRenderer(file: File): Pair<ParcelFileDescriptor, PdfRenderer> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: SecurityException) {
            pfd.close()
            throw DocOperationException(OperationError.PasswordRequired, e)
        } catch (e: IOException) {
            pfd.close()
            throw DocOperationException(OperationError.Corrupt, e)
        }
        return pfd to renderer
    }

    private fun renderPage(page: PdfRenderer.Page, dpi: Int): Bitmap = renderPageBitmap(page, dpi)

    /** The `cm` operands that map display space onto a page rotated by [rot], as stamp() does. */
    private fun rotationMatrix(rot: Int, w: Float, h: Float): String = when (rot) {
        90 -> "0 1 -1 0 ${fmt(w)} 0"
        180 -> "-1 0 0 -1 ${fmt(w)} ${fmt(h)}"
        270 -> "0 -1 1 0 0 ${fmt(h)}"
        else -> "1 0 0 1 0 0"
    }

    private fun fmt(v: Float): String = String.format(java.util.Locale.ROOT, "%.3f", v).trimEnd('0').trimEnd('.')

    /** Appends a small content stream to [page]; used to wrap the text layer in the page rotation. */
    private fun appendContent(doc: PDDocument, page: PDPage, content: String) {
        val stream = PDStream(doc)
        stream.createOutputStream().use { it.write(content.toByteArray(Charsets.US_ASCII)) }
        page.setContents(page.contentStreams.asSequence().toList() + stream)
    }

    // ---- OCR ----

    private fun openOcrEngine(languages: List<String>): OcrEngine {
        try {
            TessdataStore(context).ensureBundled()
            return OcrEngine(File(context.filesDir, "ocr"), languages)
        } catch (e: IOException) {
            throw DocOperationException(OperationError.Unknown("Text recognition is not available: ${e.message}"), e)
        } catch (e: UnsatisfiedLinkError) {
            throw DocOperationException(OperationError.Unknown("Text recognition is not available on this device: ${e.message}"), e)
        }
    }

    /**
     * Tesseract blocks the thread it runs on, so recognition happens in a child coroutine
     * while this one stays suspended and cancellable; a cancellation stops the engine, which
     * makes the child return promptly.
     */
    private suspend fun recogniseCancellable(engine: OcrEngine, bitmap: Bitmap): OcrPage = coroutineScope {
        val work = async(Dispatchers.IO) { engine.recognise(bitmap) }
        try {
            work.await()
        } catch (e: CancellationException) {
            engine.cancel()
            throw e
        }
    }

    // ---- Helpers ----

    /**
     * [restore] is the session copy of the source when the destination is the source itself
     * (Organise "Overwrite"): "wt" truncates before streaming, so a failure mid-copy would
     * otherwise leave the user with an empty original.
     */
    private suspend fun FlowCollector<OperationProgress>.commit(temp: File, destination: Uri, pages: Int, restore: File?) {
        try {
            saf.commit(temp, destination)
        } catch (e: IOException) {
            if (restore != null) runCatching { saf.commit(restore, destination) }
            // Keep the temp file: the caller can offer "Try a different location".
            throw DocOperationException(OperationError.DestinationWriteFailed(temp, pages), e)
        }
        val size = temp.length()
        temp.delete()
        emit(OperationProgress.Done(listOf(describe(destination, pages, size))))
    }

    private fun describe(uri: Uri, pages: Int, size: Long? = null): OperationOutput {
        val info = saf.queryInfo(uri)
        return OperationOutput(uri, info?.displayName ?: uri.lastPathSegment ?: "output.pdf", pages, size ?: info?.sizeBytes ?: 0L)
    }

    private fun sourceName(uri: Uri): String = saf.queryInfo(uri)?.displayName ?: context.getString(R.string.document_fallback_name)

    private fun copyIn(uri: Uri, session: File, name: String): File {
        val target = File(session, name)
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: SecurityException) {
            throw DocOperationException(OperationError.Unknown("No permission to read the source"), e)
        } ?: throw DocOperationException(OperationError.Corrupt)
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        return target
    }

    /** Long edge capped at [MAX_IMAGE_EDGE] before embedding, with EXIF orientation applied. */
    private fun decodeDownscaled(uri: Uri, rotationDegrees: Int): Bitmap? {
        val decoded = try {
            DocumentImageProcessor(context.contentResolver).decode(uri, minOf(MAX_IMAGE_EDGE, DocumentImageProcessor.adaptiveMaxEdge()))
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        if (rotationDegrees == 0) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    companion object {
        const val MAX_IMAGE_EDGE = 3000
        const val MAX_RASTER_EDGE = 6000
        /** Make searchable renders at this, capped by the device budget. */
        const val OCR_DPI = 300
        /** Redacted pages become pictures at this resolution, enough for normal print to stay crisp. */
        const val REDACT_DPI = 200
        const val REDACT_JPEG_QUALITY = 90
        /** A page with more non-space characters than this already has real text and is not recognised. */
        const val TEXT_PAGE_THRESHOLD = 20
        /** Write a note embeds this font: DejaVu Sans covers Latin, Cyrillic, Greek and more. */
        const val NOTE_FONT_ASSET = "fonts/DejaVuSans.ttf"
        /** Treat image pixels as 150 dpi when sizing a page to the image. */
        /** Images are placed at 150 dpi, or denser so that a large scan still fits an A4-length page. */
        const val PT_PER_PX = 72f / 150f
        private const val A4_LONG_EDGE_PT = 842f

        fun ptPerPx(bitmap: Bitmap): Float {
            val longEdge = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
            return minOf(PT_PER_PX, A4_LONG_EDGE_PT / longEdge)
        }

        /** An engine sized for this device: a quarter of the app's heap class stays in memory, the rest spills to [workDir]/scratch. */
        fun engineFor(context: Context, workDir: File): PdfBoxEngine {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val budget = am.memoryClass.toLong() * 1024L * 1024L / 4L
            return PdfBoxEngine(maxMainMemoryBytes = budget.coerceAtLeast(16L * 1024 * 1024), scratchDir = File(workDir, "scratch"))
        }

        /** Renders a page on white at [dpi], with the long edge capped to what this device can afford. */
        fun renderPageBitmap(page: PdfRenderer.Page, dpi: Int): Bitmap {
            val cap = DocumentImageProcessor.adaptiveMaxEdge()
            val longEdgePt = maxOf(page.width, page.height).coerceAtLeast(1)
            val scale = minOf(dpi / 72f, cap.toFloat() / longEdgePt)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            return bitmap
        }
    }
}
