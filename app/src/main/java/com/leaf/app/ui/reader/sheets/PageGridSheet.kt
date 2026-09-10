package com.leaf.app.ui.reader.sheets

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing

/** Outline fallback: the document has no outline we can read, so show every page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageGridSheet(
    pageCount: Int,
    currentPage: Int,
    colorFilter: ColorFilter?,
    render: suspend (page: Int, widthPx: Int) -> Bitmap?,
    onPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (currentPage - 3).coerceAtLeast(0))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = QuireShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Text(
                stringResource(R.string.reader_pages_title, pageCount),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items((0 until pageCount).toList(), key = { it }) { page ->
                    PageCell(
                        page = page,
                        selected = page == currentPage,
                        colorFilter = colorFilter,
                        render = render,
                        onClick = { onPage(page) },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { gridState.scrollToItem((currentPage - 3).coerceAtLeast(0)) }
}

@Composable
fun PageCell(
    page: Int,
    selected: Boolean,
    colorFilter: ColorFilter?,
    render: suspend (page: Int, widthPx: Int) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    widthPx: Int = 240,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = page) { value = render(page, widthPx) }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val description = if (selected) stringResource(R.string.reader_page_current, page + 1) else stringResource(R.string.reader_page_n, page + 1)
    Column(
        modifier
            .padding(4.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(QuireShape.Thumbnail)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(if (selected) 2.dp else 1.dp, border, QuireShape.Thumbnail),
            contentAlignment = Alignment.TopCenter,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            (page + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
