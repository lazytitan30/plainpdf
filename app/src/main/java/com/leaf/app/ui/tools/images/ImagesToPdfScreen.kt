package com.leaf.app.ui.tools.images

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.data.pdf.write.Fit
import com.leaf.app.data.pdf.write.Margin
import com.leaf.app.data.pdf.write.PageSize
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.ChoiceChips
import com.leaf.app.ui.tools.common.ProgressSheet
import com.leaf.app.ui.tools.common.ToolScaffold
import com.leaf.app.ui.tools.common.ToolSection
import com.leaf.app.ui.tools.common.rememberCreatePdfPicker
import com.leaf.app.ui.scan.DocumentCropScreen
import androidx.core.content.FileProvider as LeafFileProvider
import java.io.File

@Composable
fun ImagesToPdfScreen(
    initialUris: List<Uri>,
    onBack: () -> Unit,
    onOpenOutput: (Uri) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: ImagesToPdfViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ImagesToPdfViewModel(context.applicationContext.contentResolver, container.settings, container.operationLauncher) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.launcher.active.collectAsStateWithLifecycle()

    LaunchedEffect(initialUris) { if (initialUris.isNotEmpty()) viewModel.add(initialUris) }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val uris = ArrayList<Uri>()
        data.data?.let { uris += it }
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris += it } }
        uris.forEach { container.saf.takePersistable(it, data.flags) }
        if (uris.isNotEmpty()) viewModel.add(uris)
    }
    // A String so it survives process death while the camera app is in front.
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var adjusting by remember { mutableStateOf<ImageItem?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraTarget?.let(Uri::parse)
        cameraTarget = null
        if (ok && target != null) viewModel.add(listOf(target))
    }
    val savePicker = rememberCreatePdfPicker { viewModel.save(it) }
    val retryPicker = rememberCreatePdfPicker { destination -> active?.retryTemp?.let { viewModel.launcher.retryCommit(it, destination) } }

    ToolScaffold(
        title = stringResource(R.string.tool_images_to_pdf),
        subtitle = if (state.items.isNotEmpty()) stringResource(R.string.images_count, state.items.size) else null,
        onBack = onBack,
        scrollable = false,
        saveBar = {
            QuireButton(
                onClick = { savePicker.pick(viewModel.suggestedName()) },
                enabled = state.ready && !viewModel.launcher.isBusy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ops_save_as_new)) }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                QuireTextButton(onClick = {
                    pickImages.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        },
                    )
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.images_add))
                }
                QuireTextButton(onClick = {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    cameraTarget = uri.toString()
                    runCatching { takePicture.launch(uri) }.onFailure { cameraTarget = null }
                }) { Text(stringResource(R.string.images_camera)) }
            }
            ImageGrid(
                items = state.items,
                thumbnail = viewModel::thumbnail,
                onRotate = viewModel::rotate,
                onRemove = viewModel::remove,
                onMove = viewModel::move,
                onAdjust = { adjusting = it },
                modifier = Modifier.weight(1f),
            )
            ToolSection(stringResource(R.string.images_page)) {
                ChoiceChips(
                    options = PageSize.entries,
                    selected = state.pageSize,
                    label = { stringResource(when (it) { PageSize.FIT_TO_IMAGE -> R.string.images_fit_image; PageSize.A4 -> R.string.images_a4; PageSize.LETTER -> R.string.images_letter }) },
                    onSelect = viewModel::setPageSize,
                )
                Spacer(Modifier.height(4.dp))
                ChoiceChips(
                    options = Fit.entries,
                    selected = state.fit,
                    label = { stringResource(if (it == Fit.FIT) R.string.images_fit else R.string.images_fill) },
                    onSelect = viewModel::setFit,
                )
                Spacer(Modifier.height(4.dp))
                ChoiceChips(
                    options = Margin.entries,
                    selected = state.margin,
                    label = { stringResource(when (it) { Margin.NONE -> R.string.images_margin_none; Margin.SMALL -> R.string.images_margin_small; Margin.MEDIUM -> R.string.images_margin_medium }) },
                    onSelect = viewModel::setMargin,
                )
            }
        }
    }

    adjusting?.let { item ->
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        DocumentCropScreen(
            source = item.uri,
            outputFile = File(dir, "adjusted_${item.uid}_${System.currentTimeMillis()}.jpg"),
            onDone = { file ->
                viewModel.replace(item.uid, LeafFileProvider.getUriForFile(context, "${context.packageName}.files", file))
                adjusting = null
            },
            onCancel = { adjusting = null },
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
private fun ImageGrid(
    items: List<ImageItem>,
    thumbnail: suspend (Uri) -> Bitmap?,
    onRotate: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdjust: (ImageItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.images_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.section),
            )
        }
        return
    }
    val gridState = rememberLazyGridState()
    var draggingUid by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(items) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos -> draggingUid = gridState.uidAt(pos, items); dragOffset = Offset.Zero },
                    onDrag = { change, delta ->
                        change.consume()
                        dragOffset += delta
                        val from = draggingUid ?: return@detectDragGesturesAfterLongPress
                        val target = gridState.uidAt(change.position, items)
                        if (target != null && target != from) { onMove(from, target); dragOffset = Offset.Zero }
                    },
                    onDragEnd = { draggingUid = null; dragOffset = Offset.Zero },
                    onDragCancel = { draggingUid = null; dragOffset = Offset.Zero },
                )
            },
    ) {
        itemsIndexed(items, key = { _, item -> item.uid }) { index, item ->
            val dragging = draggingUid == item.uid
            val bitmap by produceState<Bitmap?>(initialValue = null, key1 = item.uri) { value = thumbnail(item.uri) }
            val pictureDesc = stringResource(R.string.images_picture_n, index + 1)
            Column(
                Modifier
                    .padding(4.dp)
                    .zIndex(if (dragging) 1f else 0f)
                    .then(if (dragging) Modifier.graphicsLayer { translationX = dragOffset.x; translationY = dragOffset.y } else Modifier),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(QuireShape.Thumbnail)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, QuireShape.Thumbnail)
                        .clickable { onAdjust(item) }
                        .semantics { contentDescription = pictureDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    bitmap?.let {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = item.rotation.toFloat() },
                        )
                    }
                }
                // Reorder with buttons; the long-press drag stays as a shortcut.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { if (index > 0) onMove(item.uid, items[index - 1].uid) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_move_earlier))
                    }
                    IconButton(onClick = { if (index < items.lastIndex) onMove(item.uid, items[index + 1].uid) }, enabled = index < items.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.action_move_later))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { onRotate(item.uid) }) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.organise_rotate_right)) }
                    IconButton(onClick = { onRemove(item.uid) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove)) }
                }
            }
        }
    }
}

private fun LazyGridState.uidAt(position: Offset, items: List<ImageItem>): Int? {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { item ->
        val x = position.x - item.offset.x
        val y = position.y - item.offset.y
        x >= 0 && y >= 0 && x <= item.size.width && y <= item.size.height
    } ?: return null
    return items.getOrNull(info.index)?.uid
}
