package com.leaf.app.ui.tools.sign

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
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
import com.leaf.app.ui.common.QuireOutlinedButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.ChoiceChips
import com.leaf.app.ui.tools.common.PasswordDialog
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.rememberCreatePdfPicker
import java.io.File
import kotlin.math.roundToInt

/** Draw a signature, drop it on a page, save a new file. Two steps, same scaffold as every tool. */
@Composable
fun SignScreen(
    uri: String,
    onBack: () -> Unit,
    onOpenOutput: (Uri) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val fallbackName = stringResource(R.string.document_fallback_name)
    val viewModel: SignViewModel = viewModel(
        key = "sign:$uri",
        factory = viewModelFactory {
            initializer {
                SignViewModel(
                    uri, container.documents, container.pdfEngine, container.saf, container.settings,
                    File(context.cacheDir, "work"), container.operationLauncher, fallbackName,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()
    val savePicker = rememberCreatePdfPicker { viewModel.save(it) }
    val retryPicker = rememberCreatePdfPicker { destination -> active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) } }

    ToolScaffold(
        title = stringResource(R.string.tool_sign),
        subtitle = state.displayName.takeIf { it.isNotEmpty() },
        onBack = onBack,
        scrollable = false,
        saveBar = {
            when (state.step) {
                SignStep.DRAW -> {
                    QuireTextButton(onClick = viewModel::clear, enabled = state.strokes.isNotEmpty()) { Text(stringResource(R.string.sign_clear)) }
                    Spacer(Modifier.width(12.dp))
                    QuireButton(onClick = viewModel::goToPlace, enabled = state.canContinue && !state.loading, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.sign_next))
                    }
                }
                SignStep.PLACE -> {
                    QuireOutlinedButton(onClick = viewModel::backToDraw) { Text(stringResource(R.string.sign_back)) }
                    Spacer(Modifier.width(12.dp))
                    QuireButton(
                        onClick = { savePicker.pick(viewModel.suggestedName()) },
                        enabled = !viewModel.launcher.isBusy,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.ops_save_as_new)) }
                }
            }
        },
    ) {
        when {
            state.loading -> LoadingState(stringResource(R.string.progress_opening_document), Modifier.fillMaxSize())
            state.loadError -> EmptyState(message = stringResource(R.string.ops_error_corrupt))
            state.needsPassword -> EmptyState(message = stringResource(R.string.ops_password_needed))
            state.step == SignStep.DRAW -> DrawStep(state, viewModel)
            else -> PlaceStep(state, viewModel)
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
private fun DrawStep(state: SignUiState, viewModel: SignViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.sign_draw_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.sign_draw_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SignatureCanvas(
            strokes = state.strokes,
            ink = state.ink.color(),
            onStrokeStart = viewModel::strokeStart,
            onStrokePoint = viewModel::strokePoint,
            borderColor = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        ChoiceChips(
            options = Ink.entries,
            selected = state.ink,
            label = { stringResource(if (it == Ink.BLACK) R.string.sign_ink_black else R.string.sign_ink_blue) },
            onSelect = viewModel::setInk,
        )
    }
}

@Composable
private fun PlaceStep(state: SignUiState, viewModel: SignViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.sign_place_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
        )
        PagePreview(state, viewModel, Modifier.weight(1f))
        val sizeDesc = stringResource(R.string.sign_size_desc)
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sign_size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Slider(
                value = state.width,
                onValueChange = viewModel::setWidth,
                valueRange = 0.1f..0.9f,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = sizeDesc },
            )
        }
        if (state.pageCount > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = 8.dp)) {
                items((0 until state.pageCount).toList(), key = { it }) { page ->
                    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = page) { value = viewModel.thumbnail(page, 120) }
                    val selected = page == state.page
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

/**
 * The page at fit width with the signature overlaid. Tap anywhere on the page to centre the
 * signature there, or drag the signature itself; both end in the same placement state.
 */
@Composable
private fun PagePreview(state: SignUiState, viewModel: SignViewModel, modifier: Modifier = Modifier) {
    val pageBitmap by produceState<Bitmap?>(initialValue = null, key1 = state.page) { value = viewModel.thumbnail(state.page, PREVIEW_WIDTH_PX) }
    val signature by produceState<Bitmap?>(initialValue = null, key1 = state.strokes, key2 = state.ink) {
        value = renderPreview(state.strokes, state.ink)
    }
    val previewDesc = stringResource(R.string.sign_page_preview_desc)
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal), contentAlignment = Alignment.Center) {
        val page = pageBitmap
        if (page == null) {
            LoadingState(stringResource(R.string.progress_loading_page))
            return@Box
        }
        val pageAspect = page.width.toFloat() / page.height
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Fit the page into the available box.
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
                        detectTapGestures { pos ->
                            viewModel.centreAt(pos.x / pageWpx, pos.y / pageHpx, pageWpx / pageHpx)
                        }
                    },
            ) {
                Image(page.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                val sigW = pageW * state.width
                val sigH = sigW / SIGNATURE_ASPECT
                val x = with(density) { (pageW * state.left).roundToPx() }
                val y = with(density) { (pageH * state.top).roundToPx() }
                Box(
                    Modifier
                        .offset { IntOffset(x, y) }
                        .width(sigW)
                        .height(sigH)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        .pointerInput(pageWpx, pageHpx) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                viewModel.moveBy(drag.x / pageWpx, drag.y / pageHpx, pageWpx / pageHpx)
                            }
                        },
                ) {
                    signature?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

private fun renderPreview(strokes: List<InkStroke>, ink: Ink): Bitmap {
    val width = 600
    val height = (width / SIGNATURE_ASPECT).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = if (ink == Ink.BLUE) 0xFF1B3A8A.toInt() else 0xFF111111.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = width * STROKE_WIDTH_FRACTION
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    for (stroke in strokes) {
        if (stroke.isEmpty()) continue
        val path = android.graphics.Path()
        path.moveTo(stroke[0].x * width, stroke[0].y * height)
        if (stroke.size == 1) path.lineTo(stroke[0].x * width + 0.5f, stroke[0].y * height)
        for (p in stroke.drop(1)) path.lineTo(p.x * width, p.y * height)
        canvas.drawPath(path, paint)
    }
    return bitmap
}

private fun Ink.color(): Color = if (this == Ink.BLUE) Color(0xFF1B3A8A) else Color(0xFF111111)

/** Same order of size as the reader's own page renders, which are known to come back. */
private const val PREVIEW_WIDTH_PX = 720

@Suppress("unused")
private fun Float.px() = roundToInt()
