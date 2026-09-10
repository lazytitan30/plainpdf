package com.leaf.app.ui.library

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.data.db.entities.FolderEntity
import com.leaf.app.data.prefs.LibraryLayout
import com.leaf.app.data.prefs.LibrarySort
import com.leaf.app.ui.common.Choice
import com.leaf.app.ui.common.ChoiceDialog
import com.leaf.app.ui.common.EmptyState
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.fabClearance
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.common.TextInputDialog
import com.leaf.app.ui.library.components.ContinueReadingCard
import com.leaf.app.ui.library.components.DocumentAction
import com.leaf.app.ui.library.components.DocumentContextSheet
import com.leaf.app.ui.library.components.DocumentList
import com.leaf.app.ui.scan.DocumentCropScreen
import com.leaf.app.ui.settings.label
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.launch

/**
 * A scan asked for from outside the Library. With no [sharedPhotos] the camera opens; with
 * some, each goes through the crop step in turn as if it had just been taken.
 */
data class ScanRequest(val nonce: Long, val sharedPhotos: List<Uri> = emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    /** Changes when a shortcut, the widget or the share sheet asks for a scan; each value starts one. */
    scanRequest: ScanRequest? = null,
    onOpenSettings: () -> Unit,
    onOpenSupporter: () -> Unit,
    onOpenOcrLanguages: () -> Unit = {},
    onOpenPdf: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onOpenFolder: (FolderEntity) -> Unit,
    onOrganise: (DocumentEntity) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LibraryViewModel(container.documents, container.folders, container.thumbnails, container.settings, container.saf)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanViewModel: ScanViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ScanViewModel(context.applicationContext, container.operationRunner, container.operations, container.documents, container.settings, container.tessdata, container.languagePacks) }
        },
    )
    val scan by scanViewModel.state.collectAsStateWithLifecycle()
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto?.let(Uri::parse)
        pendingPhoto = null
        if (ok && uri != null) scanViewModel.onPhotoTaken(uri)
    }
    val startScan: () -> Unit = {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingPhoto = uri.toString()
        runCatching { takePhoto.launch(uri) }.onFailure { pendingPhoto = null }
        Unit
    }
    LaunchedEffect(scanRequest) {
        when {
            scanRequest == null -> Unit
            scanRequest.sharedPhotos.isEmpty() -> startScan()
            else -> scanViewModel.onPhotosShared(scanRequest.sharedPhotos)
        }
    }
    val saveScanElsewhere = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val dest = result.data?.data
        val src = scan.output?.uri
        if (result.resultCode == Activity.RESULT_OK && dest != null && src != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(src)?.use { input ->
                        context.contentResolver.openOutputStream(dest, "wt")?.use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var sortDialog by rememberSaveable { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameFor by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteFor by remember { mutableStateOf<DocumentEntity?>(null) }
    var folderSheetFor by remember { mutableStateOf<FolderEntity?>(null) }
    var locating by remember { mutableStateOf<DocumentEntity?>(null) }

    val deletedMsg = stringResource(R.string.library_deleted)
    val deleteFailedMsg = stringResource(R.string.library_delete_failed)
    val folderFailedMsg = stringResource(R.string.library_folder_failed)

    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.addFolder(uri) { folder ->
                if (folder == null) scope.launch { snackbar.showSnackbar(folderFailedMsg) }
            }
        }
    }
    val locate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val doc = locating
        locating = null
        val uri = result.data?.data
        if (doc != null && result.resultCode == Activity.RESULT_OK && uri != null) {
            viewModel.relocate(doc, uri, result.data?.flags ?: 0) { rebound -> if (rebound != null) onOpenDocument(rebound) }
        }
    }

    if (state.searching) BackHandler { viewModel.setSearching(false) }

    Scaffold(
        topBar = {
            if (state.searching) {
                SearchBar(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onClose = { viewModel.setSearching(false) },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        // The Supporter entry lives here too, not only in Settings: a filled
                        // heart once someone has supported, an outline until then.
                        val isSupporter by container.supporter.isSupporter.collectAsStateWithLifecycle()
                        IconButton(onClick = onOpenSupporter) {
                            Icon(
                                if (isSupporter) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(R.string.supporter_title),
                            )
                        }
                        IconButton(onClick = { viewModel.setSearching(true) }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.library_search))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_sort)) },
                                    onClick = { menuOpen = false; sortDialog = true },
                                )
                                val otherLayout = if (state.layout == LibraryLayout.LIST) LibraryLayout.GRID else LibraryLayout.LIST
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (otherLayout == LibraryLayout.GRID) R.string.library_view_grid else R.string.library_view_list,
                                            ),
                                        )
                                    },
                                    onClick = { menuOpen = false; viewModel.setLayout(otherLayout) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_title)) },
                                    onClick = { menuOpen = false; onOpenSettings() },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        floatingActionButton = {
            val scanButton: @Composable () -> Unit = {
                ExtendedFloatingActionButton(
                    onClick = startScan,
                    icon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = null) },
                    text = { Text(stringResource(R.string.library_scan)) },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            val openButton: @Composable () -> Unit = {
                ExtendedFloatingActionButton(
                    onClick = onOpenPdf,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_open_pdf)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
            // A phone on its side has little height to spare: the two buttons stacked would
            // cover the tabs, so they sit side by side there.
            if (LocalConfiguration.current.screenHeightDp < 500) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    scanButton()
                    Spacer(Modifier.width(12.dp))
                    openButton()
                }
            } else {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    scanButton()
                    Spacer(Modifier.height(12.dp))
                    openButton()
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
            ) {
                LibraryTab.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = state.tab == entry,
                        onClick = { viewModel.selectTab(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LibraryTab.entries.size),
                        // At the largest font "Favourites" no longer fits its third of the row
                        // and wrapped mid-word; shrink the label a little instead.
                        label = {
                            Text(
                                entry.label(),
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                            )
                        },
                    )
                }
            }

            if (!state.loaded) return@Column

            val commonActions = { doc: DocumentEntity -> sheetFor = doc }
            val locateAction = { doc: DocumentEntity ->
                locating = doc
                locate.launch(SafAccess.openPdfIntent())
            }

            when (state.tab) {
                LibraryTab.RECENT -> if (state.recent.isEmpty()) {
                    EmptyState(
                        message = stringResource(if (state.query.isBlank()) R.string.library_empty_recent else R.string.library_no_matches),
                        actionLabel = if (state.query.isBlank()) stringResource(R.string.library_open_pdf) else null,
                        onAction = onOpenPdf,
                        modifier = Modifier.padding(bottom = fabClearance()),
                    )
                } else {
                    val continueDoc = state.continueReading
                    val header: (@Composable () -> Unit)? = if (continueDoc == null) null else {
                        {
                            ContinueReadingCard(
                                document = continueDoc,
                                onContinue = { onOpenDocument(continueDoc) },
                                onVisible = { viewModel.requestThumbnail(continueDoc) },
                            )
                        }
                    }
                    DocumentList(
                        documents = state.recent,
                        layout = state.layout,
                        onOpen = onOpenDocument,
                        onLongPress = commonActions,
                        onLocate = locateAction,
                        onVisible = viewModel::requestThumbnail,
                        header = header,
                    )
                }

                LibraryTab.FAVOURITES -> if (state.favourites.isEmpty()) {
                    EmptyState(
                        message = stringResource(if (state.query.isBlank()) R.string.library_empty_favourites else R.string.library_no_matches),
                        modifier = Modifier.padding(bottom = fabClearance()),
                    )
                } else {
                    DocumentList(
                        documents = state.favourites,
                        layout = state.layout,
                        onOpen = onOpenDocument,
                        onLongPress = commonActions,
                        onLocate = locateAction,
                        onVisible = viewModel::requestThumbnail,
                    )
                }

                LibraryTab.FOLDERS -> FoldersTab(
                    folders = state.folders,
                    query = state.query,
                    onAdd = { addFolder.launch(null) },
                    onOpen = onOpenFolder,
                    onLongPress = { folderSheetFor = it },
                )
            }
        }
    }

    scan.pendingCrop?.let { photo ->
        // Keyed so a queue of shared photos gets a fresh crop screen for each, not the last one's rotation.
        key(photo) {
            DocumentCropScreen(
                source = photo,
                outputFile = scanViewModel.cropOutputFile(),
                onDone = scanViewModel::onCropDone,
                onCancel = scanViewModel::onCropCancelled,
            )
        }
    }

    if (scan.active && scan.pendingCrop == null) {
        val packStates by container.languagePacks.states.collectAsStateWithLifecycle()
        ScanSheet(
            state = scan,
            packStates = packStates,
            onGetLanguage = scanViewModel::getSuggestedLanguage,
            onDismissSuggestion = scanViewModel::dismissSuggestion,
            onOpenLanguages = onOpenOcrLanguages,
            onAddPage = startScan,
            onRemovePage = scanViewModel::removePage,
            onAdjustPage = scanViewModel::adjust,
            onShare = { context.shareDocument(it.toString(), scan.output?.displayName ?: "") },
            onOpen = { uri ->
                scanViewModel.reset()
                scope.launch {
                    val doc = container.documents.findByUri(uri.toString()) ?: container.documents.openedNow(uri.toString())
                    onOpenDocument(doc)
                }
            },
            onSaveElsewhere = { _, name ->
                saveScanElsewhere.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = SafAccess.PDF_MIME
                        putExtra(Intent.EXTRA_TITLE, name)
                    },
                )
            },
            onCopyText = scanViewModel::copyText,
            onTextCopiedShown = scanViewModel::textCopiedShown,
            onDone = scanViewModel::reset,
        )
    }

    if (sortDialog) {
        ChoiceDialog(
            title = stringResource(R.string.library_sort),
            choices = LibrarySort.entries.map { Choice(it, it.label()) },
            selected = state.sort,
            onSelect = viewModel::setSort,
            onDismiss = { sortDialog = false },
        )
    }

    sheetFor?.let { doc ->
        val actions = buildList {
            add(DocumentAction.FAVOURITE)
            add(DocumentAction.RENAME)
            if (!doc.permissionLost) add(DocumentAction.SHARE)
            if (!doc.permissionLost) add(DocumentAction.ORGANISE)
            if (state.tab == LibraryTab.RECENT) add(DocumentAction.REMOVE_FROM_RECENTS)
            if (!doc.permissionLost) add(DocumentAction.DELETE_FILE)
        }
        DocumentContextSheet(
            document = doc,
            actions = actions,
            onDismiss = { sheetFor = null },
            onAction = { action ->
                sheetFor = null
                when (action) {
                    DocumentAction.FAVOURITE -> viewModel.toggleFavourite(doc)
                    DocumentAction.RENAME -> renameFor = doc
                    DocumentAction.SHARE -> context.shareDocument(doc.uri, doc.title)
                    DocumentAction.ORGANISE -> onOrganise(doc)
                    DocumentAction.REMOVE_FROM_RECENTS -> viewModel.removeFromRecents(doc)
                    DocumentAction.DELETE_FILE -> deleteFor = doc
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

    deleteFor?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            shape = QuireShape.Dialog,
            title = { Text(stringResource(R.string.library_delete_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.library_delete_body, doc.title), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                QuireTextButton(onClick = {
                    deleteFor = null
                    viewModel.deleteFile(doc) { ok ->
                        scope.launch { snackbar.showSnackbar(if (ok) deletedMsg else deleteFailedMsg) }
                    }
                }) { Text(stringResource(R.string.action_delete_file), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { QuireTextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    folderSheetFor?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderSheetFor = null },
            shape = QuireShape.Dialog,
            title = { Text(folder.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
            text = { Text(stringResource(R.string.library_remove_folder_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                QuireTextButton(onClick = {
                    folderSheetFor = null
                    viewModel.removeFolder(folder)
                }) { Text(stringResource(R.string.action_remove_folder)) }
            },
            dismissButton = { QuireTextButton(onClick = { folderSheetFor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun FoldersTab(
    folders: List<FolderEntity>,
    query: String,
    onAdd: () -> Unit,
    onOpen: (FolderEntity) -> Unit,
    onLongPress: (FolderEntity) -> Unit,
) {
    if (folders.isEmpty()) {
        EmptyState(
            message = stringResource(if (query.isBlank()) R.string.library_empty_folders else R.string.library_no_matches),
            actionLabel = if (query.isBlank()) stringResource(R.string.library_add_folder) else null,
            onAction = onAdd,
            modifier = Modifier.padding(bottom = fabClearance()),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = fabClearance())) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.library_add_folder), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        items(folders, key = { it.id }) { folder ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onOpen(folder) }, onLongClick = { onLongPress(folder) })
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(folder.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    Text(
                        folder.treeUri.substringAfter("tree/").replace("%3A", ":").replace("%2F", "/"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                // Same sheet the long press opens, for people who never discover long press.
                IconButton(onClick = { onLongPress(folder) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.library_more_options_for, folder.displayName),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTab.label(): String = when (this) {
    LibraryTab.RECENT -> stringResource(R.string.library_tab_recent)
    LibraryTab.FOLDERS -> stringResource(R.string.library_tab_folders)
    LibraryTab.FAVOURITES -> stringResource(R.string.library_tab_favourites)
}
