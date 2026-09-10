package com.leaf.app.ui.tools.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.theme.QuireShape

/** Asked before an operation starts, never during. The value is used once and dropped. */
@Composable
fun PasswordDialog(
    documentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    wrong: Boolean = false,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuireShape.Dialog,
        title = { Text(stringResource(R.string.ops_password_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(stringResource(R.string.ops_password_body, documentName), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    isError = wrong,
                    supportingText = if (wrong) { { Text(stringResource(R.string.ops_password_wrong)) } } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = QuireShape.Button,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            QuireButton(onClick = { onConfirm(value) }, enabled = value.isNotEmpty()) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = { QuireTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
