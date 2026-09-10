package com.leaf.app.ui.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing

/**
 * Top of the Recent tab: the last document opened, where the reader will pick it up.
 * The whole card opens it; the button is there for people who look for one.
 */
@Composable
fun ContinueReadingCard(
    document: DocumentEntity,
    onContinue: () -> Unit,
    onVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(document.id, document.thumbnailPath) { onVisible() }
    val pages = document.pageCount
    val pageText = if (pages != null && pages > 0) {
        stringResource(R.string.library_continue_page, document.lastPage + 1, pages)
    } else {
        stringResource(R.string.library_continue_page_only, document.lastPage + 1)
    }
    ElevatedCard(
        onClick = onContinue,
        shape = QuireShape.Card,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.unit * 2),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.rowVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(path = document.thumbnailPath, modifier = Modifier.width(64.dp).height(88.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.library_continue_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    pageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.unit * 2))
                QuireButton(onClick = onContinue, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_continue_reading))
                }
            }
        }
    }
}
