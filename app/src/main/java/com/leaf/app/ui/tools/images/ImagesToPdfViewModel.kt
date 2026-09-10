package com.leaf.app.ui.tools.images

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.Fit
import com.leaf.app.data.pdf.write.ImageInput
import com.leaf.app.data.pdf.write.Margin
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.pdf.write.PageSize
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.saf.OutputNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ImageItem(val uid: Int, val uri: Uri, val rotation: Int = 0)

data class ImagesUiState(
    val items: List<ImageItem> = emptyList(),
    val pageSize: PageSize = PageSize.FIT_TO_IMAGE,
    val fit: Fit = Fit.FIT,
    val margin: Margin = Margin.NONE,
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
) {
    val ready: Boolean get() = items.isNotEmpty()
}

class ImagesToPdfViewModel(
    private val resolver: ContentResolver,
    private val settings: SettingsRepository,
    val launcher: OperationLauncher,
) : ViewModel() {

    private val _state = MutableStateFlow(ImagesUiState())
    val state: StateFlow<ImagesUiState> = _state
    private var nextUid = 0
    // Filled from IO threads while the grid reads it on main.
    private val thumbs = ConcurrentHashMap<Uri, Bitmap>()

    init {
        viewModelScope.launch { _state.update { it.copy(outputPattern = settings.settings.first().outputNamePattern) } }
    }

    fun add(uris: List<Uri>) = _state.update { s ->
        val existing = s.items.map { it.uri }.toSet()
        s.copy(items = s.items + uris.filter { it !in existing }.map { ImageItem(nextUid++, it) })
    }

    fun replace(uid: Int, uri: Uri) = _state.update { s -> s.copy(items = s.items.map { if (it.uid == uid) it.copy(uri = uri, rotation = 0) else it }) }

    fun remove(uid: Int) = _state.update { s -> s.copy(items = s.items.filter { it.uid != uid }) }

    fun rotate(uid: Int) = _state.update { s -> s.copy(items = s.items.map { if (it.uid == uid) it.copy(rotation = (it.rotation + 90) % 360) else it }) }

    fun move(fromUid: Int, toUid: Int) = _state.update { s ->
        val from = s.items.indexOfFirst { it.uid == fromUid }
        val to = s.items.indexOfFirst { it.uid == toUid }
        if (from < 0 || to < 0 || from == to) s else s.copy(items = s.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun setPageSize(v: PageSize) = _state.update { it.copy(pageSize = v) }
    fun setFit(v: Fit) = _state.update { it.copy(fit = v) }
    fun setMargin(v: Margin) = _state.update { it.copy(margin = v) }

    /** Small preview, decoded with sampling so a 12 MP photo does not become a 48 MB bitmap. */
    suspend fun thumbnail(uri: Uri): Bitmap? = thumbs[uri] ?: withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return@withContext null
        var sample = 1
        while (longEdge / sample > 600) sample *= 2
        val bmp = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }.getOrNull()
        bmp?.also { thumbs[uri] = it }
    }

    fun suggestedName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        return OutputNames.build(_state.value.outputPattern, "images_$stamp", "pdf")
    }

    fun save(destination: Uri) {
        val s = _state.value
        if (!s.ready) return
        viewModelScope.launch {
            // Photos of documents deserve the same searchable text layer as scans.
            val prefs = settings.settings.first()
            val languages = if (prefs.scanOcr) OcrLanguages.split(prefs.ocrLanguages).ifEmpty { listOf("eng") } else emptyList()
            launcher.launch(
                op = DocOperation.ImagesToPdf(s.items.map { ImageInput(it.uri, it.rotation) }, s.pageSize, s.fit, s.margin, ocrLanguages = languages),
                destination = destination,
                passwords = emptyMap(),
                inputPages = s.items.size,
                summary = "${s.items.size} images",
            )
        }
    }

    override fun onCleared() {
        thumbs.values.forEach { it.recycle() }
        thumbs.clear()
        super.onCleared()
    }
}
