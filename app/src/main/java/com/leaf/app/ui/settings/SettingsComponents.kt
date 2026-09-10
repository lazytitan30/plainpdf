package com.leaf.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.leaf.app.ui.theme.Spacing

/** Plain grouped list. No icons. A hairline separates groups, not rows. */
@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
        )
        content()
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SettingsRow(
    title: String,
    value: String? = null,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The current value first, then a plain-words explanation of what the row does.
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(color, CircleShape),
    )
}
