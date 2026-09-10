package com.leaf.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing

/**
 * One-tap list of everything that can be done to the open document. Reached from the
 * always-visible Tools button, so it never depends on the auto-hiding chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderToolsSheet(
    documentName: String,
    hasForm: Boolean,
    formFilling: Boolean,
    hasAnnotations: Boolean,
    annotating: Boolean,
    readingAloud: Boolean,
    onFillForm: () -> Unit,
    onTool: (ReaderTool) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = QuireShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Up to fifteen rows: taller than many screens, and taller still with a large font,
        // so the list must scroll inside the sheet.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                documentName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
            )
            if (hasForm) {
                ToolRow(
                    title = stringResource(if (formFilling) R.string.reader_fill_active else R.string.reader_fill_form),
                    description = stringResource(R.string.reader_fill_desc),
                    onClick = onFillForm,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            if (hasAnnotations) {
                ToolRow(
                    title = stringResource(if (annotating) R.string.reader_annotate_active else R.string.reader_annotate),
                    description = stringResource(R.string.reader_annotate_desc),
                    onClick = { onTool(ReaderTool.ANNOTATE) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            ToolRow(
                title = stringResource(if (readingAloud) R.string.action_stop else R.string.reader_read_aloud),
                description = stringResource(R.string.reader_read_aloud_desc),
                onClick = { onTool(ReaderTool.READ_ALOUD) },
            )
            ToolRow(stringResource(R.string.reader_copy_text), stringResource(R.string.reader_copy_text_desc)) { onTool(ReaderTool.COPY_TEXT) }
            ToolRow(stringResource(R.string.reader_print), stringResource(R.string.reader_print_desc)) { onTool(ReaderTool.PRINT) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ToolRow(stringResource(R.string.tool_sign), stringResource(R.string.tool_sign_desc)) { onTool(ReaderTool.SIGN) }
            ToolRow(stringResource(R.string.tool_organise), stringResource(R.string.tool_organise_desc)) { onTool(ReaderTool.ORGANISE) }
            ToolRow(stringResource(R.string.reader_tool_merge), stringResource(R.string.tool_merge_desc)) { onTool(ReaderTool.MERGE) }
            ToolRow(stringResource(R.string.tool_split), stringResource(R.string.tool_split_desc)) { onTool(ReaderTool.SPLIT) }
            ToolRow(stringResource(R.string.tool_compress), stringResource(R.string.tool_compress_desc)) { onTool(ReaderTool.COMPRESS) }
            ToolRow(stringResource(R.string.tool_pdf_to_images), stringResource(R.string.tool_pdf_to_images_desc)) { onTool(ReaderTool.PDF_TO_IMAGES) }
            ToolRow(stringResource(R.string.tool_password), stringResource(R.string.tool_password_desc)) { onTool(ReaderTool.PASSWORD) }
            ToolRow(stringResource(R.string.tool_page_numbers), stringResource(R.string.tool_page_numbers_desc)) { onTool(ReaderTool.PAGE_NUMBERS) }
            ToolRow(stringResource(R.string.tool_make_searchable), stringResource(R.string.tool_make_searchable_desc)) { onTool(ReaderTool.MAKE_SEARCHABLE) }
            ToolRow(stringResource(R.string.tool_redact), stringResource(R.string.tool_redact_desc)) { onTool(ReaderTool.REDACT) }
            Spacer(Modifier.height(Spacing.section))
        }
    }
}

@Composable
private fun ToolRow(title: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
