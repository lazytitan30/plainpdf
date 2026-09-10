package com.leaf.app.ui.tools.redact

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.ui.common.EmptyState
import com.leaf.app.ui.common.LoadingState
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.PasswordDialog
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.rememberCreatePdfPicker

/**
 * Drag boxes over what must go, then save a copy. Pages with a box become pictures with
 * the boxes painted in, so the text under them is gone, not hidden. Never overwrites.
 */
@Composable
fun RedactScreen(
    uri: String,
    onBack: () -> Unit,
    onOpenOutput: (Uri) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: RedactViewModel = viewModel(
        key = "redact:$uri",
        factory = viewModelFactory {
            initializer {
                RedactViewModel(uri, container.documents, container.pdfEngine, container.saf, container.settings, container.operationLauncher, fallbackName)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val savePicker = rememberCreatePdfPicker { viewModel.save(it) }
    val retryPicker = rememberCreatePdfPicker { destination -> active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) } }

    ToolScaffold(
        title = stringResource(R.string.tool_redact),
        subtitle = state.displayName.takeIf { it.isNotEmpty() },
        onBack = onBack,
        scrollable = false,
        saveBar = {
            QuireTextButton(onClick = viewModel::undo, enabled = state.canUndo) { Text(stringResource(R.string.action_undo)) }
            Spacer(Modifier.width(12.dp))
            QuireButton(
                onClick = { savePicker.pick(viewModel.suggestedName()) },
                enabled = state.ready && !viewModel.launcher.isBusy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ops_save_as_new)) }
        },
    ) {
        when {
            state.loading -> LoadingState(stringResource(R.string.progress_opening_document), Modifier.fillMaxSize())
            state.loadError -> EmptyState(message = stringResource(R.string.ops_error_corrupt))
            state.needsPassword -> EmptyState(message = stringResource(R.string.ops_password_needed))
            else -> RedactBody(state, viewModel)
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

@Composable
private fun RedactBody(state: RedactUiState, viewModel: RedactViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.redact_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
        )
        val count = if (state.boxes.isEmpty()) {
            stringResource(R.string.redact_help)
        } else {
            pluralStringResource(R.plurals.redact_box_count, state.boxes.size, state.boxes.size) + " " +
                pluralStringResource(R.plurals.redact_page_count, state.pagesWithBoxes, state.pagesWithBoxes)
        }
        Text(
            count,
            style = MaterialTheme.typography.labelMedium,
            color = if (state.boxes.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 4.dp),
        )
        PagePreview(state, viewModel, Modifier.weight(1f))
        if (state.pageCount > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = 8.dp)) {
                items((0 until state.pageCount).toList(), key = { it }) { page ->
                    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = page) { value = viewModel.thumbnail(page, 120) }
                    val selected = page == state.page
                    val marked = state.boxes.any { it.pageIndex == page }
                    val pageDesc = if (selected) stringResource(R.string.reader_page_current, page + 1) else stringResource(R.string.reader_page_n, page + 1)
                    Column(
                        Modifier
                            .padding(end = 8.dp)
                            .clickable { viewModel.selectPage(page) }
                            .semantics { contentDescription = pageDesc },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .width(48.dp)
                                .aspectRatio(0.72f)
                                .clip(QuireShape.Thumbnail)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, QuireShape.Thumbnail),
                        ) {
                            bitmap?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                            if (marked) {
                                // A page that will be flattened carries a small black mark so it can be found again.
                                Box(Modifier.align(Alignment.BottomEnd).padding(3.dp).width(10.dp).height(10.dp).background(Color.Black))
                            }
                        }
                        Text(
                            (page + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The page at fit width. Drag anywhere to draw a box; tap a box to take it away. */
@Composable
private fun PagePreview(state: RedactUiState, viewModel: RedactViewModel, modifier: Modifier = Modifier) {
    val pageBitmap by produceState<Bitmap?>(initialValue = null, key1 = state.page) { value = viewModel.thumbnail(state.page, PREVIEW_WIDTH_PX) }
    val previewDesc = stringResource(R.string.redact_page_preview_desc)
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal), contentAlignment = Alignment.Center) {
        val page = pageBitmap
        if (page == null) {
            LoadingState(stringResource(R.string.progress_loading_page))
            return@Box
        }
        val pageAspect = page.width.toFloat() / page.height
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val boxW = maxWidth
            val boxH = maxHeight
            val pageW = if (boxW / boxH > pageAspect) boxH * pageAspect else boxW
            val pageH = pageW / pageAspect
            val density = LocalDensity.current
            val pageWpx = with(density) { pageW.toPx() }
            val pageHpx = with(density) { pageH.toPx() }
            Box(
                Modifier
                    .width(pageW)
                    .height(pageH)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .semantics { contentDescription = previewDesc }
                    .pointerInput(pageWpx, pageHpx) {
                        detectTapGestures { pos -> viewModel.removeAt(pos.x / pageWpx, pos.y / pageHpx) }
                    }
                    .pointerInput(pageWpx, pageHpx) {
                        detectDragGestures(
                            onDragStart = { pos -> viewModel.draftStart(pos.x / pageWpx, pos.y / pageHpx) },
                            onDrag = { change, _ ->
                                change.consume()
                                viewModel.draftMove(change.position.x / pageWpx, change.position.y / pageHpx)
                            },
                            onDragEnd = { viewModel.draftCommit() },
                            onDragCancel = { viewModel.draftCancel() },
                        )
                    },
            ) {
                Image(page.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    for (b in state.boxesOnPage) {
                        drawRect(
                            Color.Black,
                            topLeft = Offset(b.left * size.width, b.top * size.height),
                            size = Size((b.right - b.left) * size.width, (b.bottom - b.top) * size.height),
                        )
                    }
                    state.draft?.let { d ->
                        val left = minOf(d.startX, d.endX) * size.width
                        val top = minOf(d.startY, d.endY) * size.height
                        val w = kotlin.math.abs(d.endX - d.startX) * size.width
                        val h = kotlin.math.abs(d.endY - d.startY) * size.height
                        drawRect(accent.copy(alpha = 0.25f), topLeft = Offset(left, top), size = Size(w, h))
                        drawRect(accent, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
    }
}

/** Same order of size as the reader's own page renders, which are known to come back. */
private const val PREVIEW_WIDTH_PX = 720
