package com.leaf.app.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.leaf.app.R

/**
 * Clipboard and share intents both cross a binder transaction with a hard 1 MB ceiling, and a
 * long book is more text than that. Anything beyond this is cut rather than crashing the app.
 */
private const val MAX_TEXT_CHARS = 200_000

fun Context.copyTextToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text.take(MAX_TEXT_CHARS)))
}

fun Context.shareText(text: String, subject: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text.take(MAX_TEXT_CHARS))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    runCatching { startActivity(Intent.createChooser(send, getString(R.string.action_share_text))) }
}

/** The text-to-speech page when the device has one, otherwise the settings home. */
fun Context.openTextToSpeechSettings() {
    val direct = runCatching { startActivity(Intent(TTS_SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    if (direct.isFailure) runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Not a public constant, but every Android build since Jelly Bean answers it. */
private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"
