package com.leaf.app.ui.tools.ocr

import android.net.Uri
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
import com.leaf.app.ui.tools.split.SourceRow
import com.leaf.app.data.pdf.write.OperationProgress
import com.leaf.app.ui.common.GarbledTextHint
import com.leaf.app.ui.common.LanguagePackSuggestion
import com.leaf.app.util.ocr.LanguagePackSource
import com.leaf.app.util.ocr.OcrLanguage
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.ocr.OcrQualityCheck
import com.leaf.app.util.ocr.PackState
import com.leaf.app.util.ocr.PackSuggester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.leaf.app.util.ocr.TessdataStore
import com.leaf.app.util.saf.OutputNames
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MakeSearchableUiState(
    val uri: Uri? = null,
    val displayName: String = "",
    val pageCount: Int? = null,
    val unreadable: Boolean = false,
    val password: String? = null,
    val needsPassword: Boolean = false,
    val passwordWrong: Boolean = false,
    /** Tesseract codes from Settings that are actually installed; never empty once loaded. */
    val languages: List<String> = emptyList(),
    val outputPattern: String = OutputNames.DEFAULT_PATTERN,
) {
    val ready: Boolean get() = uri != null && !unreadable && !needsPassword && pageCount != null && languages.isNotEmpty()
}

class MakeSearchableViewModel(
    private val engine: PdfEngine,
    private val saf: SafAccess,
    private val settings: SettingsRepository,
    private val tessdata: TessdataStore,
    private val packs: LanguagePackSource,
    val launcher: OperationLauncher,
    private val fallbackName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(MakeSearchableUiState())
    val state: StateFlow<MakeSearchableUiState> = _state

    private val suggester = PackSuggester(tessdata, settings, packs)
    private val _suggestion = MutableStateFlow<OcrLanguage?>(null)
    val suggestion: StateFlow<OcrLanguage?> = _suggestion
    val packStates: StateFlow<Map<String, PackState>> = packs.states

    init {
        viewModelScope.launch {
            // Same choice the scanner makes: the chosen languages that are installed, else English.
            // Collected, not read once, so a pack that lands while this screen is open counts.
            settings.settings.collect { s ->
                val chosen = OcrLanguages.split(s.ocrLanguages).filter { tessdata.isInstalled(it) }.ifEmpty { listOf("eng") }
                _state.update { it.copy(outputPattern = s.outputNamePattern, languages = chosen) }
            }
        }
        viewModelScope.launch { _suggestion.value = suggester.current() }
        viewModelScope.launch {
            packs.states.collect { states ->
                val code = _suggestion.value?.code ?: return@collect
                if (states[code] is PackState.Installed) _suggestion.value = null
            }
        }
    }

    fun getSuggestedLanguage() {
        _suggestion.value?.code?.let(suggester::download)
    }

    fun dismissSuggestion() {
        val code = _suggestion.value?.code ?: return
        viewModelScope.launch {
            suggester.dismiss(code)
            _suggestion.value = null
        }
    }

    fun setSource(uri: Uri) {
        val info = saf.queryInfo(uri)
        _state.update {
            MakeSearchableUiState(uri = uri, displayName = info?.displayName ?: fallbackName, languages = it.languages, outputPattern = it.outputPattern)
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

    fun suggestedName(): String = _state.value.let { OutputNames.build(it.outputPattern, it.displayName, "searchable") }

    fun save(destination: Uri) {
        val s = _state.value
        val uri = s.uri ?: return
        if (!s.ready) return
        launcher.launch(
            op = DocOperation.OcrPdf(uri, s.languages),
            destination = destination,
            passwords = s.password?.let { mapOf(uri to it) } ?: emptyMap(),
            inputPages = s.pageCount ?: 0,
            summary = s.displayName,
        )
    }
}

/** Recognises the text of a scanned PDF so it can be searched and copied. Pages with text already are left alone. */
@Composable
fun MakeSearchableScreen(initialUri: Uri?, onBack: () -> Unit, onOpenOutput: (Uri) -> Unit, onOpenLanguages: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: MakeSearchableViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MakeSearchableViewModel(container.pdfEngine, container.saf, container.settings, container.tessdata, container.languagePacks, container.operationLauncher, fallbackName)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val packStates by viewModel.packStates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var garbled by remember { mutableStateOf(false) }

    // Once the searchable copy is written, peek at its text: rubbish means a missing language.
    val progress = active?.progress
    LaunchedEffect(progress) {
        val done = progress as? OperationProgress.Done ?: return@LaunchedEffect
        val uri = done.outputs.firstOrNull()?.uri ?: return@LaunchedEffect
        garbled = OcrQualityCheck.looksGarbled(context, uri)
    }

    LaunchedEffect(initialUri) { if (initialUri != null && state.uri == null) viewModel.setSource(initialUri) }

    val sourcePicker = rememberPdfPicker(container.saf, multiple = false) { it.firstOrNull()?.let(viewModel::setSource) }
    val savePicker = rememberCreatePdfPicker { viewModel.save(it) }
    val retryPicker = rememberCreatePdfPicker { destination -> active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) } }

    ToolScaffold(
        title = stringResource(R.string.tool_make_searchable),
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
        ToolSection(stringResource(R.string.settings_group_ocr)) {
            suggestion?.let { language ->
                LanguagePackSuggestion(
                    language = language,
                    state = packStates[language.code] ?: PackState.NotInstalled,
                    onGet = viewModel::getSuggestedLanguage,
                    onNotNow = viewModel::dismissSuggestion,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
                )
            }
            if (garbled && suggestion == null) {
                GarbledTextHint(onOpenLanguages = onOpenLanguages, modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp))
            }
            Text(
                stringResource(R.string.searchable_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 4.dp),
            )
            Text(
                stringResource(R.string.searchable_languages, state.languages.joinToString(", ") { OcrLanguages.nameOf(it) }),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.searchable_languages_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 4.dp),
            )
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
