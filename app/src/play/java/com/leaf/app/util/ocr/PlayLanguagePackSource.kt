package com.leaf.app.util.ocr

import android.content.Context
import android.util.Log
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Play flavour: each language is an on-demand asset pack named `lang_<code>`. Google Play
 * downloads it with its own network access; this class only watches the state, checks the
 * delivered file with Tesseract, copies it next to the bundled ones and then tells Play the
 * pack can go, so the language is stored once.
 */
class PlayLanguagePackSource(
    context: Context,
    private val tessdata: TessdataStore,
    private val scope: CoroutineScope,
    private val onInstalled: (String) -> Unit,
) : LanguagePackSource {

    private val manager: AssetPackManager = AssetPackManagerFactory.getInstance(context.applicationContext)

    private val _states = MutableStateFlow<Map<String, PackState>>(emptyMap())
    override val states: StateFlow<Map<String, PackState>> = _states

    override val canDownload: Boolean = true

    private val listener = AssetPackStateUpdateListener { state -> onStateUpdate(state) }

    init {
        refresh()
        manager.registerListener(listener)
    }

    override fun refresh() {
        _states.update { current ->
            OcrLanguages.downloadable.associate { language ->
                val code = language.code
                val previous = current[code]
                val next = when {
                    tessdata.isInstalled(code) -> PackState.Installed
                    previous is PackState.Downloading || previous is PackState.Installing -> previous
                    else -> PackState.NotInstalled
                }
                code to next
            }
        }
    }

    override fun download(code: String) {
        if (!OcrLanguages.isDownloadable(code)) return
        if (tessdata.isInstalled(code)) {
            set(code, PackState.Installed)
            return
        }
        set(code, PackState.Downloading(null))
        manager.fetch(listOf(OcrLanguages.packName(code)))
            .addOnFailureListener { e ->
                Log.w(TAG, "Pack request failed", e)
                set(code, PackState.Failed(e.message))
            }
    }

    override fun cancel(code: String) {
        manager.cancel(listOf(OcrLanguages.packName(code)))
        set(code, PackState.NotInstalled)
    }

    private fun onStateUpdate(state: AssetPackState) {
        val code = OcrLanguages.codeOfPack(state.name()) ?: return
        when (state.status()) {
            AssetPackStatus.PENDING, AssetPackStatus.WAITING_FOR_WIFI -> set(code, PackState.Downloading(null))
            AssetPackStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload()
                val fraction = if (total > 0) (state.bytesDownloaded().toFloat() / total).coerceIn(0f, 1f) else null
                set(code, PackState.Downloading(fraction))
            }
            AssetPackStatus.TRANSFERRING -> set(code, PackState.Installing)
            AssetPackStatus.COMPLETED -> {
                set(code, PackState.Installing)
                scope.launch(Dispatchers.IO) { install(code) }
            }
            AssetPackStatus.FAILED -> set(code, PackState.Failed("Play error ${state.errorCode()}"))
            AssetPackStatus.CANCELED -> set(code, PackState.NotInstalled)
            // Play asks for confirmation only for large downloads on mobile data; these packs
            // are a few megabytes, so treat it as something to retry on Wi-Fi.
            AssetPackStatus.REQUIRES_USER_CONFIRMATION -> set(code, PackState.Failed(null))
            else -> if (!tessdata.isInstalled(code)) set(code, PackState.NotInstalled)
        }
    }

    private fun install(code: String) {
        val packName = OcrLanguages.packName(code)
        val location = manager.getPackLocation(packName)
        val assetsPath = location?.assetsPath()
        if (assetsPath == null) {
            set(code, PackState.Failed("Pack location unknown"))
            return
        }
        val file = File(assetsPath, "tessdata/${TessdataStore.fileName(code)}")
        tessdata.installFromFile(code, file)
            .onSuccess {
                set(code, PackState.Installed)
                runCatching { manager.removePack(packName) }
                onInstalled(code)
            }
            .onFailure { e ->
                Log.w(TAG, "Pack install failed", e)
                set(code, PackState.Failed(e.message))
            }
    }

    private fun set(code: String, state: PackState) {
        _states.update { it + (code to state) }
    }

    private companion object {
        const val TAG = "Leaf"
    }
}
