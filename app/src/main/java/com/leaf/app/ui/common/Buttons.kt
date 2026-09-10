package com.leaf.app.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leaf.app.ui.theme.QuireShape

/** Material's pill buttons are replaced with 10dp corners so buttons match cards. */
@Composable
fun QuireButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = QuireShape.Button,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun QuireOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = QuireShape.Button,
        contentPadding = contentPadding,
        content = content,
    )
}

/** Tighter side padding for three buttons sharing one row; the labels still get a full line. */
val CompactButtonPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

@Composable
fun QuireTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = QuireShape.Button,
        content = content,
    )
}
