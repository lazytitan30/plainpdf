package com.leaf.app.ui.library.components

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads a cached thumbnail off the main thread. Null while loading or when there is none. */
@Composable
private fun rememberThumbnail(path: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}

@Composable
fun Thumbnail(path: String?, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val bitmap = rememberThumbnail(path)
    Box(
        modifier
            .clip(QuireShape.Thumbnail)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, QuireShape.Thumbnail),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                alpha = if (dimmed) 0.4f else 1f,
            )
        }
    }
}

@Composable
fun documentSubtitle(document: DocumentEntity): String {
    val context = LocalContext.current
    if (document.permissionLost) return stringResource(R.string.library_file_unavailable)
    val parts = ArrayList<String>(3)
    val pages = document.pageCount
    if (pages != null && pages > 0) {
        parts += stringResource(R.string.library_page_of, document.lastPage + 1, pages)
    }
    document.lastOpenedAt?.let {
        parts += DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    document.sizeBytes?.let { parts += Formatter.formatShortFileSize(context, it) }
    if (!document.isPersisted) parts += stringResource(R.string.reader_not_saved)
    return parts.joinToString("  ·  ")
}

@Composable
fun DocumentRow(
    document: DocumentEntity,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onLocate: () -> Unit,
    onVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(document.id, document.thumbnailPath, document.permissionLost) { onVisible() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(
            path = document.thumbnailPath,
            dimmed = document.permissionLost,
            modifier = Modifier.width(52.dp).height(72.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                document.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (document.permissionLost) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(documentSubtitle(document), style = MaterialTheme.typography.labelSmall, color = muted)
            if (document.permissionLost) {
                QuireTextButton(onClick = onLocate, modifier = Modifier.padding(top = 2.dp)) {
                    Text(stringResource(R.string.reader_locate_again))
                }
            }
        }
        MoreOptionsButton(title = document.title, onClick = onLongPress)
    }
}

/** Visible way into the context sheet; long press stays as a shortcut. */
@Composable
private fun MoreOptionsButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.library_more_options_for, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DocumentGridCell(
    document: DocumentEntity,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onLocate: () -> Unit,
    onVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(document.id, document.thumbnailPath, document.permissionLost) { onVisible() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(8.dp),
    ) {
        Thumbnail(
            path = document.thumbnailPath,
            dimmed = document.permissionLost,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                document.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (document.permissionLost) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
            MoreOptionsButton(title = document.title, onClick = onLongPress)
        }
        Text(documentSubtitle(document), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 2)
        if (document.permissionLost) {
            QuireTextButton(onClick = onLocate) { Text(stringResource(R.string.reader_locate_again)) }
        }
    }
}

/** True when the cached thumbnail file still exists; used to regenerate after a cache clear. */
fun DocumentEntity.hasThumbnailFile(): Boolean = thumbnailPath?.let { File(it).exists() } == true
