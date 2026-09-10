package com.leaf.app.ui.tools.organise

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.data.pdf.write.PagePlan
import com.leaf.app.ui.common.EmptyState
import com.leaf.app.ui.common.LoadingState
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireOutlinedButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.PasswordDialog
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.rememberCreatePdfPicker
import kotlin.math.roundToInt

/**
 * The centrepiece tool. Rotate, delete, reorder, extract and duplicate on one grid,
 * all backed by one PagePlan. Deleted pages stay visible, dimmed and struck through,
 * until the file is saved.
 */
@Composable
fun OrganiseScreen(
    uri: String,
    onBack: () -> Unit,
    onOpenOutput: (Uri) -> Unit,
) {
    val container = LocalAppContainer.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: OrganiseViewModel = viewModel(
        key = "organise:$uri",
        factory = viewModelFactory {
            initializer { OrganiseViewModel(uri, container.documents, container.pdfEngine, container.saf, container.settings, container.operationLauncher, fallbackName) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingPlan by remember { mutableStateOf<PagePlan?>(null) }
    var confirmOverwrite by remember { mutableStateOf(false) }
    val createPicker = rememberCreatePdfPicker { destination ->
        pendingPlan?.let { viewModel.save(it, destination) }
        pendingPlan = null
    }
    val retryPicker = rememberCreatePdfPicker { destination ->
        active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) }
    }

    val subtitle = if (state.pageCount > 0) {
        val pages = stringResource(R.string.organise_pages_count, state.livePages)
        if (state.changes > 0) "$pages  ·  ${stringResource(R.string.organise_changes, state.changes)}" else pages
    } else null

    ToolScaffold(
        title = state.displayName,
        subtitle = subtitle,
        onBack = onBack,
        scrollable = false,
        actions = {
            IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.action_undo))
            }
            IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                Icon(painterResource(R.drawable.ic_redo), contentDescription = stringResource(R.string.action_redo))
            }
        },
        saveBar = {
            QuireButton(
                onClick = {
                    pendingPlan = state.plan
                    createPicker.pick(viewModel.suggestedName("organised"))
                },
                enabled = state.livePages > 0 && !state.loading && !viewModel.launcher.isBusy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ops_save_as_new)) }
            if (state.hasWriteGrant) {
                Spacer(Modifier.width(12.dp))
                QuireOutlinedButton(
                    onClick = { if (state.confirmOverwrite) confirmOverwrite = true else viewModel.save(state.plan, state.uri) },
                    enabled = state.livePages > 0 && state.changes > 0 && !viewModel.launcher.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.ops_overwrite)) }
            }
        },
    ) {
        when {
            state.loading -> LoadingState(stringResource(R.string.progress_reading_pages), Modifier.fillMaxSize())
            state.loadError -> EmptyState(message = stringResource(R.string.ops_error_corrupt))
            state.needsPassword -> EmptyState(message = stringResource(R.string.ops_password_needed))
            else -> Column(Modifier.fillMaxSize()) {
                PageGrid(
                    items = state.items,
                    selected = state.selected,
                    thumbnail = { index, w -> viewModel.thumbnail(index, w) },
                    onToggle = viewModel::toggle,
                    onMove = viewModel::move,
                    modifier = Modifier.weight(1f),
                )
                if (state.selected.isNotEmpty()) {
                    // With exactly one page selected, reordering is a button press, not a long-press drag.
                    val single = state.selected.singleOrNull()
                    val singleIndex = single?.let { uid -> state.items.indexOfFirst { it.uid == uid } } ?: -1
                    SelectionBar(
                        count = state.selected.size,
                        anyDeleted = state.selectedDeleted,
                        singleSelected = single != null,
                        canMoveEarlier = singleIndex > 0,
                        canMoveLater = singleIndex >= 0 && singleIndex < state.items.lastIndex,
                        onMoveEarlier = {
                            if (single != null && singleIndex > 0) viewModel.move(single, state.items[singleIndex - 1].uid)
                        },
                        onMoveLater = {
                            if (single != null && singleIndex in 0 until state.items.lastIndex) viewModel.move(single, state.items[singleIndex + 1].uid)
                        },
                        onRotateLeft = { viewModel.rotateSelected(-90) },
                        onRotateRight = { viewModel.rotateSelected(90) },
                        onDelete = viewModel::deleteSelected,
                        onRestore = viewModel::restoreSelected,
                        onDuplicate = viewModel::duplicateSelected,
                        // Extract with only deleted pages selected would produce nothing; do not create a file.
                        canExtract = !state.selectedAllDeleted,
                        onExtract = {
                            pendingPlan = viewModel.extractPlan()
                            createPicker.pick(viewModel.suggestedName("extract"))
                        },
                        onSelectAll = viewModel::selectAll,
                        onClear = viewModel::clearSelection,
                    )
                }
            }
        }
    }

    if (state.needsPassword) {
        PasswordDialog(
            documentName = state.displayName,
            wrong = state.passwordWrong,
            onConfirm = viewModel::submitPassword,
            onDismiss = onBack,
        )
    }

    if (confirmOverwrite) {
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            shape = QuireShape.Dialog,
            title = { Text(stringResource(R.string.ops_overwrite_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.ops_overwrite_body, state.displayName), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                QuireTextButton(onClick = {
                    confirmOverwrite = false
                    viewModel.save(state.plan, state.uri)
                }) { Text(stringResource(R.string.ops_overwrite), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { QuireTextButton(onClick = { confirmOverwrite = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    active?.let { op ->
        ProgressSheet(
            operation = op,
            onOpen = { viewModel.launcher.dismiss(); onOpenOutput(it) },
            onShare = { context.shareDocument(it.toString(), state.displayName) },
            onRetryElsewhere = { retryPicker.pick(viewModel.suggestedName("organised")) },
            onCancel = viewModel.launcher::cancel,
            onDismiss = viewModel.launcher::dismiss,
        )
    }
}

/**
 * Every action stays on screen: the edit buttons wrap onto a second line instead of
 * scrolling off the edge, and Delete (or Restore) sits apart at the end behind a divider.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(
    count: Int,
    anyDeleted: Boolean,
    singleSelected: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDuplicate: () -> Unit,
    canExtract: Boolean,
    onExtract: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.organise_selected, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            QuireTextButton(onClick = onSelectAll) { Text(stringResource(R.string.action_select_all)) }
            QuireTextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(Modifier.weight(1f)) {
                QuireTextButton(onClick = onRotateLeft) { Text(stringResource(R.string.organise_rotate_left)) }
                QuireTextButton(onClick = onRotateRight) { Text(stringResource(R.string.organise_rotate_right)) }
                QuireTextButton(onClick = onDuplicate) { Text(stringResource(R.string.organise_duplicate)) }
                QuireTextButton(onClick = onExtract, enabled = canExtract) { Text(stringResource(R.string.organise_extract)) }
                if (singleSelected) {
                    QuireTextButton(onClick = onMoveEarlier, enabled = canMoveEarlier) { Text(stringResource(R.string.action_move_earlier)) }
                    QuireTextButton(onClick = onMoveLater, enabled = canMoveLater) { Text(stringResource(R.string.action_move_later)) }
                }
            }
            VerticalDivider(Modifier.height(28.dp), color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(4.dp))
            if (anyDeleted) {
                QuireTextButton(onClick = onRestore) { Text(stringResource(R.string.organise_restore)) }
            } else {
                QuireTextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

/** Three columns, drag after long press to reorder. No bounce, no entrance animation. */
@Composable
private fun PageGrid(
    items: List<PageItem>,
    selected: Set<Int>,
    thumbnail: suspend (sourceIndex: Int, widthPx: Int) -> Bitmap?,
    onToggle: (Int) -> Unit,
    onMove: (fromUid: Int, toUid: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    var draggingUid by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(items) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        draggingUid = gridState.itemUidAt(pos, items)
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        dragOffset += delta
                        val from = draggingUid ?: return@detectDragGesturesAfterLongPress
                        val target = gridState.itemUidAt(change.position, items)
                        if (target != null && target != from) {
                            onMove(from, target)
                            // Keep the visual offset continuous across the reorder.
                            dragOffset = Offset.Zero
                        }
                    },
                    onDragEnd = { draggingUid = null; dragOffset = Offset.Zero },
                    onDragCancel = { draggingUid = null; dragOffset = Offset.Zero },
                )
            },
    ) {
        items(items, key = { it.uid }) { item ->
            val position = items.indexOf(item) + 1
            val dragging = draggingUid == item.uid
            PageTile(
                item = item,
                position = position,
                selected = item.uid in selected,
                thumbnail = thumbnail,
                onClick = { onToggle(item.uid) },
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .then(if (dragging) Modifier.graphicsLayer { translationX = dragOffset.x; translationY = dragOffset.y } else Modifier),
            )
        }
    }
}

private fun LazyGridState.itemUidAt(position: Offset, items: List<PageItem>): Int? {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { item ->
        val x = position.x - item.offset.x
        val y = position.y - item.offset.y
        x >= 0 && y >= 0 && x <= item.size.width && y <= item.size.height
    } ?: return null
    return items.getOrNull(info.index)?.uid
}

@Composable
private fun PageTile(
    item: PageItem,
    position: Int,
    selected: Boolean,
    thumbnail: suspend (sourceIndex: Int, widthPx: Int) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = item.sourceIndex) { value = thumbnail(item.sourceIndex, 200) }
    val outline = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedDesc = stringResource(R.string.organise_page_selected, position)
    val plainDesc = stringResource(R.string.organise_page_desc, position)
    val deletedDesc = stringResource(R.string.organise_page_deleted, position)
    Column(
        modifier
            .padding(4.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (item.deleted) deletedDesc else if (selected) selectedDesc else plainDesc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(QuireShape.Thumbnail)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(if (selected) 2.dp else 1.dp, if (selected) accent else outline, QuireShape.Thumbnail)
                .alpha(if (item.deleted) 0.35f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = item.rotation.toFloat() },
                )
            }
            if (item.deleted) {
                // Strikethrough in onSurfaceVariant, never red. Red is for the overwrite confirm only.
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(muted, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 4.dp.toPx())
                }
            }
        }
        Text(
            position.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Suppress("unused")
private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())

@Suppress("unused")
private fun Float.roundToIntSafe() = roundToInt()
