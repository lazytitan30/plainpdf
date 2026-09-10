package com.leaf.app.ui.tools.note

import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.data.pdf.write.DocOperation
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.rememberCreatePdfPicker
import com.leaf.app.util.saf.OutputNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NoteUiState(
    val title: String = "",
    val body: String = "",
) {
    val ready: Boolean get() = title.isNotBlank() || body.isNotBlank()
}

class NoteViewModel(
    val launcher: OperationLauncher,
    /** "Note", the stem of the file name when nothing has been typed yet. */
    private val defaultName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(NoteUiState())
    val state: StateFlow<NoteUiState> = _state

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setBody(v: String) = _state.update { it.copy(body = v) }

    /** The first few words of the title, or of the text, or "Note" and the date: no operation suffix. */
    fun suggestedName(): String = "${stem()}.pdf"

    private fun stem(): String {
        val s = _state.value
        val source = s.title.ifBlank { s.body }
        val words = source.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(MAX_NAME_WORDS)
        val head = words.joinToString(" ")
        if (head.isBlank()) return "$defaultName ${SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())}"
        return OutputNames.sanitize(head.take(MAX_NAME_LENGTH).trim())
    }

    fun save(destination: Uri) {
        val s = _state.value
        if (!s.ready) return
        launcher.launch(
            op = DocOperation.TextToPdf(s.title.trim(), s.body),
            destination = destination,
            passwords = emptyMap(),
            inputPages = 0,
            summary = stem(),
        )
    }

    companion object {
        const val MAX_NAME_WORDS = 6
        const val MAX_NAME_LENGTH = 60
    }
}

/** Type a title and some text; save them as a PDF. Nothing to pick first, so the whole screen is the editor. */
@Composable
fun NoteScreen(onBack: () -> Unit, onOpenOutput: (Uri) -> Unit) {
    val container = LocalAppContainer.current
    val defaultName = stringResource(R.string.note_default_name)
    val viewModel: NoteViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NoteViewModel(container.operationLauncher, defaultName) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val savePicker = rememberCreatePdfPicker { viewModel.save(it) }
    val retryPicker = rememberCreatePdfPicker { destination -> active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) } }

    ToolScaffold(
        title = stringResource(R.string.tool_note),
        onBack = onBack,
        scrollable = false,
        saveBar = {
            QuireButton(
                onClick = { savePicker.pick(viewModel.suggestedName()) },
                enabled = state.ready && !viewModel.launcher.isBusy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ops_save_as_new)) }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            singleLine = true,
            label = { Text(stringResource(R.string.note_title_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            shape = QuireShape.Button,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.body,
            onValueChange = viewModel::setBody,
            placeholder = { Text(stringResource(R.string.note_body_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = QuireShape.Button,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Spacing.screenHorizontal),
        )
        Spacer(Modifier.height(12.dp))
    }

    active?.let { op ->
        ProgressSheet(
            operation = op,
            onOpen = { viewModel.launcher.dismiss(); onOpenOutput(it) },
            onShare = { context.shareDocument(it.toString(), viewModel.suggestedName()) },
            onRetryElsewhere = { retryPicker.pick(viewModel.suggestedName()) },
            onCancel = viewModel.launcher::cancel,
            onDismiss = viewModel.launcher::dismiss,
        )
    }
}
