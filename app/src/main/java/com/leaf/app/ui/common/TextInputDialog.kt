package com.leaf.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.ui.theme.QuireShape

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    helpText: String? = null,
    confirmLabel: String = stringResource(R.string.action_done),
    singleLine: Boolean = true,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuireShape.Dialog,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = singleLine,
                    modifier = Modifier.fillMaxWidth(),
                    shape = QuireShape.Button,
                )
                if (helpText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(helpText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            QuireTextButton(onClick = { onConfirm(value); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = {
            QuireTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
