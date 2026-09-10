package com.leaf.app.ui.tools.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.ui.theme.Spacing

/**
 * Every tool has the same shape: pick input, configure, save. Same layout, same button
 * positions, same progress treatment. Learn one, know all six.
 *
 * [body] is the scrolling input and config area (use [ToolSection] inside it), or a
 * custom non-scrolling body when [scrollable] is false. [saveBar] is sticky at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    saveBar: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    scrollable: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.screenHorizontal, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = saveBar,
                )
            }
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            content = body,
        )
    }
}

/** A titled block inside a tool body. A hairline separates sections, not rows. */
@Composable
fun ToolSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
        )
        content()
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
    }
}
