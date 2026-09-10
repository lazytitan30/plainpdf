package com.leaf.app.ui.tools.password

import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

enum class PasswordAction { SET, REMOVE }

data class PasswordUiState(
    val uri: Uri? = null,
    val displayName: String = "",
    val pageCount: Int? = null,
    val unreadable: Boolean = false,
    val encrypted: Boolean = false,
    val currentPassword: String? = null,
    val needsCurrent: Boolean = false,
    val currentWrong: Boolean = false,
    val action: PasswordAction = PasswordAction.SET,
    val newPassword: String = "",
    val confirm: String = "",
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
) {
    val mismatch: Boolean get() = confirm.isNotEmpty() && confirm != newPassword
    val ready: Boolean
        get() = uri != null && !unreadable && !needsCurrent && pageCount != null &&
            (action == PasswordAction.REMOVE || (newPassword.isNotEmpty() && newPassword == confirm))
}

class PasswordViewModel(
    private val engine: PdfEngine,
    private val saf: SafAccess,
    settings: SettingsRepository,
    val launcher: OperationLauncher,
    private val fallbackName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordUiState())
    val state: StateFlow<PasswordUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(outputPattern = settings.settings.first().outputNamePattern) } }
    }

    fun setSource(uri: Uri) {
        val info = saf.queryInfo(uri)
        _state.update { PasswordUiState(uri = uri, displayName = info?.displayName ?: fallbackName, outputPattern = it.outputPattern) }
        probe(uri, null)
    }

    private fun probe(uri: Uri, password: String?) = viewModelScope.launch {
        val result = engine.open(uri, password)
        val handle = result.getOrNull()
        if (handle != null) {
            val count = handle.pageCount
            handle.close()
            _state.update { it.copy(pageCount = count, encrypted = password != null, currentPassword = password, needsCurrent = false, currentWrong = false) }
        } else {
            val kind = (result.exceptionOrNull() as? PdfOpenException)?.kind
            if (kind == PdfOpenException.Kind.PASSWORD_REQUIRED) _state.update { it.copy(encrypted = true, needsCurrent = true, currentWrong = password != null) }
            else _state.update { it.copy(unreadable = true) }
        }
    }

    fun submitCurrent(password: String) = _state.value.uri?.let { probe(it, password) }
    fun setAction(a: PasswordAction) = _state.update { it.copy(action = a) }
    fun setNewPassword(v: String) = _state.update { it.copy(newPassword = v) }
    fun setConfirm(v: String) = _state.update { it.copy(confirm = v) }

    fun suggestedName(): String {
        val s = _state.value
        return OutputNames.build(s.outputPattern, s.displayName, if (s.action == PasswordAction.REMOVE) "unlocked" else "protected")
    }

    fun save(destination: Uri) {
        val s = _state.value
        val uri = s.uri ?: return
        if (!s.ready) return
        launcher.launch(
            op = DocOperation.SetPassword(uri, if (s.action == PasswordAction.REMOVE) null else s.newPassword, s.currentPassword),
            destination = destination,
            passwords = s.currentPassword?.let { mapOf(uri to it) } ?: emptyMap(),
            inputPages = s.pageCount ?: 0,
            summary = s.displayName,
        )
    }
}

@Composable
fun PasswordScreen(initialUri: Uri?, onBack: () -> Unit, onOpenOutput: (Uri) -> Unit) {
    val container = LocalAppContainer.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: PasswordViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PasswordViewModel(container.pdfEngine, container.saf, container.settings, container.operationLauncher, fallbackName) }
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
        title = stringResource(R.string.tool_password),
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
        ToolSection(stringResource(R.string.password_what)) {
            Text(
                stringResource(R.string.password_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 4.dp),
            )
            if (state.encrypted) {
                RadioRow(stringResource(R.string.password_change), state.action == PasswordAction.SET) { viewModel.setAction(PasswordAction.SET) }
                RadioRow(stringResource(R.string.password_remove), state.action == PasswordAction.REMOVE) { viewModel.setAction(PasswordAction.REMOVE) }
            } else {
                RadioRow(stringResource(R.string.password_add), true) { viewModel.setAction(PasswordAction.SET) }
            }
            if (state.action == PasswordAction.SET) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.newPassword,
                    onValueChange = viewModel::setNewPassword,
                    singleLine = true,
                    label = { Text(stringResource(R.string.password_new)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = QuireShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.confirm,
                    onValueChange = viewModel::setConfirm,
                    singleLine = true,
                    label = { Text(stringResource(R.string.password_confirm)) },
                    isError = state.mismatch,
                    supportingText = if (state.mismatch) { { Text(stringResource(R.string.password_mismatch)) } } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = QuireShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                )
            }
        }
    }

    if (state.needsCurrent) {
        PasswordDialog(documentName = state.displayName, wrong = state.currentWrong, onConfirm = viewModel::submitCurrent, onDismiss = onBack)
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
