package com.leaf.app.ui.tools.images

import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.data.pdf.read.PdfEngine
import com.leaf.app.data.pdf.read.PdfOpenException
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.ImageFormat
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.ChoiceChips
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.ToolSection
import com.leaf.app.ui.tools.common.rememberPdfPicker
import com.leaf.app.ui.tools.common.rememberTreePicker
import com.leaf.app.ui.tools.split.SourceRow
import com.leaf.app.ui.tools.split.rangeErrorText
import com.leaf.app.util.PageRanges
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfToImagesUiState(
    val uri: Uri? = null,
    val displayName: String = "",
    val pageCount: Int? = null,
    val unreadable: Boolean = false,
    val encrypted: Boolean = false,
    val rangeText: String = "",
    val format: ImageFormat = ImageFormat.PNG,
    val quality: Int = 90,
    val dpi: Int = 150,
    val defaultTree: Uri? = null,
) {
    val parsed: PageRanges.Result? get() = pageCount?.let { if (rangeText.isBlank()) null else PageRanges.parse(rangeText, it) }
    /** Whole-document when blank; otherwise the span from the first to the last page named. */
    val span: IntRange?
        get() {
            val count = pageCount ?: return null
            if (rangeText.isBlank()) return 0 until count
            val ok = parsed as? PageRanges.Result.Ok ?: return null
            val zero = PageRanges.toZeroBased(ok.ranges)
            return zero.minOf { it.first }..zero.maxOf { it.last }
        }
    val pagesToExport: Int get() = span?.let { it.last - it.first + 1 } ?: 0
    val ready: Boolean get() = uri != null && !unreadable && !encrypted && span != null
}

class PdfToImagesViewModel(
    private val engine: PdfEngine,
    private val saf: SafAccess,
    settings: SettingsRepository,
    val launcher: OperationLauncher,
    private val fallbackName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PdfToImagesUiState())
    val state: StateFlow<PdfToImagesUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(defaultTree = settings.settings.first().defaultSaveTreeUri?.let(Uri::parse)) } }
    }

    fun setSource(uri: Uri) {
        val info = saf.queryInfo(uri)
        _state.update { it.copy(uri = uri, displayName = info?.displayName ?: fallbackName, pageCount = null, unreadable = false, encrypted = false) }
        viewModelScope.launch {
            val result = engine.open(uri, null)
            val handle = result.getOrNull()
            if (handle != null) {
                _state.update { it.copy(pageCount = handle.pageCount) }
                handle.close()
            } else {
                val kind = (result.exceptionOrNull() as? PdfOpenException)?.kind
                _state.update { it.copy(encrypted = kind == PdfOpenException.Kind.PASSWORD_REQUIRED, unreadable = kind != PdfOpenException.Kind.PASSWORD_REQUIRED) }
            }
        }
    }

    fun setRange(text: String) = _state.update { it.copy(rangeText = text) }
    fun setFormat(f: ImageFormat) = _state.update { it.copy(format = f) }
    fun setQuality(q: Int) = _state.update { it.copy(quality = q) }
    fun setDpi(d: Int) = _state.update { it.copy(dpi = d) }

    fun save(tree: Uri) {
        val s = _state.value
        val uri = s.uri ?: return
        val span = s.span ?: return
        launcher.launch(
            op = DocOperation.PdfToImages(uri, span, s.format, s.dpi, s.quality),
            destination = tree,
            passwords = emptyMap(),
            inputPages = s.pagesToExport,
            summary = s.displayName,
        )
    }
}

@Composable
fun PdfToImagesScreen(initialUri: Uri?, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: PdfToImagesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PdfToImagesViewModel(container.pdfEngine, container.saf, container.settings, container.operationLauncher, fallbackName) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmMany by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(initialUri) { if (initialUri != null && state.uri == null) viewModel.setSource(initialUri) }

    val sourcePicker = rememberPdfPicker(container.saf, multiple = false) { it.firstOrNull()?.let(viewModel::setSource) }
    val treePicker = rememberTreePicker(container.saf) { tree ->
        if (state.pagesToExport > MANY_PAGES) confirmMany = tree else viewModel.save(tree)
    }

    ToolScaffold(
        title = stringResource(R.string.tool_pdf_to_images),
        subtitle = state.displayName.takeIf { it.isNotEmpty() },
        onBack = onBack,
        saveBar = {
            QuireButton(
                onClick = {
                    val tree = state.defaultTree
                    if (tree != null) { if (state.pagesToExport > MANY_PAGES) confirmMany = tree else viewModel.save(tree) } else treePicker.pick()
                },
                enabled = state.ready && !viewModel.launcher.isBusy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.split_save_to_folder)) }
        },
    ) {
        ToolSection(stringResource(R.string.tool_input_document)) {
            SourceRow(name = state.displayName, pageCount = state.pageCount, unreadable = state.unreadable, onPick = { sourcePicker.pick() })
            if (state.encrypted) {
                Text(
                    stringResource(R.string.pdf_to_images_encrypted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
            }
        }
        ToolSection(stringResource(R.string.pdf_to_images_pages)) {
            OutlinedTextField(
                value = state.rangeText,
                onValueChange = viewModel::setRange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.pdf_to_images_all_pages)) },
                isError = state.parsed is PageRanges.Result.Error,
                supportingText = {
                    when (val p = state.parsed) {
                        is PageRanges.Result.Error -> Text(rangeErrorText(p, state.pageCount ?: 0))
                        else -> Text(stringResource(R.string.pdf_to_images_range_help, state.pagesToExport))
                    }
                },
                shape = QuireShape.Button,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            )
        }
        ToolSection(stringResource(R.string.pdf_to_images_format)) {
            ChoiceChips(options = ImageFormat.entries, selected = state.format, label = { it.name }, onSelect = viewModel::setFormat)
            if (state.format == ImageFormat.JPEG) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.pdf_to_images_quality, state.quality),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
                val qualityDesc = stringResource(R.string.pdf_to_images_quality_desc)
                Slider(
                    value = state.quality.toFloat(),
                    onValueChange = { viewModel.setQuality(it.toInt()) },
                    valueRange = 30f..100f,
                    modifier = Modifier
                        .padding(horizontal = Spacing.screenHorizontal)
                        .semantics { contentDescription = qualityDesc },
                )
            }
        }
        ToolSection(stringResource(R.string.pdf_to_images_dpi)) {
            ChoiceChips(
                options = listOf(72, 150, 300),
                selected = state.dpi,
                label = { stringResource(R.string.pdf_to_images_dpi_value, it) },
                onSelect = viewModel::setDpi,
            )
        }
    }

    confirmMany?.let { tree ->
        AlertDialog(
            onDismissRequest = { confirmMany = null },
            shape = QuireShape.Dialog,
            title = { Text(stringResource(R.string.pdf_to_images_many_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.pdf_to_images_many_body, state.pagesToExport), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { QuireTextButton(onClick = { confirmMany = null; viewModel.save(tree) }) { Text(stringResource(R.string.action_continue)) } },
            dismissButton = { QuireTextButton(onClick = { confirmMany = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    active?.let { op ->
        ProgressSheet(
            operation = op,
            onOpen = {},
            onShare = { context.shareDocument(it.toString(), state.displayName) },
            onRetryElsewhere = { treePicker.pick() },
            onCancel = viewModel.launcher::cancel,
            onDismiss = viewModel.launcher::dismiss,
        )
    }
}

private const val MANY_PAGES = 50
