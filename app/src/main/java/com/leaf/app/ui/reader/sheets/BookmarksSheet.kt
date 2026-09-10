package com.leaf.app.ui.reader.sheets

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.data.db.entities.BookmarkEntity
import com.leaf.app.ui.common.EmptyState
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    colorFilter: ColorFilter?,
    render: suspend (page: Int, widthPx: Int) -> Bitmap?,
    onOpen: (BookmarkEntity) -> Unit,
    onRename: (BookmarkEntity) -> Unit,
    onDelete: (BookmarkEntity) -> Unit,
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
                .fillMaxHeight(0.7f)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.reader_bookmarks),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
            )
            if (bookmarks.isEmpty()) {
                EmptyState(message = stringResource(R.string.reader_bookmarks_empty))
            } else {
                LazyColumn {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            colorFilter = colorFilter,
                            render = render,
                            onOpen = { onOpen(bookmark) },
                            onRename = { onRename(bookmark) },
                            onDelete = { onDelete(bookmark) },
                        )
                    }
                    item { Spacer(Modifier.height(Spacing.section)) }
                }
            }
        }
    }
}

/**
 * Swipe still deletes at once and long press still renames, but both are hidden gestures,
 * so every row also carries a visible menu with the same two actions. Deleting from the
 * menu asks first because there is no undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    colorFilter: ColorFilter?,
    render: suspend (page: Int, widthPx: Int) -> Bitmap?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val title = bookmark.label ?: stringResource(R.string.reader_page_n, bookmark.pageIndex + 1)

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = Spacing.screenHorizontal),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelMedium)
            }
        },
    ) {
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = bookmark.pageIndex) { value = render(bookmark.pageIndex, 160) }
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = onOpen, onLongClick = onRename)
                .padding(start = Spacing.screenHorizontal, end = 4.dp, top = Spacing.rowVertical, bottom = Spacing.rowVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(44.dp)
                    .height(60.dp)
                    .clip(QuireShape.Thumbnail)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, QuireShape.Thumbnail),
                contentAlignment = Alignment.TopCenter,
            ) {
                bitmap?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.FillWidth, colorFilter = colorFilter, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (bookmark.label != null) {
                    Text(
                        stringResource(R.string.reader_page_n, bookmark.pageIndex + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.library_more_options_for, title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = QuireShape.Dialog,
            title = { Text(stringResource(R.string.reader_bookmark_delete_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.reader_bookmark_delete_body, title), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                QuireTextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { QuireTextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
