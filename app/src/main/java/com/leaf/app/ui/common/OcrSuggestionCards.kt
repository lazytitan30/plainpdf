package com.leaf.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.util.ocr.OcrLanguage
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.ocr.PackState

/**
 * "Your phone is set to German. Get the German language (1.5 MB)?" Shown before recognition
 * runs, on the scan sheet and in Make searchable, until the person taps Get or Not now.
 * While Play downloads, the same card shows the progress so nobody has to go looking.
 */
@Composable
fun LanguagePackSuggestion(
    language: OcrLanguage,
    state: PackState,
    onGet: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = OcrLanguages.nameOf(language.code)
    ElevatedCard(modifier = modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ocr_suggest_title, name), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            when (state) {
                is PackState.Downloading -> {
                    Text(stringResource(R.string.ocr_languages_downloading), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    val fraction = state.fraction
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
                PackState.Installing -> {
                    Text(stringResource(R.string.ocr_languages_installing), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                else -> {
                    Text(
                        if (state is PackState.Failed) stringResource(R.string.ocr_languages_failed)
                        else stringResource(R.string.ocr_suggest_body, name, OcrLanguages.sizeText(language.sizeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuireTextButton(onClick = onNotNow) { Text(stringResource(R.string.ocr_suggest_later)) }
                        QuireButton(onClick = onGet) {
                            Text(stringResource(if (state is PackState.Failed) R.string.ocr_languages_try_again else R.string.ocr_suggest_get, name))
                        }
                    }
                }
            }
        }
    }
}

/** After recognition produced rubbish: point at the languages screen, once, gently. */
@Composable
fun GarbledTextHint(onOpenLanguages: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ocr_garbled_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ocr_garbled_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            QuireOutlinedButton(onClick = onOpenLanguages) { Text(stringResource(R.string.ocr_garbled_action)) }
        }
    }
}
