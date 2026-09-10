package com.leaf.app.ui.tools.common

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.data.pdf.write.ActiveOperation
import com.leaf.app.data.pdf.write.OperationError
import com.leaf.app.data.pdf.write.OperationErrors
import com.leaf.app.data.pdf.write.OperationProgress
import com.leaf.app.ui.common.CompactButtonPadding
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireOutlinedButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing

/**
 * Shared progress treatment for every tool. Results say where the file went, with the
 * filename, and offer Open and Share inline. Never a bare "Success".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressSheet(
    operation: ActiveOperation,
    onOpen: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
    onRetryElsewhere: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val progress = operation.progress
    val working = progress is OperationProgress.Working
    ModalBottomSheet(
        onDismissRequest = { if (!working) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !working }),
        shape = QuireShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = if (working) null else { { androidx.compose.material3.BottomSheetDefaults.DragHandle() } },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(top = 8.dp, bottom = Spacing.section),
        ) {
            when (progress) {
                is OperationProgress.Working -> {
                    // Recognition is the one step with a translated label; the rest are short technical phrases.
                    val label = progress.recognising?.let { stringResource(R.string.scan_recognising, it.page, it.total) } ?: progress.label
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    val fraction = progress.fraction
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    QuireTextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
                is OperationProgress.Done -> {
                    val outputs = progress.outputs
                    Text(
                        if (outputs.size == 1) stringResource(R.string.ops_saved_one, outputs[0].displayName)
                        else stringResource(R.string.ops_saved_many, outputs.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val totalPages = outputs.sumOf { it.pageCount }
                    if (totalPages > 0) {
                        Text(
                            pluralStringResource(R.plurals.ops_pages, totalPages, totalPages),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    val single = outputs.singleOrNull()
                    if (single != null) {
                        // One file: the title already names it, so the actions share one row.
                        // Open comes last and filled because looking at the result is what most
                        // people do next; Done is the quiet way out.
                        val canOpen = single.displayName.endsWith(".pdf", ignoreCase = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuireOutlinedButton(onClick = { onShare(single.uri) }, modifier = Modifier.weight(1f), contentPadding = CompactButtonPadding) {
                                Text(stringResource(R.string.action_share), textAlign = TextAlign.Center)
                            }
                            if (canOpen) {
                                QuireOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), contentPadding = CompactButtonPadding) {
                                    Text(stringResource(R.string.action_done), textAlign = TextAlign.Center)
                                }
                                QuireButton(onClick = { onOpen(single.uri) }, modifier = Modifier.weight(1f), contentPadding = CompactButtonPadding) {
                                    Text(stringResource(R.string.action_open), textAlign = TextAlign.Center)
                                }
                            } else {
                                QuireButton(onClick = onDismiss, modifier = Modifier.weight(1f), contentPadding = CompactButtonPadding) {
                                    Text(stringResource(R.string.action_done), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        outputs.take(6).forEach { out ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(out.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                if (out.displayName.endsWith(".pdf", ignoreCase = true)) {
                                    QuireTextButton(onClick = { onOpen(out.uri) }) { Text(stringResource(R.string.action_open)) }
                                }
                                QuireTextButton(onClick = { onShare(out.uri) }) { Text(stringResource(R.string.action_share)) }
                            }
                        }
                        if (outputs.size > 6) {
                            Text(stringResource(R.string.ops_and_more, outputs.size - 6), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(16.dp))
                        QuireButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
                    }
                }
                is OperationProgress.Failed -> {
                    Text(stringResource(R.string.ops_failed_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(OperationErrors.describe(context, progress.reason), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    if (progress.reason is OperationError.DestinationWriteFailed) {
                        QuireButton(onClick = onRetryElsewhere, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ops_try_other_location)) }
                        Spacer(Modifier.height(8.dp))
                    }
                    QuireOutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}
