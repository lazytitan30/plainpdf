package com.leaf.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.data.billing.SupportEntry
import com.leaf.app.data.prefs.Accent
import com.leaf.app.data.prefs.AppIcon
import com.leaf.app.data.prefs.AppTheme
import com.leaf.app.data.prefs.HighlightSet
import com.leaf.app.util.AppIconSwitcher
import com.leaf.app.data.prefs.LibrarySort
import com.leaf.app.data.prefs.PageDisplayMode
import com.leaf.app.data.prefs.ReadingMode
import com.leaf.app.ui.common.Choice
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableIntStateOf
import com.leaf.app.ui.common.ChoiceDialog
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.common.MultiChoice
import com.leaf.app.ui.common.MultiChoiceDialog
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.util.AppLanguages
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.saf.OutputNames
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.TextInputDialog
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.settings.HighlightColors.color
import com.leaf.app.ui.theme.LocalPalette
import com.leaf.app.ui.theme.tone

private enum class Dialog { NONE, LANGUAGE, THEME, ACCENT, APP_ICON, HIGHLIGHT, READING_MODE, PAGE_DISPLAY, SAVE_LOCATION, OUTPUT_NAMING, OUTPUT_NAMING_CUSTOM, LIBRARY_SORT, DEDICATION }

/** Where new files go: the tools ask each time, or always use the chosen folder. PICK opens the folder picker. */
private enum class SaveWhere { ASK, FOLDER, PICK }

/** Ready-made naming patterns, so nobody has to type {name} and {op} by hand. */
private enum class NamingStyle(val pattern: String?) {
    NAME_OP("{name}_{op}"),
    OP_NAME("{op}_{name}"),
    CUSTOM(null);

    companion object {
        fun of(pattern: String): NamingStyle = entries.firstOrNull { it.pattern == pattern } ?: CUSTOM
    }
}

/** The operation used in the naming examples; it reads the same in every language. */
private const val SAMPLE_OP = "merged"

/** Taps on the version row before the dedication shows. */
private const val DEDICATION_TAPS = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSupporter: () -> Unit,
    onOpenOcrLanguages: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.supporter, container.tessdata) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val installedOcr by viewModel.installedOcrLanguages.collectAsStateWithLifecycle()
    val ocrImport by viewModel.ocrImport.collectAsStateWithLifecycle()
    val ocrImporting by viewModel.ocrImporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf(Dialog.NONE) }
    val snackbar = remember { SnackbarHostState() }

    val pickLanguageFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importOcrLanguage(uri, context.contentResolver)
    }
    val importedText = stringResource(R.string.settings_ocr_imported_done)
    LaunchedEffect(ocrImport) {
        val result = ocrImport ?: return@LaunchedEffect
        snackbar.showSnackbar(
            when (result) {
                is OcrImportResult.Imported -> "$importedText: ${OcrLanguages.nameOf(result.code)}"
                is OcrImportResult.Failed -> result.message
            },
        )
        viewModel.clearOcrImportResult()
    }

    val pickSaveTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val granted = container.saf.takePersistableTree(uri)
            if (granted.write) viewModel.setDefaultSaveTreeUri(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val ui = state ?: return@Scaffold
        val s = ui.settings
        val versionName = remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup(stringResource(R.string.settings_group_appearance)) {
                val language = remember { AppLanguages.current() }
                SettingsRow(
                    title = stringResource(R.string.settings_language),
                    value = AppLanguages.nameOf(language) ?: stringResource(R.string.settings_language_system),
                    supporting = stringResource(R.string.settings_language_summary),
                    onClick = { dialog = Dialog.LANGUAGE },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_theme),
                    value = s.theme.label(),
                    supporting = stringResource(R.string.settings_theme_summary),
                    onClick = { dialog = Dialog.THEME },
                )
                val palette = LocalPalette.current
                SettingsRow(
                    title = stringResource(R.string.settings_accent),
                    value = s.accent.label(),
                    supporting = stringResource(R.string.settings_accent_summary),
                    trailing = { ColorDot(s.accent.tone(palette.isDark, s.theme == AppTheme.SEPIA)) },
                    onClick = { dialog = Dialog.ACCENT },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_colour),
                    supporting = stringResource(R.string.settings_dynamic_colour_summary),
                    checked = s.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_app_icon),
                    value = s.appIcon.label(),
                    supporting = stringResource(R.string.settings_app_icon_summary),
                    onClick = { dialog = Dialog.APP_ICON },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_highlight),
                    value = s.highlightSet.label(),
                    supporting = stringResource(R.string.settings_highlight_summary),
                    trailing = { ColorDot(s.highlightSet.color(palette.isDark, s.accent.tone(palette.isDark, s.theme == AppTheme.SEPIA))) },
                    onClick = { dialog = Dialog.HIGHLIGHT },
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_reading)) {
                SettingsRow(
                    title = stringResource(R.string.settings_reading_mode),
                    value = s.readingMode.label(),
                    supporting = stringResource(R.string.settings_reading_mode_summary),
                    onClick = { dialog = Dialog.READING_MODE },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_page_display),
                    value = s.pageDisplayMode.label(),
                    supporting = stringResource(R.string.settings_page_display_summary),
                    onClick = { dialog = Dialog.PAGE_DISPLAY },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    supporting = stringResource(R.string.settings_keep_screen_on_summary),
                    checked = s.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_edge_brightness),
                    supporting = stringResource(R.string.settings_edge_brightness_summary),
                    checked = s.edgeBrightness,
                    onCheckedChange = viewModel::setEdgeBrightness,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_double_tap_zoom),
                    supporting = stringResource(R.string.settings_double_tap_zoom_summary),
                    checked = s.doubleTapZoom,
                    onCheckedChange = viewModel::setDoubleTapZoom,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_volume_keys),
                    supporting = stringResource(R.string.settings_volume_keys_summary),
                    checked = s.volumeKeysTurnPages,
                    onCheckedChange = viewModel::setVolumeKeysTurnPages,
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_tools)) {
                val treeLabel = s.defaultSaveTreeUri?.let { container.saf.treeDisplayName(Uri.parse(it)) }
                // One row, one dialog: "ask each time" and the chosen folder are two options of
                // the same choice, not two rows that seem to contradict each other.
                SettingsRow(
                    title = stringResource(R.string.settings_save_location),
                    value = treeLabel?.let { stringResource(R.string.settings_save_location_folder, it) }
                        ?: stringResource(R.string.settings_save_location_ask),
                    supporting = stringResource(R.string.settings_save_location_summary),
                    onClick = { dialog = Dialog.SAVE_LOCATION },
                )
                val sampleName = stringResource(R.string.naming_sample_name)
                SettingsRow(
                    title = stringResource(R.string.settings_output_naming),
                    value = when (NamingStyle.of(s.outputNamePattern)) {
                        NamingStyle.NAME_OP -> stringResource(R.string.naming_style_name_op)
                        NamingStyle.OP_NAME -> stringResource(R.string.naming_style_op_name)
                        NamingStyle.CUSTOM -> "${stringResource(R.string.naming_style_custom)}: ${s.outputNamePattern}"
                    },
                    supporting = stringResource(
                        R.string.settings_output_naming_summary,
                        "$sampleName.pdf",
                        OutputNames.build(s.outputNamePattern, sampleName, SAMPLE_OP),
                    ),
                    onClick = { dialog = Dialog.OUTPUT_NAMING },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_confirm_overwrite),
                    supporting = stringResource(R.string.settings_confirm_overwrite_summary),
                    checked = s.confirmOverwrite,
                    onCheckedChange = viewModel::setConfirmOverwrite,
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_ocr)) {
                SwitchRow(
                    title = stringResource(R.string.settings_ocr_enabled),
                    supporting = stringResource(R.string.settings_ocr_enabled_summary),
                    checked = s.scanOcr,
                    onCheckedChange = viewModel::setScanOcr,
                )
                val chosen = OcrLanguages.split(s.ocrLanguages)
                SettingsRow(
                    title = stringResource(R.string.settings_ocr_languages),
                    value = chosen.joinToString(", ") { OcrLanguages.nameOf(it) },
                    supporting = stringResource(R.string.settings_ocr_languages_help),
                    enabled = s.scanOcr,
                    onClick = onOpenOcrLanguages,
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_library)) {
                SettingsRow(
                    title = stringResource(R.string.settings_default_sort),
                    value = s.librarySort.label(),
                    supporting = stringResource(R.string.settings_default_sort_summary),
                    onClick = { dialog = Dialog.LIBRARY_SORT },
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_about)) {
                var versionTaps by remember { mutableIntStateOf(0) }
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    value = versionName,
                    onClick = {
                        versionTaps += 1
                        if (versionTaps >= DEDICATION_TAPS) {
                            versionTaps = 0
                            dialog = Dialog.DEDICATION
                        }
                    },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_source),
                    value = SOURCE_URL,
                    supporting = stringResource(R.string.settings_source_summary),
                    onClick = { context.openUrl(SOURCE_URL) },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_licence),
                    value = stringResource(R.string.settings_licence_value),
                    supporting = stringResource(R.string.settings_licence_summary),
                )
                val feedbackSubject = stringResource(R.string.feedback_subject)
                SettingsRow(
                    title = stringResource(R.string.settings_feedback),
                    supporting = stringResource(R.string.settings_feedback_summary),
                    onClick = { context.sendFeedback(feedbackSubject) },
                )
                SettingsRow(
                    title = stringResource(R.string.tour_show_again),
                    supporting = stringResource(R.string.tour_show_again_summary),
                    onClick = viewModel::showTourAgain,
                )
                when (val entry = ui.supportEntry) {
                    SupportEntry.InApp -> SettingsRow(
                        title = stringResource(R.string.settings_supporter),
                        value = stringResource(
                            when {
                                ui.standing.unlockOwned -> R.string.settings_supporter_active
                                ui.standing.tipped -> R.string.settings_supporter_active_gift
                                else -> R.string.settings_supporter_summary
                            },
                        ),
                        onClick = onOpenSupporter,
                    )
                    is SupportEntry.DonateLink -> SettingsRow(
                        title = stringResource(R.string.settings_donate),
                        value = stringResource(R.string.settings_supporter_summary),
                        onClick = { context.openUrl(entry.url) },
                    )
                }
            }
        }

        when (dialog) {
            Dialog.DEDICATION -> AlertDialog(
                onDismissRequest = { dialog = Dialog.NONE },
                title = { Text(stringResource(R.string.app_name)) },
                text = { Text(stringResource(R.string.settings_dedication)) },
                confirmButton = {
                    QuireTextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.action_done)) }
                },
            )
            Dialog.NONE -> Unit
            Dialog.LANGUAGE -> ChoiceDialog(
                title = stringResource(R.string.settings_language),
                choices = listOf(Choice(AppLanguages.SYSTEM, stringResource(R.string.settings_language_system))) +
                    AppLanguages.supported.map { (tag, name) -> Choice(tag, name) },
                selected = AppLanguages.current(),
                onSelect = { tag ->
                    dialog = Dialog.NONE
                    AppLanguages.apply(tag)
                },
                onDismiss = { dialog = Dialog.NONE },
            )
            Dialog.THEME -> ChoiceDialog(
                title = stringResource(R.string.settings_theme),
                choices = AppTheme.entries.map { Choice(it, it.label()) },
                selected = s.theme,
                onSelect = viewModel::setTheme,
                onDismiss = { dialog = Dialog.NONE },
            )
            Dialog.ACCENT -> {
                val palette = LocalPalette.current
                ChoiceDialog(
                    title = stringResource(R.string.settings_accent),
                    choices = Accent.entries.map { accent ->
                        Choice(
                            value = accent,
                            label = accent.label(),
                            locked = !accent.free && !ui.isSupporter,
                            leading = { ColorDot(accent.tone(palette.isDark, s.theme == AppTheme.SEPIA)) },
                        )
                    },
                    selected = s.accent,
                    onSelect = viewModel::setAccent,
                    onDismiss = { dialog = Dialog.NONE },
                    onLockedTap = {
                        dialog = Dialog.NONE
                        onOpenSupporter()
                    },
                    lockedLabel = stringResource(R.string.settings_locked),
                )
            }
            Dialog.APP_ICON -> ChoiceDialog(
                title = stringResource(R.string.settings_app_icon),
                choices = AppIcon.entries.map { Choice(it, it.label(), locked = !it.free && !ui.isSupporter) },
                selected = s.appIcon,
                onSelect = { icon ->
                    viewModel.setAppIcon(icon)
                    AppIconSwitcher(context).apply(icon)
                },
                onDismiss = { dialog = Dialog.NONE },
                onLockedTap = { dialog = Dialog.NONE; onOpenSupporter() },
                lockedLabel = stringResource(R.string.settings_locked),
            )
            Dialog.HIGHLIGHT -> {
                val palette = LocalPalette.current
                val accentColor = s.accent.tone(palette.isDark, s.theme == AppTheme.SEPIA)
                ChoiceDialog(
                    title = stringResource(R.string.settings_highlight),
                    choices = HighlightSet.entries.map { set ->
                        Choice(set, set.label(), locked = !set.free && !ui.isSupporter, leading = { ColorDot(set.color(palette.isDark, accentColor)) })
                    },
                    selected = s.highlightSet,
                    onSelect = viewModel::setHighlightSet,
                    onDismiss = { dialog = Dialog.NONE },
                    onLockedTap = { dialog = Dialog.NONE; onOpenSupporter() },
                    lockedLabel = stringResource(R.string.settings_locked),
                )
            }
            Dialog.READING_MODE -> ChoiceDialog(
                title = stringResource(R.string.settings_reading_mode),
                choices = ReadingMode.entries.map { Choice(it, it.label()) },
                selected = s.readingMode,
                onSelect = viewModel::setReadingMode,
                onDismiss = { dialog = Dialog.NONE },
            )
            Dialog.PAGE_DISPLAY -> ChoiceDialog(
                title = stringResource(R.string.settings_page_display),
                choices = PageDisplayMode.entries.map { Choice(it, it.label()) },
                selected = s.pageDisplayMode,
                onSelect = viewModel::setPageDisplayMode,
                onDismiss = { dialog = Dialog.NONE },
            )
            Dialog.SAVE_LOCATION -> {
                val treeLabel = s.defaultSaveTreeUri?.let { container.saf.treeDisplayName(Uri.parse(it)) }
                ChoiceDialog(
                    title = stringResource(R.string.settings_save_location),
                    choices = buildList {
                        add(
                            Choice(
                                SaveWhere.ASK,
                                stringResource(R.string.settings_save_location_ask),
                                supporting = stringResource(R.string.settings_save_location_ask_summary),
                            ),
                        )
                        if (treeLabel != null) add(Choice(SaveWhere.FOLDER, stringResource(R.string.settings_save_location_folder, treeLabel)))
                        add(
                            Choice(
                                SaveWhere.PICK,
                                stringResource(R.string.settings_save_location_choose),
                                supporting = stringResource(R.string.settings_save_location_choose_summary),
                            ),
                        )
                    },
                    selected = if (treeLabel != null) SaveWhere.FOLDER else SaveWhere.ASK,
                    onSelect = { where ->
                        dialog = Dialog.NONE
                        when (where) {
                            SaveWhere.ASK -> viewModel.setDefaultSaveTreeUri(null)
                            SaveWhere.FOLDER -> Unit
                            SaveWhere.PICK -> pickSaveTree.launch(null)
                        }
                    },
                    onDismiss = { dialog = Dialog.NONE },
                )
            }
            Dialog.OUTPUT_NAMING -> {
                val sampleName = stringResource(R.string.naming_sample_name)
                ChoiceDialog(
                    title = stringResource(R.string.settings_output_naming),
                    choices = listOf(
                        Choice(
                            NamingStyle.NAME_OP,
                            stringResource(R.string.naming_style_name_op),
                            supporting = OutputNames.build(NamingStyle.NAME_OP.pattern.orEmpty(), sampleName, SAMPLE_OP),
                        ),
                        Choice(
                            NamingStyle.OP_NAME,
                            stringResource(R.string.naming_style_op_name),
                            supporting = OutputNames.build(NamingStyle.OP_NAME.pattern.orEmpty(), sampleName, SAMPLE_OP),
                        ),
                        Choice(
                            NamingStyle.CUSTOM,
                            stringResource(R.string.naming_style_custom),
                            supporting = stringResource(R.string.naming_style_custom_summary),
                        ),
                    ),
                    selected = NamingStyle.of(s.outputNamePattern),
                    onSelect = { style ->
                        val pattern = style.pattern
                        if (pattern == null) {
                            dialog = Dialog.OUTPUT_NAMING_CUSTOM
                        } else {
                            dialog = Dialog.NONE
                            viewModel.setOutputNamePattern(pattern)
                        }
                    },
                    // The custom choice hands over to the text dialog; a dismiss that arrives
                    // after that hand-over must not close it.
                    onDismiss = { if (dialog == Dialog.OUTPUT_NAMING) dialog = Dialog.NONE },
                )
            }
            Dialog.OUTPUT_NAMING_CUSTOM -> TextInputDialog(
                title = stringResource(R.string.naming_style_custom),
                initialValue = s.outputNamePattern,
                helpText = stringResource(R.string.settings_output_naming_help),
                onConfirm = viewModel::setOutputNamePattern,
                onDismiss = { dialog = Dialog.NONE },
            )
            Dialog.LIBRARY_SORT -> ChoiceDialog(
                title = stringResource(R.string.settings_default_sort),
                choices = LibrarySort.entries.map { Choice(it, it.label()) },
                selected = s.librarySort,
                onSelect = viewModel::setLibrarySort,
                onDismiss = { dialog = Dialog.NONE },
            )
        }
    }
}

// The website forwards to the repository, so the app never hard-codes a hosting service.
private const val SOURCE_URL = "https://plainpdf.app/source"

/** Opened in the browser; the app itself never fetches anything. */
private const val TESSDATA_URL = "https://github.com/tesseract-ocr/tessdata_fast"

/** Opens the mail app with a message to support; the app itself sends nothing. */
internal fun android.content.Context.sendFeedback(subject: String) {
    val uri = android.net.Uri.parse("mailto:support@plainpdf.app?subject=" + android.net.Uri.encode(subject))
    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

internal fun android.content.Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
fun AppTheme.label(): String = stringResource(
    when (this) {
        AppTheme.SYSTEM -> R.string.theme_system
        AppTheme.LIGHT -> R.string.theme_light
        AppTheme.DARK -> R.string.theme_dark
        AppTheme.BLACK -> R.string.theme_black
        AppTheme.SEPIA -> R.string.theme_sepia
    },
)

@Composable
fun Accent.label(): String = stringResource(
    when (this) {
        Accent.TEAL -> R.string.accent_teal
        Accent.AMBER -> R.string.accent_amber
        Accent.CRIMSON -> R.string.accent_crimson
        Accent.INDIGO -> R.string.accent_indigo
        Accent.VIOLET -> R.string.accent_violet
        Accent.FOREST -> R.string.accent_forest
        Accent.SLATE -> R.string.accent_slate
    },
)

@Composable
fun ReadingMode.label(): String = stringResource(
    when (this) {
        ReadingMode.CONTINUOUS -> R.string.reading_mode_continuous
        ReadingMode.PAGED -> R.string.reading_mode_paged
    },
)

@Composable
fun PageDisplayMode.label(): String = stringResource(
    when (this) {
        PageDisplayMode.NORMAL -> R.string.page_display_normal
        PageDisplayMode.NIGHT -> R.string.page_display_night
        PageDisplayMode.SEPIA -> R.string.page_display_sepia
        PageDisplayMode.GRAYSCALE -> R.string.page_display_grayscale
    },
)

@Composable
fun LibrarySort.label(): String = stringResource(
    when (this) {
        LibrarySort.RECENTLY_OPENED -> R.string.sort_recently_opened
        LibrarySort.RECENTLY_ADDED -> R.string.sort_recently_added
        LibrarySort.NAME -> R.string.sort_name
        LibrarySort.SIZE -> R.string.sort_size
    },
)

@Composable
fun AppIcon.label(): String = stringResource(
    when (this) {
        AppIcon.DEFAULT -> R.string.app_icon_default
        AppIcon.MONO -> R.string.app_icon_mono
        AppIcon.PAPER -> R.string.app_icon_paper
        AppIcon.NIGHT -> R.string.app_icon_night
    },
)

@Composable
fun HighlightSet.label(): String = stringResource(
    when (this) {
        HighlightSet.ACCENT -> R.string.highlight_accent
        HighlightSet.AMBER -> R.string.highlight_amber
        HighlightSet.ROSE -> R.string.highlight_rose
        HighlightSet.SKY -> R.string.highlight_sky
    },
)

/** Search highlight base colours; the reader applies its own alpha. */
object HighlightColors {
    fun HighlightSet.color(dark: Boolean, accent: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color = when (this) {
        HighlightSet.ACCENT -> accent
        HighlightSet.AMBER -> if (dark) androidx.compose.ui.graphics.Color(0xFFE0A85C) else androidx.compose.ui.graphics.Color(0xFFE6A32B)
        HighlightSet.ROSE -> if (dark) androidx.compose.ui.graphics.Color(0xFFE38AA0) else androidx.compose.ui.graphics.Color(0xFFD9607E)
        HighlightSet.SKY -> if (dark) androidx.compose.ui.graphics.Color(0xFF7FB4E6) else androidx.compose.ui.graphics.Color(0xFF3C8DD9)
    }
}
