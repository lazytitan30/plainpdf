package com.leaf.app.ui.tools.merge

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.PasswordDialog
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.ToolSection
import com.leaf.app.ui.tools.common.rememberCreatePdfPicker
import com.leaf.app.ui.tools.common.rememberPdfPicker
import com.leaf.app.ui.tools.split.rangeErrorText

/** Ordered list of documents, drag handles as up/down buttons, optional page ranges per document. */
@Composable
fun MergeScreen(
    initialUris: List<Uri>,
    onBack: () -> Unit,
    onOpenOutput: (Uri) -> Unit,
) {
    val container = LocalAppContainer.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: MergeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MergeViewModel(container.pdfEngine, container.saf, container.settings, container.operationLauncher, fallbackName) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(initialUris) { if (initialUris.isNotEmpty()) viewModel.add(initialUris) }

    val addPicker = rememberPdfPicker(container.saf, multiple = true) { viewModel.add(it) }
    val savePicker = rememberCreatePdfPicker { viewModel.save(it) }
    val retryPicker = rememberCreatePdfPicker { destination ->
        active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_merge),
        subtitle = if (state.items.isNotEmpty()) stringResource(R.string.merge_total, state.totalPages) else null,
        onBack = onBack,
        saveBar = {
            QuireButton(
                onClick = { savePicker.pick(viewModel.suggestedName()) },
                enabled = state.ready && !viewModel.launcher.isBusy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ops_save_as_new)) }
        },
    ) {
        ToolSection(stringResource(R.string.merge_documents)) {
            // Stays until there are two, which is also why Save is still off with one document.
            if (state.items.size < 2) {
                Text(
                    stringResource(R.string.merge_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
                )
            }
            state.items.forEachIndexed { index, item ->
                MergeRow(
                    item = item,
                    index = index,
                    count = state.items.size,
                    onUp = { viewModel.move(index, index - 1) },
                    onDown = { viewModel.move(index, index + 1) },
                    onRemove = { viewModel.remove(item.uri) },
                    onToggle = { viewModel.toggleExpanded(item.uri) },
                    onRange = { viewModel.setRange(item.uri, it) },
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { addPicker.pick() }
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.merge_add_file), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    state.passwordFor?.let { uri ->
        val name = state.items.firstOrNull { it.uri == uri }?.displayName ?: ""
        PasswordDialog(
            documentName = name,
            wrong = state.passwordWrong,
            onConfirm = { viewModel.submitPassword(uri, it) },
            onDismiss = viewModel::dismissPassword,
        )
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

@Composable
private fun MergeRow(
    item: MergeItem,
    index: Int,
    count: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onToggle: () -> Unit,
    onRange: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                val meta = buildList {
                    when (val pages = item.pageCount) {
                        null -> add(stringResource(R.string.merge_reading))
                        -1 -> add(stringResource(R.string.ops_error_corrupt))
                        else -> add(stringResource(R.string.organise_pages_count, pages))
                    }
                    item.sizeBytes?.let { add(Formatter.formatShortFileSize(context, it)) }
                    if (item.rangeText.isNotBlank() && item.rangeError == null) add(stringResource(R.string.merge_selected_pages, item.selectedPages))
                }
                Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUp, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up)) }
            IconButton(onClick = onDown, enabled = index < count - 1) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down)) }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove)) }
        }
        if (item.expanded) {
            OutlinedTextField(
                value = item.rangeText,
                onValueChange = onRange,
                singleLine = true,
                label = { Text(stringResource(R.string.merge_range_label)) },
                placeholder = { Text(stringResource(R.string.split_ranges_hint)) },
                isError = item.rangeError != null,
                supportingText = item.rangeError?.let { err -> { Text(rangeErrorText(err, item.pageCount ?: 0)) } },
                shape = QuireShape.Button,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            QuireTextButton(onClick = onToggle) {
                Text(if (item.rangeText.isBlank()) stringResource(R.string.merge_choose_pages) else stringResource(R.string.merge_pages_chosen, item.rangeText))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
