package com.leaf.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.leaf.app.ui.theme.QuireShape

data class Choice<T>(
    val value: T,
    val label: String,
    val supporting: String? = null,
    val locked: Boolean = false,
    val leading: (@Composable () -> Unit)? = null,
)

/** Single-select radio dialog. Picking an option applies it and closes; no confirm button. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    onLockedTap: (() -> Unit)? = null,
    lockedLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuireShape.Dialog,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            // Long lists (the language picker has 18 entries) must scroll inside the dialog.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    val isSelected = choice.value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    if (choice.locked) onLockedTap?.invoke() else {
                                        onSelect(choice.value)
                                        onDismiss()
                                    }
                                },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = null, enabled = !choice.locked)
                        Spacer(Modifier.width(12.dp))
                        choice.leading?.let {
                            it()
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                choice.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (choice.locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            choice.supporting?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (choice.locked && lockedLabel != null) {
                            Text(
                                lockedLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onLockedTap?.invoke() },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            QuireTextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.leaf.app.R.string.action_cancel)) }
        },
    )
}
