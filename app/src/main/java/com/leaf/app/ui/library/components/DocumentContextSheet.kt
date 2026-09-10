package com.leaf.app.ui.library.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.leaf.app.R
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing

enum class DocumentAction { FAVOURITE, RENAME, SHARE, ORGANISE, REMOVE_FROM_RECENTS, DELETE_FILE }

/** Long-press sheet. Plain rows, sentence case, no icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentContextSheet(
    document: DocumentEntity,
    actions: List<DocumentAction>,
    onAction: (DocumentAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = QuireShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                document.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp0),
            )
            Spacer(Modifier.height(4.dp0))
            actions.forEach { action ->
                val label = when (action) {
                    DocumentAction.FAVOURITE -> if (document.isFavorite) R.string.action_unfavourite else R.string.action_favourite
                    DocumentAction.RENAME -> R.string.action_rename_label
                    DocumentAction.SHARE -> R.string.action_share
                    DocumentAction.ORGANISE -> R.string.action_organise_pages
                    DocumentAction.REMOVE_FROM_RECENTS -> R.string.action_remove_from_recents
                    DocumentAction.DELETE_FILE -> R.string.action_delete_file
                }
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(action) }
                        .padding(horizontal = Spacing.screenHorizontal, vertical = 14.dp0),
                )
            }
            Spacer(Modifier.height(Spacing.section))
        }
    }
}

private val Int.dp0 get() = androidx.compose.ui.unit.Dp(this.toFloat())
