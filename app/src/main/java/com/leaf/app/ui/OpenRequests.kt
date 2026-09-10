package com.leaf.app.ui

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Something the activity was asked to do by an intent. Consumed once by the UI. */
sealed interface OpenRequest {
    val nonce: Long

    /** VIEW or SEND of one PDF: open the reader. */
    data class Open(val uri: Uri, val grantFlags: Int, override val nonce: Long = System.nanoTime()) : OpenRequest

    /** SEND_MULTIPLE of PDFs: open Merge pre-populated. */
    data class MergePdfs(val uris: List<Uri>, val grantFlags: Int, override val nonce: Long = System.nanoTime()) : OpenRequest

    /** SEND_MULTIPLE of images: open Images to PDF pre-populated. */
    data class ImagesToPdf(val uris: List<Uri>, val grantFlags: Int, override val nonce: Long = System.nanoTime()) : OpenRequest

    /** SEND of one PDF through the "Sign with" share target: open Sign on it. */
    data class Sign(val uri: Uri, val grantFlags: Int, override val nonce: Long = System.nanoTime()) : OpenRequest

    /** SEND or SEND_MULTIPLE of images through the "Scan with" share target: run them through the scan flow. */
    data class ScanImages(val uris: List<Uri>, val grantFlags: Int, override val nonce: Long = System.nanoTime()) : OpenRequest

    /** A launcher shortcut or the widget: start a scan straight away. */
    data class Scan(override val nonce: Long = System.nanoTime()) : OpenRequest

    /** A launcher shortcut or the widget: show the PDF picker straight away. */
    data class PickPdf(override val nonce: Long = System.nanoTime()) : OpenRequest
}

/** Bridges MainActivity intents to the Compose tree. Lives on the activity. */
class OpenRequests {
    private val _pending = MutableStateFlow<OpenRequest?>(null)
    val pending: StateFlow<OpenRequest?> = _pending

    fun offer(intent: Intent?) {
        val request = fromIntent(intent) ?: return
        _pending.value = request
    }

    fun consume(request: OpenRequest) {
        if (_pending.value?.nonce == request.nonce) _pending.value = null
    }

    companion object {
        const val ACTION_SCAN = "com.leaf.app.action.SCAN"
        const val ACTION_OPEN = "com.leaf.app.action.OPEN"

        /**
         * Set on VIEW intents the app builds for itself (the Continue reading shortcuts). The URI
         * is already persisted in the library, so no grant is offered and none must be taken.
         */
        const val EXTRA_OWN_DOCUMENT = "com.leaf.app.extra.OWN_DOCUMENT"

        fun fromIntent(intent: Intent?): OpenRequest? {
            intent ?: return null
            val target = ShareTarget.fromClassName(intent.component?.className)
            return when (intent.action) {
                ACTION_SCAN -> OpenRequest.Scan()
                ACTION_OPEN -> OpenRequest.PickPdf()
                Intent.ACTION_VIEW -> intent.data?.takeIf { it.isFileLike() }?.let { uri ->
                    val grantFlags = if (intent.getBooleanExtra(EXTRA_OWN_DOCUMENT, false)) 0 else intent.flags
                    OpenRequest.Open(uri, grantFlags)
                }
                Intent.ACTION_SEND -> intent.streamExtra()?.takeIf { it.isFileLike() }?.let { uri ->
                    val image = intent.type.orEmpty().startsWith("image/")
                    when {
                        // The share targets are single-purpose; the plain entry keeps its old behaviour.
                        image && target == ShareTarget.SCAN -> OpenRequest.ScanImages(listOf(uri), intent.flags)
                        // One photo shared from the gallery goes to Images to PDF, one PDF to the reader.
                        image -> OpenRequest.ImagesToPdf(listOf(uri), intent.flags)
                        target == ShareTarget.SIGN -> OpenRequest.Sign(uri, intent.flags)
                        else -> OpenRequest.Open(uri, intent.flags)
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris = intent.streamExtras().filter { it.isFileLike() }
                    if (uris.isEmpty()) return null
                    val type = intent.type.orEmpty()
                    when {
                        type.startsWith("image/") && target == ShareTarget.SCAN -> OpenRequest.ScanImages(uris, intent.flags)
                        type.startsWith("image/") -> OpenRequest.ImagesToPdf(uris, intent.flags)
                        uris.size == 1 && target == ShareTarget.SIGN -> OpenRequest.Sign(uris[0], intent.flags)
                        uris.size == 1 -> OpenRequest.Open(uris[0], intent.flags)
                        else -> OpenRequest.MergePdfs(uris, intent.flags)
                    }
                }
                else -> null
            }
        }

        private fun Uri.isFileLike() = scheme == "content" || scheme == "file"

        @Suppress("DEPRECATION")
        private fun Intent.streamExtra(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

        @Suppress("DEPRECATION")
        private fun Intent.streamExtras(): List<Uri> =
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.filterNotNull() ?: listOfNotNull(streamExtra())
    }
}
