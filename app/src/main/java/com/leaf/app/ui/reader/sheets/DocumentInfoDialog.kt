package com.leaf.app.ui.reader.sheets

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.theme.QuireShape

@Composable
fun DocumentInfoDialog(
    displayName: String,
    uri: Uri,
    pageCount: Int,
    sizeBytes: Long?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val saf = LocalAppContainer.current.saf
    // A raw content:// URI means nothing to a reader; show the folder name or nothing at all.
    val location = remember(uri) { saf.documentFolderName(uri) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuireShape.Dialog,
        title = { Text(stringResource(R.string.reader_document_info), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                InfoLine(stringResource(R.string.reader_info_name), displayName)
                InfoLine(stringResource(R.string.reader_info_pages), pageCount.toString())
                InfoLine(
                    stringResource(R.string.reader_info_size),
                    sizeBytes?.let { Formatter.formatFileSize(context, it) } ?: stringResource(R.string.reader_info_unknown),
                )
                if (location != null) InfoLine(stringResource(R.string.reader_info_location), location)
            }
        },
        confirmButton = { QuireTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
}
