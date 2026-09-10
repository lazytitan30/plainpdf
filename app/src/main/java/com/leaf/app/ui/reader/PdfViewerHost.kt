package com.leaf.app.ui.reader

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment

/** Hosts the androidx viewer fragment inside Compose and wires it to the ViewModel. */
@Composable
fun PdfViewerHost(
    uri: Uri,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(viewModel) {
        onDispose { viewModel.controller = null }
    }
    AndroidFragment<QuirePdfFragment>(
        modifier = modifier,
        onUpdate = { fragment ->
            fragment.host = viewModel
            if (viewModel.controller !== fragment) viewModel.controller = fragment
            fragment.ensureDocument(uri)
        },
    )
}
