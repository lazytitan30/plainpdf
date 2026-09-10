package com.leaf.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leaf.app.R
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireOutlinedButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.util.ocr.OcrLanguage
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.ocr.PackState
import com.leaf.app.util.ocr.PackTier

/**
 * Languages for scanned text. Two plain lists: what is on the phone, with a tick per
 * language, and what can be fetched, with a size and one Get button each. Written for
 * people who have never heard of a language pack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrLanguagesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: OcrLanguagesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { OcrLanguagesViewModel(container.settings, container.tessdata, container.languagePacks, context.contentResolver) }
        },
    )
    val chosen by viewModel.chosen.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val packStates by viewModel.packStates.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var removing by remember { mutableStateOf<String?>(null) }

    val pickLanguageFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    val keepOneText = stringResource(R.string.ocr_languages_keep_one)
    val resources = LocalResources.current
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        val text = when (n) {
            is OcrLanguagesNotice.Ready -> resources.getString(R.string.ocr_languages_ready, OcrLanguages.nameOf(n.code))
            is OcrLanguagesNotice.ImportFailed -> n.message
            OcrLanguagesNotice.KeepOne -> keepOneText
        }
        viewModel.clearNotice()
        snackbar.showSnackbar(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_languages_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val downloadable = OcrLanguages.downloadable.filter { it.code !in installed }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    stringResource(R.string.ocr_languages_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 12.dp),
                )
            }

            item { SectionHeader(stringResource(R.string.ocr_languages_on_phone)) }
            items(installed, key = { "on-$it" }) { code ->
                InstalledRow(
                    code = code,
                    checked = code in chosen,
                    onCheckedChange = { viewModel.setChosen(code, it) },
                    onRemove = if (OcrLanguages.isBundled(code)) null else ({ removing = code }),
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.ocr_languages_get_more))
                Text(
                    stringResource(if (viewModel.canDownload) R.string.ocr_languages_get_more_play else R.string.ocr_languages_get_more_foss),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 4.dp),
                )
            }
            for (tier in listOf(PackTier.COMMON, PackTier.NEARBY, PackTier.OTHER)) {
                val group = downloadable.filter { it.tier == tier }
                if (group.isEmpty()) continue
                item(key = "tier-$tier") {
                    Text(
                        stringResource(
                            when (tier) {
                                PackTier.COMMON -> R.string.ocr_languages_tier_common
                                PackTier.NEARBY -> R.string.ocr_languages_tier_nearby
                                else -> R.string.ocr_languages_tier_other
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.screenHorizontal, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(group, key = { "get-${it.code}" }) { language ->
                    DownloadRow(
                        language = language,
                        state = packStates[language.code] ?: PackState.NotInstalled,
                        canDownload = viewModel.canDownload,
                        onGet = { viewModel.download(language.code) },
                        onCancel = { viewModel.cancel(language.code) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                    QuireOutlinedButton(onClick = { pickLanguageFile.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_ocr_import))
                    }
                    Text(
                        stringResource(R.string.settings_ocr_import_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (!viewModel.canDownload) {
                        QuireTextButton(onClick = { context.openUrl(TESSDATA_FILES_URL) }) {
                            Text(stringResource(R.string.settings_ocr_get_languages))
                        }
                    }
                }
            }
        }
    }

    val code = removing
    if (code != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.ocr_languages_remove_title, OcrLanguages.nameOf(code))) },
            text = { Text(stringResource(R.string.ocr_languages_remove_body)) },
            confirmButton = {
                QuireTextButton(onClick = { removing = null; viewModel.remove(code) }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                QuireTextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
    )
}

@Composable
private fun InstalledRow(code: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onRemove: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal - 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(OcrLanguages.nameOf(code), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (OcrLanguages.isBundled(code)) R.string.settings_ocr_bundled else R.string.ocr_languages_downloaded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
            }
        }
    }
}

@Composable
private fun DownloadRow(language: OcrLanguage, state: PackState, canDownload: Boolean, onGet: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(OcrLanguages.nameOf(language.code), style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (state) {
                        is PackState.Downloading -> stringResource(R.string.ocr_languages_downloading)
                        PackState.Installing -> stringResource(R.string.ocr_languages_installing)
                        is PackState.Failed -> stringResource(R.string.ocr_languages_failed)
                        else -> OcrLanguages.sizeText(language.sizeBytes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state is PackState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                !canDownload -> Unit
                state is PackState.Downloading -> QuireTextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                state is PackState.Installing -> Unit
                state is PackState.Failed -> QuireOutlinedButton(onClick = onGet) { Text(stringResource(R.string.ocr_languages_try_again)) }
                else -> QuireButton(onClick = onGet) { Text(stringResource(R.string.ocr_languages_get)) }
            }
        }
        if (state is PackState.Downloading) {
            Spacer(Modifier.height(6.dp))
            val fraction = state.fraction
            if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        if (state is PackState.Installing) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(0.dp))
    }
}

/** Opened in the browser by the FOSS build; the app itself never fetches anything. */
private const val TESSDATA_FILES_URL = "https://github.com/tesseract-ocr/tessdata_fast"
