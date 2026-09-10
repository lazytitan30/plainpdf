package com.leaf.app.ui.tools.sign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.docs.DocumentRepository
import com.leaf.app.data.pdf.read.PdfEngine
import com.leaf.app.data.pdf.read.PdfHandle
import com.leaf.app.data.pdf.read.PdfOpenException
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.ui.reader.PageThumbnailCache
import com.leaf.app.util.saf.OutputNames
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class SignStep { DRAW, PLACE }
enum class Ink { BLACK, BLUE }

data class SignUiState(
    val uri: Uri,
    val displayName: String = "",
    val pageCount: Int = 0,
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val needsPassword: Boolean = false,
    val passwordWrong: Boolean = false,
    val step: SignStep = SignStep.DRAW,
    val strokes: List<InkStroke> = emptyList(),
    val ink: Ink = Ink.BLACK,
    val page: Int = 0,
    /** Placement as fractions of the displayed page, top-left origin. */
    val left: Float = 0.1f,
    val top: Float = 0.7f,
    val width: Float = 0.35f,
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
) {
    val canContinue: Boolean get() = strokes.any { it.size > 1 }
}

class SignViewModel(
    uriString: String,
    private val documents: DocumentRepository,
    private val engine: PdfEngine,
    private val saf: SafAccess,
    private val settings: SettingsRepository,
    private val workDir: File,
    val launcher: OperationLauncher,
    private val fallbackName: String,
) : ViewModel() {

    private val uri = Uri.parse(uriString)
    private val _state = MutableStateFlow(SignUiState(uri = uri))
    val state: StateFlow<SignUiState> = _state

    private var handle: PdfHandle? = null
    private var password: String? = null
    private val thumbnails = PageThumbnailCache(16 * 1024 * 1024)

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            val doc = documents.findByUri(uriString)
            val info = saf.queryInfo(uri)
            _state.update {
                it.copy(
                    displayName = doc?.title ?: info?.displayName ?: fallbackName,
                    page = doc?.lastPage ?: 0,
                    outputPattern = s.outputNamePattern,
                )
            }
            open(null)
        }
    }

    private suspend fun open(pw: String?) {
        val result = engine.open(uri, pw)
        val h = result.getOrNull()
        if (h == null) {
            val kind = (result.exceptionOrNull() as? PdfOpenException)?.kind
            if (kind == PdfOpenException.Kind.PASSWORD_REQUIRED) {
                _state.update { it.copy(loading = false, needsPassword = true, passwordWrong = pw != null) }
            } else {
                _state.update { it.copy(loading = false, loadError = true) }
            }
            return
        }
        handle?.close()
        handle = h
        password = pw
        _state.update { it.copy(loading = false, needsPassword = false, pageCount = h.pageCount, page = it.page.coerceIn(0, (h.pageCount - 1).coerceAtLeast(0))) }
    }

    fun submitPassword(pw: String) = viewModelScope.launch { open(pw) }

    suspend fun thumbnail(page: Int, widthPx: Int): Bitmap? {
        val h = handle ?: return null
        return thumbnails.get(page, widthPx) {
            runCatching { h.renderPage(page, widthPx) }
                .onFailure { android.util.Log.w("Sign", "render page $page at $widthPx failed", it) }
                .getOrNull()
        }
    }

    // ---- Drawing ----

    fun strokeStart(p: Offset) = _state.update { it.copy(strokes = it.strokes + listOf(listOf(p))) }

    fun strokePoint(p: Offset) = _state.update { s ->
        if (s.strokes.isEmpty()) s else s.copy(strokes = s.strokes.dropLast(1) + listOf(s.strokes.last() + p))
    }

    fun clear() = _state.update { it.copy(strokes = emptyList()) }
    fun setInk(ink: Ink) = _state.update { it.copy(ink = ink) }

    // ---- Placement ----

    fun goToPlace() = _state.update { it.copy(step = SignStep.PLACE) }
    fun backToDraw() = _state.update { it.copy(step = SignStep.DRAW) }
    fun selectPage(page: Int) = _state.update { it.copy(page = page.coerceIn(0, (it.pageCount - 1).coerceAtLeast(0))) }

    fun moveBy(dxFrac: Float, dyFrac: Float, pageAspect: Float) = _state.update { s ->
        val height = s.width / SIGNATURE_ASPECT * pageAspect
        s.copy(
            left = (s.left + dxFrac).coerceIn(0f, (1f - s.width).coerceAtLeast(0f)),
            top = (s.top + dyFrac).coerceIn(0f, (1f - height).coerceAtLeast(0f)),
        )
    }

    /** Tap placement: centre the signature on a point given as fractions of the displayed page. */
    fun centreAt(xFrac: Float, yFrac: Float, pageAspect: Float) = _state.update { s ->
        val height = s.width / SIGNATURE_ASPECT * pageAspect
        s.copy(
            left = (xFrac - s.width / 2f).coerceIn(0f, (1f - s.width).coerceAtLeast(0f)),
            top = (yFrac - height / 2f).coerceIn(0f, (1f - height).coerceAtLeast(0f)),
        )
    }

    fun setWidth(width: Float) = _state.update { s ->
        val w = width.coerceIn(0.1f, 0.9f)
        s.copy(width = w, left = s.left.coerceIn(0f, (1f - w).coerceAtLeast(0f)))
    }

    // ---- Save ----

    fun suggestedName(): String = OutputNames.build(_state.value.outputPattern, _state.value.displayName, "signed")

    fun save(destination: Uri) {
        val s = _state.value
        if (!s.canContinue) return
        viewModelScope.launch {
            val png = withContext(Dispatchers.IO) { exportSignature(s.strokes, s.ink) }
            launcher.launch(
                op = DocOperation.Stamp(uri, s.page, png, s.left, s.top, s.width),
                destination = destination,
                passwords = password?.let { mapOf(uri to it) } ?: emptyMap(),
                inputPages = s.pageCount,
                summary = s.displayName,
            )
        }
    }

    /** Renders the strokes to a transparent PNG at [EXPORT_WIDTH] px with the on-screen stroke weight. */
    private fun exportSignature(strokes: List<InkStroke>, ink: Ink): File {
        val width = EXPORT_WIDTH
        val height = (width / SIGNATURE_ASPECT).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (ink == Ink.BLUE) 0xFF1B3A8A.toInt() else 0xFF111111.toInt()
            style = Paint.Style.STROKE
            strokeWidth = width * STROKE_WIDTH_FRACTION
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            val path = Path()
            path.moveTo(stroke[0].x * width, stroke[0].y * height)
            if (stroke.size == 1) path.lineTo(stroke[0].x * width + 0.5f, stroke[0].y * height)
            for (p in stroke.drop(1)) path.lineTo(p.x * width, p.y * height)
            canvas.drawPath(path, paint)
        }
        workDir.mkdirs()
        val file = File(workDir, "signature-${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    override fun onCleared() {
        handle?.close()
        thumbnails.clear()
        super.onCleared()
    }

    companion object {
        const val EXPORT_WIDTH = 1500
    }
}
