package com.leaf.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.leaf.app.R
import com.leaf.app.di.AppContainer
import com.leaf.app.ui.common.CrashReportPrompt
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.library.FolderScreen
import com.leaf.app.ui.library.LibraryScreen
import com.leaf.app.ui.library.ScanRequest
import com.leaf.app.ui.navigation.FolderRoute
import com.leaf.app.ui.navigation.LibraryRoute
import com.leaf.app.ui.navigation.ImagesToPdfRoute
import com.leaf.app.ui.navigation.MergeRoute
import com.leaf.app.ui.navigation.OrganiseRoute
import com.leaf.app.ui.navigation.CompressRoute
import com.leaf.app.ui.navigation.MakeSearchableRoute
import com.leaf.app.ui.navigation.NoteRoute
import com.leaf.app.ui.navigation.OcrLanguagesRoute
import com.leaf.app.ui.settings.OcrLanguagesScreen
import com.leaf.app.ui.navigation.PageNumbersRoute
import com.leaf.app.ui.navigation.PasswordRoute
import com.leaf.app.ui.navigation.RedactRoute
import com.leaf.app.ui.navigation.PdfToImagesRoute
import com.leaf.app.ui.navigation.SignRoute
import com.leaf.app.ui.navigation.SplitRoute
import com.leaf.app.ui.tools.sign.SignScreen
import com.leaf.app.ui.navigation.ReaderRoute
import com.leaf.app.ui.navigation.SettingsRoute
import com.leaf.app.ui.navigation.SupporterRoute
import com.leaf.app.ui.navigation.ToolsRoute
import com.leaf.app.ui.reader.ReaderScreen
import com.leaf.app.ui.reader.ReaderTool
import com.leaf.app.ui.settings.SettingsScreen
import com.leaf.app.ui.tour.WelcomeTour
import com.leaf.app.ui.supporter.SupporterScreen
import com.leaf.app.ui.theme.QuireTheme
import com.leaf.app.ui.tools.Tool
import com.leaf.app.ui.tools.ToolsScreen
import com.leaf.app.ui.tools.images.ImagesToPdfScreen
import com.leaf.app.ui.tools.images.PdfToImagesScreen
import com.leaf.app.ui.tools.merge.MergeScreen
import com.leaf.app.ui.tools.organise.OrganiseScreen
import com.leaf.app.ui.tools.compress.CompressScreen
import com.leaf.app.ui.tools.ocr.MakeSearchableScreen
import com.leaf.app.ui.tools.note.NoteScreen
import com.leaf.app.ui.tools.pagenumbers.PageNumbersScreen
import com.leaf.app.ui.tools.password.PasswordScreen
import com.leaf.app.ui.tools.redact.RedactScreen
import com.leaf.app.ui.tools.split.SplitScreen
import android.net.Uri
import com.leaf.app.util.saf.SafAccess

@Composable
fun QuireApp(container: AppContainer, openRequests: OpenRequests, volumeKeys: VolumeKeys) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    // The splash screen covers the first frame; do not flash the wrong theme.
    val current = settings ?: return

    CompositionLocalProvider(LocalAppContainer provides container, LocalVolumeKeys provides volumeKeys) {
        QuireTheme(theme = current.theme, accent = current.accent, dynamicColor = current.dynamicColor) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val destination = backStackEntry?.destination
            val onLibrary = destination?.hasRoute<LibraryRoute>() == true
            val onTools = destination?.hasRoute<ToolsRoute>() == true
            val showBottomBar = destination == null || onLibrary || onTools

            // Documents arriving from the picker: persist whatever was granted, then read.
            val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data = result.data
                val uri = data?.data
                if (result.resultCode == Activity.RESULT_OK && uri != null) {
                    container.saf.takePersistable(uri, data.flags)
                    navController.navigate(ReaderRoute(uri.toString()))
                }
            }

            val pickForOrganise = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data = result.data
                val uri = data?.data
                if (result.resultCode == Activity.RESULT_OK && uri != null) {
                    container.saf.takePersistable(uri, data.flags)
                    navController.navigate(OrganiseRoute(uri.toString()))
                }
            }

            val pickForSign = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data = result.data
                val uri = data?.data
                if (result.resultCode == Activity.RESULT_OK && uri != null) {
                    container.saf.takePersistable(uri, data.flags)
                    navController.navigate(SignRoute(uri.toString()))
                }
            }

            val pickForRedact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data = result.data
                val uri = data?.data
                if (result.resultCode == Activity.RESULT_OK && uri != null) {
                    container.saf.takePersistable(uri, data.flags)
                    navController.navigate(RedactRoute(uri.toString()))
                }
            }

            // Documents arriving from other apps via VIEW or SEND, or a shortcut asking for a scan.
            var scanRequest by remember { mutableStateOf<ScanRequest?>(null) }
            val incoming by openRequests.pending.collectAsStateWithLifecycle()
            LaunchedEffect(incoming) {
                val request = incoming ?: return@LaunchedEffect
                openRequests.consume(request)
                when (request) {
                    is OpenRequest.Open -> {
                        if (request.grantFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                            container.saf.takePersistable(request.uri, request.grantFlags)
                        }
                        navController.navigate(ReaderRoute(request.uri.toString()))
                    }
                    is OpenRequest.MergePdfs -> {
                        request.uris.forEach { container.saf.takePersistable(it, request.grantFlags) }
                        navController.navigate(MergeRoute(request.uris.map { it.toString() }))
                    }
                    is OpenRequest.ImagesToPdf -> {
                        request.uris.forEach { container.saf.takePersistable(it, request.grantFlags) }
                        navController.navigate(ImagesToPdfRoute(request.uris.map { it.toString() }))
                    }
                    is OpenRequest.Sign -> {
                        container.saf.takePersistable(request.uri, request.grantFlags)
                        navController.navigate(SignRoute(request.uri.toString()))
                    }
                    is OpenRequest.ScanImages -> {
                        request.uris.forEach { container.saf.takePersistable(it, request.grantFlags) }
                        navController.switchTab(LibraryRoute)
                        scanRequest = ScanRequest(request.nonce, sharedPhotos = request.uris)
                    }
                    is OpenRequest.Scan -> {
                        navController.switchTab(LibraryRoute)
                        scanRequest = ScanRequest(request.nonce)
                    }
                    is OpenRequest.PickPdf -> pickPdf.launch(SafAccess.openPdfIntent())
                }
            }

            // After a crash: offer to share the report, whatever screen comes next.
            CrashReportPrompt()

            // Android 13 and newer only show the "Working on your document" notification with
            // permission. Ask the first time a long job starts, when the reason is on screen;
            // the job runs either way, and Android stops asking after two refusals.
            val activeOperation by container.operationLauncher.active.collectAsStateWithLifecycle()
            val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val appContext = LocalContext.current
            LaunchedEffect(activeOperation != null) {
                if (activeOperation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // First launch: the tour, unless another app just handed us a document to open.
            val tourScope = rememberCoroutineScope()
            if (!current.tourSeen && incoming == null) {
                WelcomeTour(onFinished = { tourScope.launch { container.settings.setTourSeen(true) } })
                return@QuireTheme
            }

            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(tonalElevation = 0.dp) {
                            NavigationBarItem(
                                selected = onLibrary || destination == null,
                                onClick = { navController.switchTab(LibraryRoute) },
                                icon = { Icon(painterResource(R.drawable.ic_library), contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_library)) },
                            )
                            NavigationBarItem(
                                selected = onTools,
                                onClick = { navController.switchTab(ToolsRoute) },
                                icon = { Icon(painterResource(R.drawable.ic_tools), contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_tools)) },
                            )
                        }
                    }
                },
                // Every screen has its own top bar, which already leaves room for the status
                // bar. Letting this outer layout leave room as well pushed every title a
                // status bar's height too far down. It hands over the bottom bar's height only.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = LibraryRoute,
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                ) {
                    composable<LibraryRoute> {
                        LibraryScreen(
                            onOpenSupporter = { navController.navigate(SupporterRoute) },
                            onOpenOcrLanguages = { navController.navigate(OcrLanguagesRoute) },
                            scanRequest = scanRequest,
                            onOpenSettings = { navController.navigate(SettingsRoute) },
                            onOpenPdf = { pickPdf.launch(SafAccess.openPdfIntent()) },
                            onOpenDocument = { navController.navigate(ReaderRoute(it.uri)) },
                            onOpenFolder = { navController.navigate(FolderRoute(it.id)) },
                            onOrganise = { navController.navigate(OrganiseRoute(it.uri)) },
                        )
                    }
                    composable<FolderRoute> { entry ->
                        val route = entry.toRoute<FolderRoute>()
                        FolderScreen(
                            folderId = route.folderId,
                            onBack = { navController.popBackStack() },
                            onOpenDocument = { navController.navigate(ReaderRoute(it.uri)) },
                        )
                    }
                    composable<ToolsRoute> {
                        ToolsScreen(
                            onTool = { tool ->
                                when (tool) {
                                    Tool.ORGANISE -> pickForOrganise.launch(SafAccess.openPdfIntent())
                                    Tool.MERGE -> navController.navigate(MergeRoute())
                                    Tool.SPLIT -> navController.navigate(SplitRoute())
                                    Tool.IMAGES_TO_PDF -> navController.navigate(ImagesToPdfRoute())
                                    Tool.NOTE -> navController.navigate(NoteRoute)
                                    Tool.PDF_TO_IMAGES -> navController.navigate(PdfToImagesRoute())
                                    Tool.PASSWORD -> navController.navigate(PasswordRoute())
                                    Tool.COMPRESS -> navController.navigate(CompressRoute())
                                    Tool.SIGN -> pickForSign.launch(SafAccess.openPdfIntent())
                                    Tool.PAGE_NUMBERS -> navController.navigate(PageNumbersRoute())
                                    Tool.MAKE_SEARCHABLE -> navController.navigate(MakeSearchableRoute())
                                    Tool.REDACT -> pickForRedact.launch(SafAccess.openPdfIntent())
                                }
                            },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<MergeRoute> { entry ->
                        val route = entry.toRoute<MergeRoute>()
                        MergeScreen(
                            initialUris = route.uris.map(Uri::parse),
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<SplitRoute> { entry ->
                        val route = entry.toRoute<SplitRoute>()
                        SplitScreen(
                            initialUri = route.uri?.let(Uri::parse),
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                            onExtract = { navController.navigate(OrganiseRoute(it.toString())) },
                        )
                    }
                    composable<ImagesToPdfRoute> { entry ->
                        val route = entry.toRoute<ImagesToPdfRoute>()
                        ImagesToPdfScreen(
                            initialUris = route.uris.map(Uri::parse),
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<PdfToImagesRoute> { entry ->
                        val route = entry.toRoute<PdfToImagesRoute>()
                        PdfToImagesScreen(initialUri = route.uri?.let(Uri::parse), onBack = { navController.popBackStack() })
                    }
                    composable<NoteRoute> {
                        NoteScreen(
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<SignRoute> { entry ->
                        val route = entry.toRoute<SignRoute>()
                        SignScreen(
                            uri = route.uri,
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<CompressRoute> { entry ->
                        val route = entry.toRoute<CompressRoute>()
                        CompressScreen(
                            initialUri = route.uri?.let(Uri::parse),
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<PasswordRoute> { entry ->
                        val route = entry.toRoute<PasswordRoute>()
                        PasswordScreen(
                            initialUri = route.uri?.let(Uri::parse),
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<PageNumbersRoute> { entry ->
                        val route = entry.toRoute<PageNumbersRoute>()
                        PageNumbersScreen(
                            initialUri = route.uri?.let(Uri::parse),
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<MakeSearchableRoute> { entry ->
                        val route = entry.toRoute<MakeSearchableRoute>()
                        MakeSearchableScreen(
                            initialUri = route.uri?.let(Uri::parse),
                            onOpenLanguages = { navController.navigate(OcrLanguagesRoute) },
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<RedactRoute> { entry ->
                        val route = entry.toRoute<RedactRoute>()
                        RedactScreen(
                            uri = route.uri,
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<OrganiseRoute> { entry ->
                        val route = entry.toRoute<OrganiseRoute>()
                        OrganiseScreen(
                            uri = route.uri,
                            onBack = { navController.popBackStack() },
                            onOpenOutput = { navController.navigate(ReaderRoute(it.toString())) },
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            onOpenOcrLanguages = { navController.navigate(OcrLanguagesRoute) },
                            onBack = { navController.popBackStack() },
                            onOpenSupporter = { navController.navigate(SupporterRoute) },
                        )
                    }
                    composable<OcrLanguagesRoute> {
                        OcrLanguagesScreen(onBack = { navController.popBackStack() })
                    }
                    composable<SupporterRoute> {
                        SupporterScreen(onBack = { navController.popBackStack() })
                    }
                    composable<ReaderRoute> { entry ->
                        val route = entry.toRoute<ReaderRoute>()
                        ReaderScreen(
                            uri = route.uri,
                            onBack = { navController.popBackStack() },
                            onRelocated = { newUri ->
                                navController.navigate(ReaderRoute(newUri)) {
                                    popUpTo<ReaderRoute> { inclusive = true }
                                }
                            },
                            onTool = { tool ->
                                when (tool) {
                                    ReaderTool.ORGANISE -> navController.navigate(OrganiseRoute(route.uri))
                                    ReaderTool.MERGE -> navController.navigate(MergeRoute(listOf(route.uri)))
                                    ReaderTool.SPLIT -> navController.navigate(SplitRoute(route.uri))
                                    ReaderTool.PDF_TO_IMAGES -> navController.navigate(PdfToImagesRoute(route.uri))
                                    ReaderTool.PASSWORD -> navController.navigate(PasswordRoute(route.uri))
                                    ReaderTool.COMPRESS -> navController.navigate(CompressRoute(route.uri))
                                    ReaderTool.SIGN -> navController.navigate(SignRoute(route.uri))
                                    ReaderTool.PAGE_NUMBERS -> navController.navigate(PageNumbersRoute(route.uri))
                                    ReaderTool.MAKE_SEARCHABLE -> navController.navigate(MakeSearchableRoute(route.uri))
                                    ReaderTool.REDACT -> navController.navigate(RedactRoute(route.uri))
                                    // Handled inside the reader: drawing mode, speech, text and print never leave it.
                                    ReaderTool.ANNOTATE, ReaderTool.READ_ALOUD, ReaderTool.COPY_TEXT, ReaderTool.PRINT -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
