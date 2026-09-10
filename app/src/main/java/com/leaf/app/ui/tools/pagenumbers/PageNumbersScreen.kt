package com.leaf.app.ui.tools.pagenumbers

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
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
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.pdf.write.PageNumberPosition
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
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

data class PageNumbersUiState(
    val uri: Uri? = null,
    val displayName: String = "",
    val pageCount: Int? = null,
    val unreadable: Boolean = false,
    val password: String? = null,
    val needsPassword: Boolean = false,
    val passwordWrong: Boolean = false,
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTRE,
    /** Kept as typed so the field can be emptied while editing; [startAt] is what gets used. */
    val startAtText: String = "1",
    val showTotal: Boolean = false,
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
) {
    val startAt: Int? get() = startAtText.trim().toIntOrNull()?.takeIf { it in 0..MAX_START }
    val startAtInvalid: Boolean get() = startAtText.isNotBlank() && startAt == null
    val ready: Boolean get() = uri != null && !unreadable && !needsPassword && pageCount != null && startAt != null

    /** What the first and last page will read, for the preview under the switch. */
    fun example(): String {
        val first = startAt ?: 1
        val last = first + (pageCount ?: 1) - 1
        return if (showTotal) "$first / $last" else "$first"
    }

    companion object {
        const val MAX_START = 99_999
    }
}

class PageNumbersViewModel(
    private val engine: PdfEngine,
    private val saf: SafAccess,
    settings: SettingsRepository,
    val launcher: OperationLauncher,
    private val fallbackName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PageNumbersUiState())
    val state: StateFlow<PageNumbersUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(outputPattern = settings.settings.first().outputNamePattern) } }
    }

    fun setSource(uri: Uri) {
        val info = saf.queryInfo(uri)
        _state.update {
            PageNumbersUiState(
                uri = uri,
                displayName = info?.displayName ?: fallbackName,
                position = it.position,
                startAtText = it.startAtText,
                showTotal = it.showTotal,
                outputPattern = it.outputPattern,
            )
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
    fun setPosition(p: PageNumberPosition) = _state.update { it.copy(position = p) }
    fun setStartAt(text: String) = _state.update { it.copy(startAtText = text.filter { c -> c.isDigit() }.take(6)) }
    fun setShowTotal(v: Boolean) = _state.update { it.copy(showTotal = v) }

    fun suggestedName(): String = _state.value.let { OutputNames.build(it.outputPattern, it.displayName, "numbered") }

    fun save(destination: Uri) {
        val s = _state.value
        val uri = s.uri ?: return
        val startAt = s.startAt ?: return
        if (!s.ready) return
        launcher.launch(
            op = DocOperation.PageNumbers(uri, s.position, startAt, if (s.showTotal) "{n} / {total}" else "{n}"),
            destination = destination,
            passwords = s.password?.let { mapOf(uri to it) } ?: emptyMap(),
            inputPages = s.pageCount ?: 0,
            summary = s.displayName,
        )
    }
}

/** Prints a number on every page. Position, first number and a "1 / 12" style are the only choices. */
@Composable
fun PageNumbersScreen(initialUri: Uri?, onBack: () -> Unit, onOpenOutput: (Uri) -> Unit) {
    val container = LocalAppContainer.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: PageNumbersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PageNumbersViewModel(container.pdfEngine, container.saf, container.settings, container.operationLauncher, fallbackName) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(initialUri) { if (initialUri != null && state.uri == null) viewModel.setSource(initialUri) }

    val sourcePicker = rememberPdfPicker(container.saf, multiple = false) { it.firstOrNull()?.let(viewModel::setSource) }
    val savePicker = rememberCreatePdfPicker { viewModel.save(it) }
    val retryPicker = rememberCreatePdfPicker { destination -> active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) } }

    ToolScaffold(
        title = stringResource(R.string.tool_page_numbers),
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
        }
        ToolSection(stringResource(R.string.page_numbers_position)) {
            RadioRow(stringResource(R.string.page_numbers_bottom_centre), state.position == PageNumberPosition.BOTTOM_CENTRE) {
                viewModel.setPosition(PageNumberPosition.BOTTOM_CENTRE)
            }
            RadioRow(stringResource(R.string.page_numbers_bottom_right), state.position == PageNumberPosition.BOTTOM_RIGHT) {
                viewModel.setPosition(PageNumberPosition.BOTTOM_RIGHT)
            }
            RadioRow(stringResource(R.string.page_numbers_top_right), state.position == PageNumberPosition.TOP_RIGHT) {
                viewModel.setPosition(PageNumberPosition.TOP_RIGHT)
            }
        }
        ToolSection(stringResource(R.string.page_numbers_style)) {
            OutlinedTextField(
                value = state.startAtText,
                onValueChange = viewModel::setStartAt,
                singleLine = true,
                label = { Text(stringResource(R.string.page_numbers_start_at)) },
                isError = state.startAtInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = QuireShape.Button,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { viewModel.setShowTotal(!state.showTotal) }
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.page_numbers_show_total), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.page_numbers_example, state.example()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = state.showTotal, onCheckedChange = null)
            }
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
