package com.leaf.app.ui.tools.split

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.PasswordDialog
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.ToolSection
import com.leaf.app.ui.tools.common.rememberPdfPicker
import com.leaf.app.ui.tools.common.rememberTreePicker
import com.leaf.app.util.PageRanges

@Composable
fun rangeErrorText(error: PageRanges.Result.Error, pageCount: Int): String = when (error.kind) {
    PageRanges.ErrorKind.EMPTY -> stringResource(R.string.ranges_error_empty)
    PageRanges.ErrorKind.BAD_TOKEN -> stringResource(R.string.ranges_error_bad, error.token ?: "")
    PageRanges.ErrorKind.OUT_OF_RANGE -> stringResource(R.string.ranges_error_out_of_range, error.token ?: "", pageCount)
    PageRanges.ErrorKind.REVERSED -> stringResource(R.string.ranges_error_reversed, error.token ?: "")
}

/** Three modes as radios. Multi-file output goes to a folder, never a single create-document call. */
@Composable
fun SplitScreen(
    initialUri: Uri?,
    onBack: () -> Unit,
    onOpenOutput: (Uri) -> Unit,
    onExtract: (Uri) -> Unit,
) {
    val container = LocalAppContainer.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: SplitViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SplitViewModel(container.pdfEngine, container.saf, container.settings, container.operationLauncher, fallbackName) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(initialUri) { if (initialUri != null && state.uri == null) viewModel.setSource(initialUri) }

    val sourcePicker = rememberPdfPicker(container.saf, multiple = false) { it.firstOrNull()?.let(viewModel::setSource) }
    val treePicker = rememberTreePicker(container.saf) { viewModel.save(it) }

    ToolScaffold(
        title = stringResource(R.string.tool_split),
        subtitle = state.displayName.takeIf { it.isNotEmpty() },
        onBack = onBack,
        saveBar = {
            if (state.choice == SplitChoice.EXTRACT) {
                QuireButton(
                    onClick = { state.uri?.let(onExtract) },
                    enabled = state.uri != null && !state.needsPassword && !state.unreadable,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.split_open_organiser)) }
            } else {
                QuireButton(
                    onClick = {
                        val tree = state.defaultTree
                        if (tree != null) viewModel.save(tree) else treePicker.pick()
                    },
                    enabled = state.ready && !viewModel.launcher.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.split_save_to_folder)) }
            }
        },
    ) {
        ToolSection(stringResource(R.string.tool_input_document)) {
            SourceRow(
                name = state.displayName,
                pageCount = state.pageCount,
                unreadable = state.unreadable,
                onPick = { sourcePicker.pick() },
            )
        }
        ToolSection(stringResource(R.string.split_mode)) {
            RadioRow(stringResource(R.string.split_by_ranges), state.choice == SplitChoice.RANGES) { viewModel.setChoice(SplitChoice.RANGES) }
            if (state.choice == SplitChoice.RANGES) {
                OutlinedTextField(
                    value = state.rangeText,
                    onValueChange = viewModel::setRangeText,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.split_ranges_hint)) },
                    isError = state.parsedRanges is PageRanges.Result.Error,
                    supportingText = {
                        when (val parsed = state.parsedRanges) {
                            is PageRanges.Result.Error -> Text(rangeErrorText(parsed, state.pageCount ?: 0))
                            is PageRanges.Result.Ok -> Text(splitPreview(parsed.ranges))
                            null -> Text(stringResource(R.string.split_ranges_help))
                        }
                    },
                    shape = QuireShape.Button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal),
                )
            }
            RadioRow(stringResource(R.string.split_every_n), state.choice == SplitChoice.EVERY_N) { viewModel.setChoice(SplitChoice.EVERY_N) }
            if (state.choice == SplitChoice.EVERY_N) {
                OutlinedTextField(
                    value = state.everyNText,
                    onValueChange = viewModel::setEveryN,
                    singleLine = true,
                    label = { Text(stringResource(R.string.split_pages_per_file)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { state.plannedRanges?.let { Text(splitPreview(it)) } },
                    shape = QuireShape.Button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal),
                )
            }
            RadioRow(stringResource(R.string.split_extract), state.choice == SplitChoice.EXTRACT) { viewModel.setChoice(SplitChoice.EXTRACT) }
            if (state.choice == SplitChoice.EXTRACT) {
                Text(
                    stringResource(R.string.split_extract_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
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
            onRetryElsewhere = { treePicker.pick() },
            onCancel = viewModel.launcher::cancel,
            onDismiss = viewModel.launcher::dismiss,
        )
    }
}

/** "3 files: 4 pages, 1 page, 9 pages" */
@Composable
fun splitPreview(ranges: List<IntRange>): String {
    val counts = PageRanges.pageCountOf(ranges)
    val parts = counts.take(8).map { pluralStringResource(R.plurals.ops_pages, it, it) }
    val tail = if (counts.size > 8) ", …" else ""
    return stringResource(R.string.split_preview, counts.size, parts.joinToString(", ") + tail)
}

@Composable
fun SourceRow(name: String, pageCount: Int?, unreadable: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (name.isEmpty()) {
                Text(stringResource(R.string.tool_choose_pdf), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    when {
                        unreadable -> stringResource(R.string.ops_error_corrupt)
                        pageCount == null -> stringResource(R.string.merge_reading)
                        else -> stringResource(R.string.organise_pages_count, pageCount)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (name.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_change), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Spacing.screenHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
