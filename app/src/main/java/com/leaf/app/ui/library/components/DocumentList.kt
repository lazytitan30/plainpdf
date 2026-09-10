package com.leaf.app.ui.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.data.prefs.LibraryLayout
import com.leaf.app.ui.common.fabClearance

/** One list, two layouts. No entrance animations, no press scale. */
@Composable
fun DocumentList(
    documents: List<DocumentEntity>,
    layout: LibraryLayout,
    onOpen: (DocumentEntity) -> Unit,
    onLongPress: (DocumentEntity) -> Unit,
    onLocate: (DocumentEntity) -> Unit,
    onVisible: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = fabClearance(),
    /** Scrolls with the list; spans every column in the grid. */
    header: (@Composable () -> Unit)? = null,
) {
    when (layout) {
        LibraryLayout.LIST -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            if (header != null) item(key = "header") { header() }
            items(documents, key = { it.id }) { doc ->
                DocumentRow(
                    document = doc,
                    onOpen = { onOpen(doc) },
                    onLongPress = { onLongPress(doc) },
                    onLocate = { onLocate(doc) },
                    onVisible = { onVisible(doc) },
                )
            }
        }
        LibraryLayout.GRID -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = bottomPadding),
        ) {
            if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
            items(documents, key = { it.id }) { doc ->
                DocumentGridCell(
                    document = doc,
                    onOpen = { onOpen(doc) },
                    onLongPress = { onLongPress(doc) },
                    onLocate = { onLocate(doc) },
                    onVisible = { onVisible(doc) },
                )
            }
        }
    }
}
