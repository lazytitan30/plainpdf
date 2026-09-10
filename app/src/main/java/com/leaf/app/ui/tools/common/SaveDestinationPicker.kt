package com.leaf.app.ui.tools.common

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.util.saf.SafAccess

/** Launches ACTION_CREATE_DOCUMENT for a single PDF and hands back the chosen URI. Persists the grant. */
class CreatePdfPicker internal constructor(private val launch: (String) -> Unit) {
    fun pick(suggestedName: String) = launch(suggestedName)
}

@Composable
fun rememberCreatePdfPicker(onPicked: (Uri) -> Unit): CreatePdfPicker {
    val saf = LocalAppContainer.current.saf
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            // A WorkManager job may outlive the activity; keep write access for it.
            saf.takePersistable(uri, result.data?.flags ?: 0)
            callback.value(uri)
        }
    }
    return remember(launcher) {
        CreatePdfPicker { name ->
            launcher.launch(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = SafAccess.PDF_MIME
                    putExtra(Intent.EXTRA_TITLE, name)
                },
            )
        }
    }
}

/** Launches ACTION_OPEN_DOCUMENT_TREE for multi-file output. Persists the grant. */
class TreePicker internal constructor(private val launch: () -> Unit) {
    fun pick() = launch()
}

@Composable
fun rememberTreePicker(saf: SafAccess, onPicked: (Uri) -> Unit): TreePicker {
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            saf.takePersistableTree(uri)
            callback.value(uri)
        }
    }
    return remember(launcher) { TreePicker { launcher.launch(null) } }
}

/** Launches ACTION_OPEN_DOCUMENT for one or more PDFs. Persists the grants. */
class PdfPicker internal constructor(private val launch: () -> Unit) {
    fun pick() = launch()
}

@Composable
fun rememberPdfPicker(saf: SafAccess, multiple: Boolean, onPicked: (List<Uri>) -> Unit): PdfPicker {
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uris = ArrayList<Uri>()
        data.data?.let { uris += it }
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris += it } }
        uris.forEach { saf.takePersistable(it, data.flags) }
        if (uris.isNotEmpty()) callback.value(uris.distinct())
    }
    return remember(launcher, multiple) {
        PdfPicker {
            launcher.launch(SafAccess.openPdfIntent().apply { if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) })
        }
    }
}
