package com.leaf.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leaf.app.ui.theme.Spacing

/** An invitation, not an apology. One line and the action, nothing decorative. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenHorizontal * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.section))
            QuireButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A spinner is never shown alone: [message] says what the wait is for. */
@Composable
fun LoadingState(
    message: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        CircularProgressIndicator(color = color)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = textColor, textAlign = TextAlign.Center)
    }
}
