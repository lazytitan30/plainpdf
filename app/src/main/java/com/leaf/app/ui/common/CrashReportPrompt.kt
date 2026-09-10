package com.leaf.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.leaf.app.R
import com.leaf.app.ui.theme.QuireShape
import com.leaf.app.util.CrashReports

/** Asks once, on the launch after a crash, whether to share the report. Either answer clears it. */
@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(CrashReports.pending(context) != null) }
    if (!show) return
    val appName = stringResource(R.string.app_name)
    AlertDialog(
        onDismissRequest = { CrashReports.discard(context); show = false },
        shape = QuireShape.Dialog,
        title = { Text(stringResource(R.string.crash_title), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(R.string.crash_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            QuireTextButton(onClick = {
                CrashReports.share(context, appName)
                CrashReports.discard(context)
                show = false
            }) { Text(stringResource(R.string.crash_send)) }
        },
        dismissButton = {
            QuireTextButton(onClick = { CrashReports.discard(context); show = false }) { Text(stringResource(R.string.crash_not_now)) }
        },
    )
}
