package com.leaf.app.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.ui.common.EmptyState
import com.leaf.app.ui.common.LoadingState
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.TextInputDialog
import com.leaf.app.ui.library.components.DocumentAction
import com.leaf.app.ui.library.components.DocumentContextSheet
import com.leaf.app.ui.library.components.DocumentList
import com.leaf.app.util.saf.SafAccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderId: Long,
    onBack: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: FolderViewModel = viewModel(
        key = "folder:$folderId",
        factory = viewModelFactory {
            initializer { FolderViewModel(folderId, container.folders, container.documents, container.thumbnails, container.saf) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetFor by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameFor by remember { mutableStateOf<DocumentEntity?>(null) }
    var locating by remember { mutableStateOf<DocumentEntity?>(null) }
    val locate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val doc = locating
        locating = null
        val uri = result.data?.data
        if (doc != null && result.resultCode == Activity.RESULT_OK && uri != null) {
            viewModel.relocate(doc, uri, result.data?.flags ?: 0) { rebound -> if (rebound != null) onOpenDocument(rebound) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.unavailable -> EmptyState(
                    message = stringResource(R.string.library_folder_unavailable),
                    actionLabel = stringResource(R.string.action_refresh),
                    onAction = viewModel::refresh,
                )
                state.documents.isEmpty() && state.refreshing -> LoadingState(
                    stringResource(R.string.progress_reading_folder),
                    Modifier.fillMaxSize(),
                )
                state.documents.isEmpty() -> EmptyState(message = stringResource(R.string.library_folder_empty))
                else -> DocumentList(
                    documents = state.documents,
                    layout = com.leaf.app.data.prefs.LibraryLayout.LIST,
                    onOpen = onOpenDocument,
                    onLongPress = { sheetFor = it },
                    onLocate = { doc ->
                        locating = doc
                        locate.launch(SafAccess.openPdfIntent())
                    },
                    onVisible = viewModel::requestThumbnail,
                    bottomPadding = 24.dp0,
                )
            }
        }
    }

    sheetFor?.let { doc ->
        DocumentContextSheet(
            document = doc,
            actions = listOf(DocumentAction.FAVOURITE, DocumentAction.RENAME, DocumentAction.SHARE),
            onDismiss = { sheetFor = null },
            onAction = { action ->
                sheetFor = null
                when (action) {
                    DocumentAction.FAVOURITE -> viewModel.toggleFavourite(doc)
                    DocumentAction.RENAME -> renameFor = doc
                    DocumentAction.SHARE -> context.shareDocument(doc.uri, doc.title)
                    else -> Unit
                }
            },
        )
    }

    renameFor?.let { doc ->
        TextInputDialog(
            title = stringResource(R.string.action_rename_label),
            initialValue = doc.title,
            helpText = stringResource(R.string.library_rename_help),
            onConfirm = { viewModel.rename(doc, it) },
            onDismiss = { renameFor = null },
        )
    }
}

private val Int.dp0 get() = androidx.compose.ui.unit.Dp(this.toFloat())
