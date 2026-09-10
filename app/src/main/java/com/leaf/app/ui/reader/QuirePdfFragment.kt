package com.leaf.app.ui.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Size
import android.util.SparseArray
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.util.forEach
import androidx.pdf.EditablePdfDocument
import androidx.pdf.ExperimentalPdfApi
import android.os.ParcelFileDescriptor
import com.leaf.app.util.PdfFeatures
import java.io.File
import androidx.pdf.Highlight
import androidx.pdf.PdfDocument
import androidx.pdf.PdfFeature
import androidx.pdf.PdfPoint
import androidx.pdf.PdfRect
import androidx.pdf.PdfWriteHandle
import androidx.pdf.ink.EditablePdfViewerFragment
import androidx.pdf.ink.model.ApplyInProgressException
import androidx.pdf.models.FormEditInfo
import androidx.pdf.view.PdfView
import androidx.lifecycle.lifecycleScope
import com.leaf.app.data.pdf.read.SearchMatch
import com.leaf.app.data.prefs.PageDisplayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The only class that subclasses the androidx viewer. It forwards lifecycle events to
 * [ReaderHost] and implements [PdfController]. Keep library-specific code in here.
 *
 * The superclass is the ink-capable viewer. Its drawing journey needs Android 12 with PDF
 * extension 18, but the class itself runs on every supported device as a plain viewer:
 * nothing here flips [isEditModeEnabled] unless [PdfFeatures.annotationsAvailable] holds.
 *
 * NewApi is suppressed for exactly that reason: lint sees a subclass of an extension-18
 * class and cannot see the runtime gate. Every extension-dependent call in here still
 * checks [PdfFeatures] first; keep it that way when adding to this class.
 */
@SuppressLint("NewApi")
@OptIn(ExperimentalPdfApi::class)
class QuirePdfFragment : EditablePdfViewerFragment(), PdfController {

    var host: ReaderHost? = null

    private var pdfViewRef: PdfView? = null
    private var document: PdfDocument? = null
    private var pendingRestore: ReadingPosition? = null
    private var requestedUri: Uri? = null
    private var loaded = false
    private var displayMode: PageDisplayMode = PageDisplayMode.NORMAL

    override val isDocumentLoaded: Boolean get() = loaded

    /** Idempotent: safe to call on every recomposition. */
    fun ensureDocument(uri: Uri) {
        if (requestedUri == uri || documentUri == uri) return
        requestedUri = uri
        loaded = false
        documentUri = uri
    }

    override fun onPdfViewCreated(pdfView: PdfView) {
        super.onPdfViewCreated(pdfView)
        pdfViewRef = pdfView
        pdfView.addOnViewportChangedListener(viewportListener)
        pdfView.addOnFirstContentLoadListener(firstContentListener)
        pdfView.addOnFormWidgetInfoUpdatedListener(formEditListener)
        // Replace the library's tap handling: any single tap that is not on a link or a
        // form field toggles the chrome. The library still gets the event for links and fields.
        // While drawing, taps belong to the ink layer and the chrome stays put.
        val detector = GestureDetector(
            pdfView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!annotating) handleSingleTap(pdfView, e.x, e.y)
                    return false
                }
            },
        )
        pdfView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
        applyDisplayMode(pdfView)
    }

    private fun handleSingleTap(view: PdfView, x: Float, y: Float) {
        val doc = document
        val point = view.viewToPdfPoint(x, y)
        if (doc == null || point == null) {
            host?.onCentreTap()
            return
        }
        val formFilling = view.isFormFillingEnabled
        viewLifecycleOwner.lifecycleScope.launch {
            val hit = withContext(Dispatchers.IO) { hitsLinkOrField(doc, point, formFilling) }
            if (!hit) host?.onCentreTap()
        }
    }

    /** Page-space hit test against the links on the page and, while filling, its form widgets. */
    private suspend fun hitsLinkOrField(doc: PdfDocument, point: PdfPoint, formFilling: Boolean): Boolean = try {
        // Link and widget lookups need the PDF extension; older phones just toggle the chrome.
        if (!PdfFeatures.formEditingAvailable) return false
        val links = doc.getPageLinks(point.pageNum)
        val inLink = links.gotoLinks.any { link -> link.bounds.any { it.contains(point.x, point.y) } } ||
            links.externalLinks.any { link -> link.bounds.any { it.contains(point.x, point.y) } }
        when {
            inLink -> true
            formFilling -> doc.getFormWidgetInfos(point.pageNum).any { it.widgetRect.contains(point.x.toInt(), point.y.toInt()) }
            else -> false
        }
    } catch (_: IOException) {
        false
    } catch (_: IllegalStateException) {
        false
    } catch (_: UnsupportedOperationException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        this.document = document
        loaded = true
        pendingRestore = host?.onDocumentLoaded(document.pageCount)
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        loaded = false
        document = null
        host?.onLoadError(error)
    }

    override fun onRequestImmersiveMode(enterImmersive: Boolean) {
        // Deliberately not calling super: it would show the library's edit button, and the
        // way into drawing is the Tools sheet. Chrome is ours.
        isToolboxVisible = false
        host?.onImmersiveModeRequested(enterImmersive)
    }

    override fun onResume() {
        super.onResume()
        isToolboxVisible = false
    }

    override fun scrollToPage(pageIndex: Int) {
        val view = pdfViewRef ?: return
        val count = view.pdfDocument?.pageCount ?: return
        view.scrollToPage(pageIndex.coerceIn(0, count - 1))
    }

    override fun scrollToMatch(match: SearchMatch) {
        val view = pdfViewRef ?: return
        val rect = match.bounds.firstOrNull() ?: return
        view.scrollToPosition(PdfPoint(match.pageIndex, rect.centerX(), rect.centerY()))
    }

    override suspend fun search(query: String): List<SearchMatch> {
        val doc = document ?: return emptyList()
        if (query.isBlank() || doc.pageCount == 0) return emptyList()
        val results = try {
            doc.searchDocument(query, 0 until doc.pageCount)
        } catch (_: IOException) {
            return emptyList()
        } catch (_: IllegalStateException) {
            return emptyList()
        }
        val matches = ArrayList<SearchMatch>()
        results.forEach { page, bounds ->
            bounds.forEach { b -> matches.add(SearchMatch(page, b.bounds, b.textStartIndex)) }
        }
        matches.sortWith(compareBy({ it.pageIndex }, { it.textStartIndex }))
        return matches
    }

    override fun highlight(matches: List<SearchMatch>, currentIndex: Int, color: Int, currentColor: Int) {
        val view = pdfViewRef ?: return
        if (view.pdfDocument == null) return
        val highlights = ArrayList<Highlight>()
        matches.forEachIndexed { index, match ->
            val c = if (index == currentIndex) currentColor else color
            match.bounds.forEach { r: RectF ->
                highlights.add(Highlight(PdfRect(match.pageIndex, r.left, r.top, r.right, r.bottom), c))
            }
        }
        view.setHighlights(highlights)
    }

    override suspend fun renderPage(pageIndex: Int, widthPx: Int): Bitmap? {
        val doc = document ?: return null
        if (pageIndex !in 0 until doc.pageCount) return null
        return withContext(Dispatchers.IO) {
            try {
                val info = doc.getPageInfo(pageIndex)
                val width = widthPx.coerceAtLeast(1)
                val height = (width.toFloat() * info.height / info.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                doc.getPageBitmapSource(pageIndex).use { it.getBitmap(Size(width, height)) }
            } catch (_: IOException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        }
    }

    override val hasForm: Boolean
        get() = PdfFeatures.formEditingAvailable &&
            document?.formType == PdfDocument.PDF_FORM_TYPE_ACRO_FORM &&
            document is EditablePdfDocument

    override fun setFormFilling(enabled: Boolean) {
        pdfViewRef?.isFormFillingEnabled = enabled && hasForm
        if (enabled) formEdited = false
    }

    private var formEdited = false
    override val hasFormEdits: Boolean get() = formEdited

    private val formEditListener = object : PdfView.OnFormWidgetInfoUpdatedListener {
        override fun onFormWidgetInfoUpdated(formEditInfo: FormEditInfo) {
            formEdited = true
        }
    }

    override suspend fun writeEditedCopy(target: File): Boolean {
        val editable = document as? EditablePdfDocument ?: return false
        if (!PdfFeatures.formEditingAvailable) return false
        return writeThrough(target) { pfd -> editable.createWriteHandle().use { it.writeTo(pfd) } }
    }

    // ---- Ink and highlight annotations ----

    /** Guarded read of the library flag: never consulted on devices without the extension. */
    private val annotating: Boolean
        get() = PdfFeatures.annotationsAvailable && isEditModeEnabled

    // isFeatureSupported is restricted to the androidx.pdf library group in beta01, but it is
    // the only way to learn before the user draws whether applyDraftEdits() will refuse; the
    // alternative is letting someone draw for ten minutes and then failing the save.
    @get:SuppressLint("RestrictedApi")
    override val hasAnnotations: Boolean
        get() = PdfFeatures.annotationsAvailable &&
            document?.isFeatureSupported(PdfFeature.ANNOTATIONS) == true

    override fun setAnnotating(enabled: Boolean) {
        // The gate the whole feature rests on: the ink journey must never start on a device
        // whose PDF module cannot write annotations back.
        if (!PdfFeatures.annotationsAvailable) return
        if (enabled && !hasAnnotations) return
        if (isEditModeEnabled != enabled) isEditModeEnabled = enabled
        isToolboxVisible = false
    }

    override val hasAnnotationEdits: Boolean
        get() = PdfFeatures.annotationsAvailable && hasUnsavedChanges

    /** Outstanding [applyDraftEdits] call; the library answers through the two hooks below. */
    private var pendingApply: CompletableDeferred<PdfWriteHandle>? = null

    override fun onApplyEditsSuccess(handle: PdfWriteHandle) {
        super.onApplyEditsSuccess(handle)
        val waiting = pendingApply
        pendingApply = null
        // The apply status is a state flow, so a stale success can be replayed after the
        // view is recreated. Nobody is waiting for that one; do not leak its handle.
        if (waiting == null || !waiting.complete(handle)) handle.close()
    }

    override fun onApplyEditsFailed(error: Throwable) {
        super.onApplyEditsFailed(error)
        pendingApply?.completeExceptionally(error)
        pendingApply = null
    }

    override suspend fun writeAnnotatedCopy(target: File): Boolean {
        if (!PdfFeatures.annotationsAvailable) return false
        if (!hasAnnotations || !annotating) return false
        if (pendingApply != null) return false
        val deferred = CompletableDeferred<PdfWriteHandle>()
        pendingApply = deferred
        try {
            applyDraftEdits()
        } catch (_: UnsupportedOperationException) {
            pendingApply = null
            return false
        } catch (_: ApplyInProgressException) {
            pendingApply = null
            return false
        }
        val handle = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        return writeThrough(target) { pfd -> handle.use { it.writeTo(pfd) } }
    }

    /** Opens [target] for writing and hands the descriptor to [write]. False on any I/O failure. */
    private suspend fun writeThrough(target: File, write: suspend (ParcelFileDescriptor) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                target.parentFile?.mkdirs()
                ParcelFileDescriptor.open(
                    target,
                    ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
                ).use { pfd -> write(pfd) }
                true
            } catch (_: IOException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }

    override fun setPageDisplayMode(mode: PageDisplayMode) {
        displayMode = mode
        pdfViewRef?.let { applyDisplayMode(it) }
    }

    private fun applyDisplayMode(view: PdfView) {
        val matrix = PageDisplayFilters.matrixFor(displayMode)
        if (matrix == null) {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        } else {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        }
    }

    override fun onDestroyView() {
        pdfViewRef?.removeOnViewportChangedListener(viewportListener)
        pdfViewRef?.removeOnFirstContentLoadListener(firstContentListener)
        pdfViewRef?.removeOnFormWidgetInfoUpdatedListener(formEditListener)
        pdfViewRef?.setOnTouchListener(null)
        pdfViewRef = null
        pendingApply?.cancel()
        pendingApply = null
        super.onDestroyView()
    }

    private val firstContentListener = PdfView.OnFirstContentLoadListener {
        val target = pendingRestore ?: return@OnFirstContentLoadListener
        pendingRestore = null
        val view = pdfViewRef ?: return@OnFirstContentLoadListener
        view.post {
            if (view.pdfDocument == null) return@post
            view.zoom = target.zoom.coerceIn(view.minZoom, view.maxZoom)
            view.scrollToPage(target.page)
            if (target.scrollY > 0) {
                view.postDelayed({ view.scrollBy(0, target.scrollY.coerceAtMost(view.height)) }, RESTORE_OFFSET_DELAY_MS)
            }
        }
    }

    private val viewportListener = object : PdfView.OnViewportChangedListener {
        override fun onViewportChanged(
            firstVisiblePage: Int,
            visiblePagesCount: Int,
            pageLocations: SparseArray<RectF>,
            zoomLevel: Float,
        ) {
            // Scroll offset within the first visible page, in view pixels, so restore can
            // land mid-page rather than snapping to the page top.
            val pageTop = pageLocations[firstVisiblePage]?.top ?: 0f
            val offsetInPage = if (pageTop < 0f) (-pageTop).toInt() else 0
            host?.onViewportChanged(firstVisiblePage, visiblePagesCount, zoomLevel, offsetInPage)
        }
    }

    private companion object {
        const val RESTORE_OFFSET_DELAY_MS = 120L
    }
}
