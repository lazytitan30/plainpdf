package com.leaf.app.ui.reader

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.data.db.entities.BookmarkEntity
import com.leaf.app.data.pdf.read.PdfHandle
import com.leaf.app.data.prefs.PageDisplayMode
import com.leaf.app.data.prefs.ReadingMode
import com.leaf.app.ui.LocalVolumeKeys
import com.leaf.app.ui.common.Choice
import com.leaf.app.ui.common.ChoiceDialog
import com.leaf.app.ui.common.EmptyState
import com.leaf.app.ui.common.LoadingState
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.common.TextInputDialog
import com.leaf.app.ui.library.shareDocument
import com.leaf.app.ui.library.shareFile
import com.leaf.app.ui.reader.sheets.BookmarksSheet
import com.leaf.app.ui.reader.sheets.DocumentInfoDialog
import com.leaf.app.ui.reader.sheets.PageGridSheet
import com.leaf.app.ui.settings.label
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHROME_AUTO_HIDE_MS = 6_000L
private const val CHROME_MOTION_MS = 180

private enum class ReaderDialog { NONE, JUMP, ADD_BOOKMARK, PAGE_DISPLAY, READING_MODE, INFO, SHARE_PAGE, DISCARD_FORM, DISCARD_ANNOTATIONS }

/**
 * Tools reachable from inside the reader, each opening pre-filled with the current document.
 * [ANNOTATE], [READ_ALOUD], [COPY_TEXT] and [PRINT] stay in the reader; the rest navigate.
 */
enum class ReaderTool { ANNOTATE, READ_ALOUD, COPY_TEXT, PRINT, SIGN, ORGANISE, MERGE, SPLIT, COMPRESS, PAGE_NUMBERS, MAKE_SEARCHABLE, REDACT, PDF_TO_IMAGES, PASSWORD }

/**
 * The page is the hero. Chrome is thin, translucent, and disappears after six seconds.
 * In continuous mode a single tap anywhere on the page (except links and form fields)
 * toggles it; in paged mode only the centre third does, because the outer thirds turn pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uri: String,
    onBack: () -> Unit,
    onRelocated: (String) -> Unit,
    onTool: (ReaderTool) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: ReaderViewModel = viewModel(
        key = "reader:$uri",
        factory = viewModelFactory {
            initializer { ReaderViewModel(uri, context.applicationContext, container.documents, container.bookmarks, container.settings, container.saf, java.io.File(context.cacheDir, "work"), container.appScope) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val colorFilter = remember(state.pageDisplayMode) { PageDisplayFilters.composeFilterFor(state.pageDisplayMode) }

    val accent = MaterialTheme.colorScheme.primary
    val palette = com.leaf.app.ui.theme.LocalPalette.current
    val highlightBase = with(com.leaf.app.ui.settings.HighlightColors) { state.highlightSet.color(palette.isDark, accent) }
    LaunchedEffect(highlightBase) {
        // The viewer multiplies the highlight onto the page, like a real highlighter pen, so
        // the colour must be an opaque pale tint: a translucent one comes out as a dark box.
        val white = androidx.compose.ui.graphics.Color.White
        val match = androidx.compose.ui.graphics.lerp(highlightBase, white, 0.7f).copy(alpha = 1f)
        val current = androidx.compose.ui.graphics.lerp(highlightBase, white, 0.45f).copy(alpha = 1f)
        viewModel.setHighlightColors(match.toArgb(), current.toArgb())
    }

    var dialog by rememberSaveable { mutableStateOf(ReaderDialog.NONE) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var pagesSheet by rememberSaveable { mutableStateOf(false) }
    var bookmarksSheet by rememberSaveable { mutableStateOf(false) }
    var renameBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var toolsSheet by rememberSaveable { mutableStateOf(false) }
    val savedFilledMsg = stringResource(R.string.reader_fill_saved)
    val saveFailedMsg = stringResource(R.string.ops_error_destination)
    val openLabel = stringResource(R.string.action_open)
    val searchNeedsContinuousMsg = stringResource(R.string.reader_search_needs_continuous)
    val saveFilled = com.leaf.app.ui.tools.common.rememberCreatePdfPicker { destination ->
        scope.launch {
            val ok = viewModel.saveFilledCopy(destination)
            if (ok) {
                viewModel.setFormFilling(false)
                val result = snackbar.showSnackbar(savedFilledMsg, actionLabel = openLabel)
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onRelocated(destination.toString())
            } else {
                snackbar.showSnackbar(saveFailedMsg)
            }
        }
    }

    val savedAnnotatedMsg = stringResource(R.string.reader_annotated_saved)
    val saveAnnotated = com.leaf.app.ui.tools.common.rememberCreatePdfPicker { destination ->
        scope.launch {
            val ok = viewModel.saveAnnotatedCopy(destination)
            if (ok) {
                viewModel.setAnnotating(false)
                val result = snackbar.showSnackbar(savedAnnotatedMsg, actionLabel = openLabel)
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onRelocated(destination.toString())
            } else {
                snackbar.showSnackbar(saveFailedMsg)
            }
        }
    }
    // Leaving drawing mode throws the strokes away, so every exit goes through this.
    val leaveAnnotating: () -> Unit = {
        if (viewModel.hasUnsavedAnnotationEdits()) dialog = ReaderDialog.DISCARD_ANNOTATIONS else viewModel.setAnnotating(false)
    }

    // One-off messages from the text features. The missing-voice one leads to the system settings.
    val noVoiceMsg = stringResource(R.string.reader_tts_no_voice)
    val noTextMsg = stringResource(R.string.reader_tts_no_text)
    val passwordMsg = stringResource(R.string.ops_error_password)
    val failedMsg = stringResource(R.string.ops_error_unknown)
    val printUnavailableMsg = stringResource(R.string.reader_print_unavailable)
    val settingsLabel = stringResource(R.string.reader_tts_settings)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ReaderEvent.NO_VOICE -> {
                    val result = snackbar.showSnackbar(noVoiceMsg, actionLabel = settingsLabel, duration = androidx.compose.material3.SnackbarDuration.Long)
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) context.openTextToSpeechSettings()
                }
                ReaderEvent.NO_TEXT -> snackbar.showSnackbar(noTextMsg)
                ReaderEvent.PASSWORD_REQUIRED -> snackbar.showSnackbar(passwordMsg)
                ReaderEvent.FAILED -> snackbar.showSnackbar(failedMsg)
                ReaderEvent.PRINT_UNAVAILABLE -> snackbar.showSnackbar(printUnavailableMsg)
            }
        }
    }
    // A page exported for sharing: the chooser wants the activity context, not the application one.
    LaunchedEffect(viewModel) {
        viewModel.pageShares.collect { shared -> context.shareFile(shared.file, shared.mime, shared.file.nameWithoutExtension) }
    }
    val activity = LocalActivity.current
    val print: () -> Boolean = {
        // The print dialog wants an activity behind it; the application context has none.
        (activity ?: context).printPdf(state.uri, state.displayName, state.pageCount, container.appScope)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flushPosition() }
    SystemBars(visible = state.chromeVisible || state.annotating)
    KeepScreenOn(state.keepScreenOn)
    VolumeKeyPaging(enabled = state.volumeKeysTurnPages && state.pageCount > 0) { delta -> viewModel.turnPage(delta) }

    BackHandler(enabled = state.search.active) { viewModel.closeSearch() }
    BackHandler(enabled = !state.search.active && state.annotating) { leaveAnnotating() }
    BackHandler(enabled = !state.search.active && !state.annotating) { onBack() }

    // Auto-hide after a quiet spell; interacting with the chrome resets the timer.
    var chromeTouchNonce by remember { mutableStateOf(0) }
    LaunchedEffect(state.chromeVisible, chromeTouchNonce, state.search.active, menuOpen) {
        if (state.chromeVisible && !state.search.active && !menuOpen) {
            delay(CHROME_AUTO_HIDE_MS)
            viewModel.setChromeVisible(false)
        }
    }

    val locate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val newUri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && newUri != null) {
            container.saf.takePersistable(newUri, result.data?.flags ?: 0)
            scope.launch { if (viewModel.relocate(newUri.toString())) onRelocated(newUri.toString()) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.loadError -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    message = stringResource(R.string.reader_error_open),
                    actionLabel = stringResource(R.string.reader_locate_again),
                    onAction = { locate.launch(SafAccess.openPdfIntent()) },
                    modifier = Modifier.weight(1f),
                )
                QuireTextButton(onClick = onBack, modifier = Modifier.padding(bottom = 48.dp)) { Text(stringResource(R.string.action_back)) }
            }
            !state.ready -> LoadingState(stringResource(R.string.progress_opening_document), Modifier.fillMaxSize())
            state.readingMode == ReadingMode.PAGED -> PagedHost(viewModel = viewModel, uri = state.uri)
            else -> PdfViewerHost(uri = state.uri, viewModel = viewModel, modifier = Modifier.fillMaxSize())
        }

        if (state.edgeBrightness && !state.loadError) {
            BrightnessEdge(Modifier.align(Alignment.CenterStart))
        }

        // Top chrome: search bar replaces the title bar while search is active. While drawing,
        // the chrome gives way to the drawing bar above and the ink toolbar below.
        AnimatedVisibility(
            visible = (state.chromeVisible || state.search.active) && !state.loadError && !state.annotating,
            enter = fadeIn(tween(CHROME_MOTION_MS)) + slideInVertically(tween(CHROME_MOTION_MS)) { -it / 8 },
            exit = fadeOut(tween(CHROME_MOTION_MS)) + slideOutVertically(tween(CHROME_MOTION_MS)) { -it / 8 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (state.search.active) {
                SearchTopBar(
                    search = state.search,
                    onQueryChange = viewModel::setSearchQuery,
                    onPrevious = viewModel::previousMatch,
                    onNext = viewModel::nextMatch,
                    onClose = viewModel::closeSearch,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(state.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        // Never silently disabled: in paged mode the tap explains why search is off.
                        IconButton(onClick = {
                            chromeTouchNonce++
                            if (state.readingMode == ReadingMode.CONTINUOUS) {
                                viewModel.openSearch()
                            } else {
                                scope.launch { snackbar.showSnackbar(searchNeedsContinuousMsg) }
                            }
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.reader_search))
                        }
                        IconButton(onClick = { chromeTouchNonce++; pagesSheet = true }, enabled = state.pageCount > 0) {
                            Icon(Icons.Filled.List, contentDescription = stringResource(R.string.reader_outline))
                        }
                        Box {
                            IconButton(onClick = { chromeTouchNonce++; menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_more))
                            }
                            ReaderMenu(
                                expanded = menuOpen,
                                keepScreenOn = state.keepScreenOn,
                                onDismiss = { menuOpen = false },
                                onJump = { dialog = ReaderDialog.JUMP },
                                onAddBookmark = { dialog = ReaderDialog.ADD_BOOKMARK },
                                onBookmarks = { bookmarksSheet = true },
                                onPageDisplay = { dialog = ReaderDialog.PAGE_DISPLAY },
                                onReadingMode = { dialog = ReaderDialog.READING_MODE },
                                onKeepScreenOn = { viewModel.setKeepScreenOn(!state.keepScreenOn) },
                                onShare = { context.shareDocument(state.uri.toString(), state.displayName) },
                                onSharePage = { dialog = ReaderDialog.SHARE_PAGE },
                                onInfo = { dialog = ReaderDialog.INFO },
                                onTool = { viewModel.flushPosition(); viewModel.stopReadAloud(); onTool(it) },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        }

        AnimatedVisibility(
            visible = state.chromeVisible && !state.search.active && !state.loadError && !state.annotating && state.pageCount > 0,
            enter = fadeIn(tween(CHROME_MOTION_MS)) + slideInVertically(tween(CHROME_MOTION_MS)) { it / 8 },
            exit = fadeOut(tween(CHROME_MOTION_MS)) + slideOutVertically(tween(CHROME_MOTION_MS)) { it / 8 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PageBar(
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                colorFilter = colorFilter,
                preview = { page -> viewModel.pageThumbnail(page, 200) },
                onInteract = { chromeTouchNonce++ },
                onSeek = viewModel::goToPage,
            )
        }

        // Always visible: the one-tap way into every tool, independent of the auto-hiding chrome.
        // Except while drawing: the bottom of the screen belongs to the ink toolbar then.
        if (!state.loadError && state.ready && !state.annotating) {
            val lift = when {
                state.formFilling && state.readAloud != null -> 168.dp
                state.formFilling || state.readAloud != null -> 96.dp
                state.chromeVisible && state.pageCount > 0 -> 88.dp
                else -> 24.dp
            }
            androidx.compose.material3.SmallFloatingActionButton(
                onClick = { chromeTouchNonce++; toolsSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = QuireShape.Card,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = lift),
            ) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_tools), contentDescription = stringResource(R.string.reader_tools_button))
            }
        }

        // Bottom bars: reading aloud and form filling can both be on; they stack.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenHorizontal, vertical = 16.dp),
        ) {
            state.readAloud?.let { reading ->
                BottomBarCard {
                    Text(stringResource(R.string.reader_reading_page, reading.page + 1), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    QuireTextButton(onClick = { if (reading.paused) viewModel.resumeReadAloud() else viewModel.pauseReadAloud() }) {
                        Text(stringResource(if (reading.paused) R.string.action_resume else R.string.action_pause))
                    }
                    QuireButton(onClick = viewModel::stopReadAloud) { Text(stringResource(R.string.action_stop)) }
                }
                if (state.formFilling) Spacer(Modifier.height(8.dp))
            }
            if (state.formFilling) {
                BottomBarCard {
                    Text(stringResource(R.string.reader_fill_active), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    QuireTextButton(onClick = {
                        if (viewModel.hasUnsavedFormEdits()) dialog = ReaderDialog.DISCARD_FORM else viewModel.setFormFilling(false)
                    }) { Text(stringResource(R.string.action_cancel)) }
                    QuireButton(onClick = { saveFilled.pick(com.leaf.app.util.saf.OutputNames.build(com.leaf.app.util.saf.OutputNames.DEFAULT_PATTERN, state.displayName, "filled")) }) {
                        Text(stringResource(R.string.reader_save_filled))
                    }
                }
            }
        }

        // Drawing bar. It sits at the top because the library anchors its pen, highlighter and
        // eraser toolbar to the bottom edge, and nothing of ours may cover that.
        if (state.annotating) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = QuireShape.Card,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.screenHorizontal, vertical = 16.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.reader_annotate_active), style = MaterialTheme.typography.labelMedium)
                    Text(
                        stringResource(R.string.reader_annotate_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        QuireTextButton(onClick = leaveAnnotating) { Text(stringResource(R.string.action_cancel)) }
                        QuireButton(onClick = { saveAnnotated.pick(com.leaf.app.util.saf.OutputNames.build(com.leaf.app.util.saf.OutputNames.DEFAULT_PATTERN, state.displayName, "annotated")) }) {
                            Text(stringResource(R.string.reader_save_annotated))
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (toolsSheet) {
        ReaderToolsSheet(
            documentName = state.displayName,
            hasForm = state.hasForm,
            formFilling = state.formFilling,
            hasAnnotations = state.hasAnnotations && state.readingMode == ReadingMode.CONTINUOUS,
            annotating = state.annotating,
            readingAloud = state.readAloud != null,
            onFillForm = { toolsSheet = false; viewModel.setFormFilling(!state.formFilling) },
            onTool = { tool ->
                toolsSheet = false
                when (tool) {
                    ReaderTool.ANNOTATE -> if (state.annotating) leaveAnnotating() else viewModel.setAnnotating(true)
                    ReaderTool.READ_ALOUD -> if (state.readAloud == null) viewModel.startReadAloud() else viewModel.stopReadAloud()
                    ReaderTool.COPY_TEXT -> viewModel.extractText()
                    ReaderTool.PRINT -> viewModel.printAfterPasswordCheck(print)
                    else -> {
                        viewModel.flushPosition()
                        viewModel.stopReadAloud()
                        onTool(tool)
                    }
                }
            },
            onDismiss = { toolsSheet = false },
        )
    }

    state.busy?.let { busy ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            shape = QuireShape.Dialog,
            text = {
                LoadingState(stringResource(if (busy == ReaderBusy.EXTRACTING_TEXT) R.string.progress_reading_pages else R.string.progress_opening_document), Modifier.fillMaxWidth())
            },
            confirmButton = { QuireTextButton(onClick = viewModel::cancelBusy) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    state.extractedText?.let { text ->
        val copiedMsg = stringResource(R.string.reader_text_copied)
        ExtractedTextDialog(
            text = text,
            onCopy = {
                context.copyTextToClipboard(text)
                viewModel.dismissExtractedText()
                scope.launch { snackbar.showSnackbar(copiedMsg) }
            },
            onShare = { context.shareText(text, state.displayName); viewModel.dismissExtractedText() },
            onDismiss = viewModel::dismissExtractedText,
        )
    }

    // ---- Sheets and dialogs ----

    if (pagesSheet) {
        PageGridSheet(
            pageCount = state.pageCount,
            currentPage = state.currentPage,
            colorFilter = colorFilter,
            render = { page, w -> viewModel.pageThumbnail(page, w) },
            onPage = { pagesSheet = false; viewModel.goToPage(it) },
            onDismiss = { pagesSheet = false },
        )
    }

    if (bookmarksSheet) {
        BookmarksSheet(
            bookmarks = bookmarks,
            colorFilter = colorFilter,
            render = { page, w -> viewModel.pageThumbnail(page, w) },
            onOpen = { bookmarksSheet = false; viewModel.goToPage(it.pageIndex) },
            onRename = { renameBookmark = it },
            onDelete = { viewModel.removeBookmark(it.id) },
            onDismiss = { bookmarksSheet = false },
        )
    }

    renameBookmark?.let { bookmark ->
        TextInputDialog(
            title = stringResource(R.string.reader_bookmark_label),
            initialValue = bookmark.label ?: "",
            onConfirm = { viewModel.relabelBookmark(bookmark.id, it) },
            onDismiss = { renameBookmark = null },
        )
    }

    val addedMsg = stringResource(R.string.reader_bookmark_added)
    when (dialog) {
        ReaderDialog.NONE -> Unit
        ReaderDialog.JUMP -> JumpToPageDialog(
            pageCount = state.pageCount,
            onJump = { viewModel.goToPage(it - 1) },
            onDismiss = { dialog = ReaderDialog.NONE },
        )
        ReaderDialog.ADD_BOOKMARK -> TextInputDialog(
            title = stringResource(R.string.reader_add_bookmark_title, state.currentPage + 1),
            initialValue = "",
            helpText = stringResource(R.string.reader_bookmark_label_help),
            confirmLabel = stringResource(R.string.reader_add_bookmark),
            onConfirm = { label ->
                viewModel.addBookmark(label)
                scope.launch { snackbar.showSnackbar(addedMsg) }
            },
            onDismiss = { dialog = ReaderDialog.NONE },
        )
        ReaderDialog.PAGE_DISPLAY -> ChoiceDialog(
            title = stringResource(R.string.reader_page_display),
            choices = PageDisplayMode.entries.map { Choice(it, it.label()) },
            selected = state.pageDisplayMode,
            onSelect = viewModel::setPageDisplayMode,
            onDismiss = { dialog = ReaderDialog.NONE },
        )
        ReaderDialog.READING_MODE -> ChoiceDialog(
            title = stringResource(R.string.reader_reading_mode),
            choices = ReadingMode.entries.map { Choice(it, it.label()) },
            selected = state.readingMode,
            onSelect = viewModel::setReadingMode,
            onDismiss = { dialog = ReaderDialog.NONE },
        )
        ReaderDialog.INFO -> DocumentInfoDialog(
            displayName = state.displayName,
            uri = state.uri,
            pageCount = state.pageCount,
            sizeBytes = state.sizeBytes,
            onDismiss = { dialog = ReaderDialog.NONE },
        )
        ReaderDialog.SHARE_PAGE -> SharePageDialog(
            pageNumber = state.currentPage + 1,
            onChoose = { format -> dialog = ReaderDialog.NONE; viewModel.shareCurrentPage(format) },
            onDismiss = { dialog = ReaderDialog.NONE },
        )
        ReaderDialog.DISCARD_FORM -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = ReaderDialog.NONE },
            shape = QuireShape.Dialog,
            title = { Text(stringResource(R.string.reader_discard_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.reader_discard_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                QuireTextButton(onClick = { dialog = ReaderDialog.NONE; viewModel.setFormFilling(false) }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                QuireTextButton(onClick = { dialog = ReaderDialog.NONE }) { Text(stringResource(R.string.action_keep_editing)) }
            },
        )
        ReaderDialog.DISCARD_ANNOTATIONS -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = ReaderDialog.NONE },
            shape = QuireShape.Dialog,
            title = { Text(stringResource(R.string.reader_discard_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.reader_discard_annotations_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                QuireTextButton(onClick = { dialog = ReaderDialog.NONE; viewModel.setAnnotating(false) }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                QuireTextButton(onClick = { dialog = ReaderDialog.NONE }) { Text(stringResource(R.string.action_keep_editing)) }
            },
        )
    }
}

/** Paged mode opens its own handle through the engine; the androidx view only scrolls vertically. */
@Composable
private fun PagedHost(viewModel: ReaderViewModel, uri: Uri) {
    val container = LocalAppContainer.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val target by viewModel.pagedTarget.collectAsStateWithLifecycle()
    val handle by produceState<Result<PdfHandle>?>(initialValue = null, key1 = uri) {
        value = container.pdfEngine.open(uri)
    }
    // Key on the opened handle itself so the dispose closes what this effect saw, not the
    // newer value a state read at dispose time would return.
    val opened = handle?.getOrNull()
    DisposableEffect(opened) {
        onDispose { opened?.close() }
    }
    val colorFilter = remember(state.pageDisplayMode) { PageDisplayFilters.composeFilterFor(state.pageDisplayMode) }
    when (val result = handle) {
        null -> LoadingState(stringResource(R.string.progress_opening_document), Modifier.fillMaxSize())
        else -> {
            val open = result.getOrNull()
            if (open == null) {
                // Password protected or unreadable through the engine: fall back to continuous mode.
                LaunchedEffect(Unit) { viewModel.setReadingMode(ReadingMode.CONTINUOUS) }
            } else {
                LaunchedEffect(open.pageCount) { if (state.pageCount != open.pageCount) viewModel.onDocumentLoaded(open.pageCount) }
                PagedReader(
                    handle = open,
                    startPage = state.pagedStartPage,
                    targetPage = target,
                    onTargetConsumed = { viewModel.pagedTarget.value = null },
                    onPageChanged = viewModel::onPagedPageChanged,
                    onCentreTap = viewModel::onCentreTap,
                    doubleTapZoom = state.doubleTapZoom,
                    colorFilter = colorFilter,
                )
            }
        }
    }
}

@Composable
private fun ReaderMenu(
    expanded: Boolean,
    keepScreenOn: Boolean,
    onDismiss: () -> Unit,
    onJump: () -> Unit,
    onAddBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onPageDisplay: () -> Unit,
    onReadingMode: () -> Unit,
    onKeepScreenOn: () -> Unit,
    onShare: () -> Unit,
    onSharePage: () -> Unit,
    onInfo: () -> Unit,
    onTool: (ReaderTool) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val item: @Composable (Int, () -> Unit) -> Unit = { res, action ->
            DropdownMenuItem(text = { Text(stringResource(res)) }, onClick = { onDismiss(); action() })
        }
        item(R.string.reader_jump_to_page, onJump)
        item(R.string.reader_add_bookmark, onAddBookmark)
        item(R.string.reader_bookmarks, onBookmarks)
        item(R.string.reader_page_display, onPageDisplay)
        item(R.string.reader_reading_mode, onReadingMode)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.reader_keep_screen_on)) },
            trailingIcon = { Checkbox(checked = keepScreenOn, onCheckedChange = null) },
            onClick = { onDismiss(); onKeepScreenOn() },
        )
        item(R.string.action_share, onShare)
        item(R.string.reader_share_page, onSharePage)
        item(R.string.reader_document_info, onInfo)
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            stringResource(R.string.reader_tools_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        item(R.string.tool_sign) { onTool(ReaderTool.SIGN) }
        item(R.string.tool_organise) { onTool(ReaderTool.ORGANISE) }
        item(R.string.reader_tool_merge) { onTool(ReaderTool.MERGE) }
        item(R.string.tool_split) { onTool(ReaderTool.SPLIT) }
        item(R.string.tool_compress) { onTool(ReaderTool.COMPRESS) }
        item(R.string.tool_pdf_to_images) { onTool(ReaderTool.PDF_TO_IMAGES) }
        item(R.string.tool_password) { onTool(ReaderTool.PASSWORD) }
        item(R.string.tool_page_numbers) { onTool(ReaderTool.PAGE_NUMBERS) }
        item(R.string.tool_make_searchable) { onTool(ReaderTool.MAKE_SEARCHABLE) }
        item(R.string.tool_redact) { onTool(ReaderTool.REDACT) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val counter = when {
        search.query.isBlank() -> ""
        search.searching -> "…"
        search.matches.isEmpty() -> stringResource(R.string.reader_search_none)
        else -> stringResource(R.string.reader_search_count, search.current + 1, search.matches.size)
    }
    TopAppBar(
        title = {
            TextField(
                value = search.query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                trailingIcon = { Text(counter, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_back)) }
        },
        actions = {
            IconButton(onClick = onPrevious, enabled = search.matches.isNotEmpty()) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.reader_search_previous))
            }
            IconButton(onClick = onNext, enabled = search.matches.isNotEmpty()) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.reader_search_next))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.97f)),
        modifier = Modifier.statusBarsPadding(),
    )
}

@Composable
private fun PageBar(
    currentPage: Int,
    pageCount: Int,
    colorFilter: androidx.compose.ui.graphics.ColorFilter?,
    preview: suspend (Int) -> android.graphics.Bitmap?,
    onInteract: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue.toInt() else currentPage
    val label = stringResource(R.string.reader_page_of, shown + 1, pageCount)
    val seekDescription = stringResource(R.string.reader_jump_to_page)
    val density = LocalDensity.current

    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = if (dragging) dragValue else currentPage.toFloat(),
                    onValueChange = {
                        dragging = true
                        dragValue = it
                        onInteract()
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onSeek(dragValue.toInt())
                    },
                    valueRange = 0f..(pageCount - 1).coerceAtLeast(0).toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = seekDescription },
                )
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        // The one place the app spends boldness: a floating preview of the target page.
        if (dragging && pageCount > 1) {
            val fraction = dragValue / (pageCount - 1).coerceAtLeast(1)
            val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = shown) { value = preview(shown) }
            val cardWidth = 120.dp
            val trackInset = Spacing.screenHorizontal + 8.dp
            val labelWidth = 72.dp
            val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val trackWidth = screenWidth - trackInset * 2 - labelWidth
            val xDp = (trackInset + trackWidth * fraction - cardWidth / 2)
                .coerceIn(8.dp, (screenWidth - cardWidth - 8.dp).coerceAtLeast(8.dp))
            val xOffset = with(density) { xDp.roundToPx() }
            Surface(
                shape = QuireShape.Card,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .offset { IntOffset(xOffset, 0) }
                    .offset(y = (-150).dp)
                    .width(cardWidth),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(cardWidth).height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.TopCenter) {
                        bitmap?.let {
                            Image(it.asImageBitmap(), null, contentScale = ContentScale.FillWidth, colorFilter = colorFilter, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Text(
                        (shown + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** The compact card the reader uses for a mode that stays on until the user ends it. */
@Composable
private fun BottomBarCard(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = QuireShape.Card,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Shows the first lines so the user can tell the words came out right before copying. */
@Composable
private fun ExtractedTextDialog(text: String, onCopy: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuireShape.Dialog,
        title = { Text(stringResource(R.string.reader_copy_text), style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text.take(EXTRACT_PREVIEW_CHARS),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        },
        confirmButton = { QuireButton(onClick = onCopy) { Text(stringResource(R.string.action_copy)) } },
        dismissButton = { QuireTextButton(onClick = onShare) { Text(stringResource(R.string.action_share_text)) } },
    )
}

private const val EXTRACT_PREVIEW_CHARS = 600

/** Two ways to hand one page to another app: a picture, or a PDF with just that page. */
@Composable
private fun SharePageDialog(pageNumber: Int, onChoose: (PageShareExporter.Format) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuireShape.Dialog,
        title = { Text(stringResource(R.string.reader_share_page), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    stringResource(R.string.reader_page_n, pageNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                listOf(
                    PageShareExporter.Format.PNG to R.string.reader_share_page_image,
                    PageShareExporter.Format.PDF to R.string.reader_share_page_pdf,
                ).forEach { (format, label) ->
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(format) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { QuireTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun JumpToPageDialog(pageCount: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.toIntOrNull()
    val valid = value != null && value in 1..pageCount
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuireShape.Dialog,
        title = { Text(stringResource(R.string.reader_jump_to_page), style = MaterialTheme.typography.titleLarge) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text(stringResource(R.string.reader_jump_hint, pageCount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid && value != null) { onJump(value); onDismiss() } }),
                shape = QuireShape.Button,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            QuireButton(onClick = { if (value != null) { onJump(value); onDismiss() } }, enabled = valid) {
                Text(stringResource(R.string.reader_go))
            }
        },
        dismissButton = { QuireTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Optional vertical drag on the left edge: window brightness only, never system brightness.
 * Off by default. While dragging, the current level is shown beside the strip so the
 * invisible control has visible feedback.
 */
@Composable
private fun BrightnessEdge(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    val description = stringResource(R.string.reader_brightness)
    var percent by remember { mutableStateOf<Int?>(null) }
    Box(modifier.fillMaxHeight(0.7f)) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(28.dp)
                .semantics { contentDescription = description }
                .pointerInput(activity) {
                    detectVerticalDragGestures(
                        onDragEnd = { percent = null },
                        onDragCancel = { percent = null },
                    ) { _, dragAmount ->
                        val window = activity?.window ?: return@detectVerticalDragGestures
                        val attrs = window.attributes
                        val current = if (attrs.screenBrightness < 0f) 0.6f else attrs.screenBrightness
                        val next = (current - dragAmount / size.height).coerceIn(0.05f, 1f)
                        attrs.screenBrightness = next
                        window.attributes = attrs
                        percent = (next * 100).toInt()
                    }
                },
        )
        percent?.let { value ->
            Surface(
                shape = QuireShape.Card,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 36.dp),
            ) {
                Text(
                    stringResource(R.string.reader_brightness_percent, value),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun VolumeKeyPaging(enabled: Boolean, onTurn: (Int) -> Unit) {
    val keys = LocalVolumeKeys.current
    DisposableEffect(enabled, keys) {
        keys.consume = enabled
        onDispose { keys.consume = false }
    }
    LaunchedEffect(enabled, keys) {
        if (enabled) keys.events.collect { onTurn(it) }
    }
}

/** Show or hide status and navigation bars with the chrome. */
@Composable
private fun SystemBars(visible: Boolean) {
    val view = LocalView.current
    val activity = LocalActivity.current
    DisposableEffect(visible, activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
}
