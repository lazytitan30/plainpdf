package com.leaf.app.ui.tools

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaf.app.R
import com.leaf.app.data.db.entities.OperationEntity
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing

enum class Tool { SIGN, ORGANISE, MERGE, SPLIT, COMPRESS, IMAGES_TO_PDF, NOTE, PDF_TO_IMAGES, PASSWORD, PAGE_NUMBERS, MAKE_SEARCHABLE, REDACT }

/** A plain list of tools, not a grid of coloured tiles. Recent output above it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onTool: (Tool) -> Unit,
    onOpenOutput: (Uri) -> Unit,
) {
    val container = LocalAppContainer.current
    val recent by container.operations.observeRecent().collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            val successes = recent.filter { it.succeeded && it.outputUri != null }
            if (successes.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tools_recent_output),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal)) {
                        items(successes, key = { it.id }) { op ->
                            RecentOutputCard(
                                op = op,
                                onOpen = { op.outputUri?.let { onOpenOutput(Uri.parse(it)) } },
                                onShare = { op.outputUri?.let { context.shareDocument(it, op.outputName ?: "") } },
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                    }
                    Spacer(Modifier.height(Spacing.section))
                }
            }
            items(Tool.entries, key = { it.name }) { tool ->
                ToolRow(tool = tool, onClick = { onTool(tool) })
            }
        }
    }
}

@Composable
private fun ToolRow(tool: Tool, onClick: () -> Unit) {
    val (title, description) = when (tool) {
        Tool.SIGN -> R.string.tool_sign to R.string.tool_sign_desc
        Tool.ORGANISE -> R.string.tool_organise to R.string.tool_organise_desc
        Tool.MERGE -> R.string.tool_merge to R.string.tool_merge_desc
        Tool.SPLIT -> R.string.tool_split to R.string.tool_split_desc
        Tool.COMPRESS -> R.string.tool_compress to R.string.tool_compress_desc
        Tool.IMAGES_TO_PDF -> R.string.tool_images_to_pdf to R.string.tool_images_to_pdf_desc
        Tool.NOTE -> R.string.tool_note to R.string.tool_note_desc
        Tool.PDF_TO_IMAGES -> R.string.tool_pdf_to_images to R.string.tool_pdf_to_images_desc
        Tool.PASSWORD -> R.string.tool_password to R.string.tool_password_desc
        Tool.PAGE_NUMBERS -> R.string.tool_page_numbers to R.string.tool_page_numbers_desc
        Tool.MAKE_SEARCHABLE -> R.string.tool_make_searchable to R.string.tool_make_searchable_desc
        Tool.REDACT -> R.string.tool_redact to R.string.tool_redact_desc
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentOutputCard(op: OperationEntity, onOpen: () -> Unit, onShare: () -> Unit) {
    val isPdf = op.outputName?.endsWith(".pdf", ignoreCase = true) == true
    Column(
        Modifier
            .width(220.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, QuireShape.Card)
            .clickable(onClick = if (isPdf) onOpen else onShare)
            .padding(12.dp),
    ) {
        Text(op.outputName ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        Text(
            DateUtils.getRelativeTimeSpanString(op.completedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isPdf) QuireTextButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
            QuireTextButton(onClick = onShare) { Text(stringResource(R.string.action_share)) }
        }
    }
}
