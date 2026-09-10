package com.leaf.app.ui.tools.compress

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.leaf.app.data.pdf.write.CompressLevel
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.PasswordDialog
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.ToolSection
import com.leaf.app.ui.tools.common.rememberCreatePdfPicker
import com.leaf.app.ui.tools.common.rememberPdfPicker
import com.leaf.app.ui.tools.split.RadioRow
import com.leaf.app.ui.tools.split.SourceRow
import com.leaf.app.util.saf.OutputNames
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompressUiState(
    val uri: Uri? = null,
    val displayName: String = "",
    val sizeBytes: Long? = null,
    val pageCount: Int? = null,
    val unreadable: Boolean = false,
    val password: String? = null,
    val needsPassword: Boolean = false,
    val passwordWrong: Boolean = false,
    val level: CompressLevel = CompressLevel.BALANCED,
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
) {
    val ready: Boolean get() = uri != null && !unreadable && !needsPassword && pageCount != null
}

class CompressViewModel(
    private val engine: PdfEngine,
    private val saf: SafAccess,
    settings: SettingsRepository,
    val launcher: OperationLauncher,
) : ViewModel() {
    private val _state = MutableStateFlow(CompressUiState())
    val state: StateFlow<CompressUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(outputPattern = settings.settings.first().outputNamePattern) } }
    }

    fun setSource(uri: Uri, fallbackName: String) {
        val info = saf.queryInfo(uri)
        _state.update {
            CompressUiState(uri = uri, displayName = info?.displayName ?: fallbackName, sizeBytes = info?.sizeBytes, outputPattern = it.outputPattern)
        }
        probe(uri, null)
    }

    private fun probe(uri: Uri, password: String?) = viewModelScope.launch {
        val result = engine.open(uri, password)
        val handle = result.getOrNull()
        if (handle != null) {
            val count = handle.pageCount
            handle.close()
            _state.update { it.copy(pageCount = count, password = password, needsPassword = false, passwordWrong = false) }
        } else {
            val kind = (result.exceptionOrNull() as? PdfOpenException)?.kind
            if (kind == PdfOpenException.Kind.PASSWORD_REQUIRED) _state.update { it.copy(needsPassword = true, passwordWrong = password != null) }
            else _state.update { it.copy(unreadable = true) }
        }
    }

    fun submitPassword(password: String) = _state.value.uri?.let { probe(it, password) }
    fun setLevel(level: CompressLevel) = _state.update { it.copy(level = level) }

    fun suggestedName(): String = _state.value.let { OutputNames.build(it.outputPattern, it.displayName, "compressed") }

    fun save(destination: Uri) {
        val s = _state.value
        val uri = s.uri ?: return
        if (!s.ready) return
        launcher.launch(
            op = DocOperation.Compress(uri, s.level),
            destination = destination,
            passwords = s.password?.let { mapOf(uri to it) } ?: emptyMap(),
            inputPages = s.pageCount ?: 0,
            summary = s.displayName,
        )
    }
}

/** Shrinks the pictures inside a PDF. Text and vector drawings are left exactly as they are. */
@Composable
fun CompressScreen(initialUri: Uri?, onBack: () -> Unit, onOpenOutput: (Uri) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: CompressViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CompressViewModel(container.pdfEngine, container.saf, container.settings, container.operationLauncher) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fallbackName = stringResource(R.string.document_fallback_name)

    LaunchedEffect(initialUri) { if (initialUri != null && state.uri == null) viewModel.setSource(initialUri, fallbackName) }

    val sourcePicker = rememberPdfPicker(container.saf, multiple = false) { it.firstOrNull()?.let { uri -> viewModel.setSource(uri, fallbackName) } }
    val savePicker = rememberCreatePdfPicker { viewModel.save(it) }
    val retryPicker = rememberCreatePdfPicker { destination -> active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) } }

    ToolScaffold(
        title = stringResource(R.string.tool_compress),
        subtitle = state.displayName.takeIf { it.isNotEmpty() },
        onBack = onBack,
        saveBar = {
            QuireButton(
                onClick = { savePicker.pick(viewModel.suggestedName()) },
                enabled = state.ready && !viewModel.launcher.isBusy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ops_save_as_new)) }
        },
    ) {
        ToolSection(stringResource(R.string.tool_input_document)) {
            SourceRow(name = state.displayName, pageCount = state.pageCount, unreadable = state.unreadable, onPick = { sourcePicker.pick() })
            state.sizeBytes?.let { bytes ->
                Text(
                    stringResource(R.string.compress_current_size, Formatter.formatShortFileSize(context, bytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 4.dp),
                )
            }
        }
        ToolSection(stringResource(R.string.compress_how_much)) {
            Text(
                stringResource(R.string.compress_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 4.dp),
            )
            RadioRow(stringResource(R.string.compress_level_light), state.level == CompressLevel.LIGHT) { viewModel.setLevel(CompressLevel.LIGHT) }
            RadioRow(stringResource(R.string.compress_level_balanced), state.level == CompressLevel.BALANCED) { viewModel.setLevel(CompressLevel.BALANCED) }
            RadioRow(stringResource(R.string.compress_level_strong), state.level == CompressLevel.STRONG) { viewModel.setLevel(CompressLevel.STRONG) }
        }
    }

    if (state.needsPassword) {
        PasswordDialog(documentName = state.displayName, wrong = state.passwordWrong, onConfirm = viewModel::submitPassword, onDismiss = onBack)
    }

    active?.let { op ->
        ProgressSheet(
            operation = op,
            onOpen = { viewModel.launcher.dismiss(); onOpenOutput(it) },
            onShare = { context.shareDocument(it.toString(), state.displayName) },
            onRetryElsewhere = { retryPicker.pick(viewModel.suggestedName()) },
            onCancel = viewModel.launcher::cancel,
            onDismiss = viewModel.launcher::dismiss,
        )
    }
}
