package com.leaf.app.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireOutlinedButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.common.GarbledTextHint
import com.leaf.app.ui.common.LanguagePackSuggestion
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.ocr.PackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Appears right after a photo is taken. Share is the primary action; nothing else is required. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(
    state: ScanUiState,
    onAddPage: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onAdjustPage: (Int) -> Unit,
    onShare: (Uri) -> Unit,
    onOpen: (Uri) -> Unit,
    onSaveElsewhere: (Uri, String) -> Unit,
    onCopyText: () -> Unit,
    onTextCopiedShown: () -> Unit,
    onDone: () -> Unit,
    packStates: Map<String, PackState> = emptyMap(),
    onGetLanguage: () -> Unit = {},
    onDismissSuggestion: () -> Unit = {},
    onOpenLanguages: () -> Unit = {},
) {
    // The "copied" note is transient; Android 13+ also shows its own clipboard toast.
    LaunchedEffect(state.textCopied) {
        if (state.textCopied) {
            delay(2500)
            onTextCopiedShown()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = QuireShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Spacing.section),
        ) {
            val output = state.output
            Text(
                output?.displayName ?: stringResource(R.string.scan_title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
            Text(
                pluralStringResource(R.plurals.ops_pages, state.pages.size, state.pages.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
            if (state.searchable) {
                Text(
                    stringResource(R.string.scan_searchable, state.ocrLanguages.joinToString(", ") { OcrLanguages.nameOf(it) }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
            }
            state.packSuggestion?.let { language ->
                Spacer(Modifier.height(8.dp))
                LanguagePackSuggestion(
                    language = language,
                    state = packStates[language.code] ?: PackState.NotInstalled,
                    onGet = onGetLanguage,
                    onNotNow = onDismissSuggestion,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
            }
            if (state.garbledHint && state.packSuggestion == null) {
                Spacer(Modifier.height(8.dp))
                GarbledTextHint(onOpenLanguages = onOpenLanguages, modifier = Modifier.padding(horizontal = Spacing.screenHorizontal))
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal)) {
                items(state.pages.indices.toList(), key = { state.pages[it].original.toString() }) { index ->
                    PhotoThumb(
                        index = index,
                        photo = state.pages[index].effective,
                        onAdjust = { onAdjustPage(index) },
                        onRemove = { onRemovePage(index) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.building) {
                Text(
                    state.progressMessage ?: stringResource(R.string.progress_building_pdf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
            }
            Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                QuireButton(
                    onClick = { output?.let { onShare(it.uri) } },
                    enabled = output != null && !state.building,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.scan_share)) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    QuireOutlinedButton(onClick = onAddPage, enabled = !state.building, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.scan_add_page))
                    }
                    Spacer(Modifier.width(8.dp))
                    QuireOutlinedButton(
                        onClick = { output?.let { onOpen(it.uri) } },
                        enabled = output != null && !state.building,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_open)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    QuireTextButton(onClick = { output?.let { onSaveElsewhere(it.uri, it.displayName) } }, enabled = output != null && !state.building) {
                        Text(stringResource(R.string.scan_save_to_folder))
                    }
                    if (output != null && state.searchable) {
                        QuireTextButton(onClick = onCopyText, enabled = !state.building && !state.copyingText) {
                            Text(stringResource(if (state.textCopied) R.string.scan_text_copied else R.string.scan_copy_text))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    QuireTextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(index: Int, photo: Uri, onAdjust: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val pageDesc = stringResource(R.string.reader_page_n, index + 1)
    var confirmRemove by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = photo) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(photo)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 400) sample *= 2
                context.contentResolver.openInputStream(photo)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(QuireShape.Thumbnail)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, QuireShape.Thumbnail)
                .clickable(onClick = onAdjust)
                .semantics { contentDescription = pageDesc },
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp)) }
        }
        Row {
            QuireTextButton(onClick = onAdjust) { Text(stringResource(R.string.crop_adjust)) }
            QuireTextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.action_remove)) }
        }
    }

    // Removing deletes the photo, so a mis-tap should not cost a page.
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            shape = QuireShape.Dialog,
            title = { Text(stringResource(R.string.scan_remove_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.scan_remove_body, index + 1), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                QuireTextButton(onClick = { confirmRemove = false; onRemove() }) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { QuireTextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
