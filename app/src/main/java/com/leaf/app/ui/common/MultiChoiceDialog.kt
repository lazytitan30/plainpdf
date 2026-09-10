package com.leaf.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.ui.theme.QuireShape

data class MultiChoice<T>(
    val value: T,
    val label: String,
    val supporting: String? = null,
    /** Shows a Remove action, which calls the onDelete passed to [MultiChoiceDialog]. */
    val deletable: Boolean = false,
)

/**
 * Checkbox list with a Done button. Unlike [ChoiceDialog] the choice is only applied on
 * Done, and at least [minSelected] items must stay ticked.
 */
@Composable
fun <T> MultiChoiceDialog(
    title: String,
    choices: List<MultiChoice<T>>,
    selected: Set<T>,
    onConfirm: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
    onDelete: ((T) -> Unit)? = null,
    minSelected: Int = 1,
) {
    var ticked by remember(selected, choices) { mutableStateOf(selected.filter { s -> choices.any { it.value == s } }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuireShape.Dialog,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    val isTicked = choice.value in ticked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isTicked,
                                role = Role.Checkbox,
                                onValueChange = { on -> ticked = if (on) ticked + choice.value else ticked - choice.value },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isTicked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            choice.supporting?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (choice.deletable && onDelete != null) {
                            QuireTextButton(onClick = { ticked = ticked - choice.value; onDelete(choice.value) }) {
                                Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            QuireTextButton(onClick = { onConfirm(ticked); onDismiss() }, enabled = ticked.size >= minSelected) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            QuireTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
