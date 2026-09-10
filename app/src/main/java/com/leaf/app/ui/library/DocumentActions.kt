package com.leaf.app.ui.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.leaf.app.R
import com.leaf.app.util.saf.SafAccess
import java.io.File

/** Share a document with another app. The receiver gets a read grant for the URI only. */
fun Context.shareDocument(uri: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = SafAccess.PDF_MIME
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(Intent.createChooser(send, getString(R.string.action_share))) }
}

/** Share one of our own files (under a FileProvider path) with another app, with a read grant only. */
fun Context.shareFile(file: File, mime: String, title: String) {
    val uri = runCatching { FileProvider.getUriForFile(this, "$packageName.files", file) }.getOrNull() ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        clipData = ClipData.newRawUri(title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(Intent.createChooser(send, getString(R.string.action_share))) }
}
