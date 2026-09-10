package com.leaf.app.util.ocr

import kotlinx.coroutines.flow.StateFlow

/** Where one downloadable language pack stands on this phone. */
sealed interface PackState {
    data object NotInstalled : PackState

    /** [fraction] is 0..1 when Play reports sizes, null while it is only queued. */
    data class Downloading(val fraction: Float?) : PackState

    /** Downloaded; Tesseract is checking the file and moving it into place. */
    data object Installing : PackState

    data object Installed : PackState

    data class Failed(val message: String?) : PackState
}

/**
 * Downloads language packs for text recognition. The app has no network permission, so the
 * Play build asks Google Play to fetch an asset pack and only reads the file afterwards; the
 * FOSS build cannot download at all and offers the file import instead.
 */
interface LanguagePackSource {
    /** False on builds that cannot ask Play for packs. */
    val canDownload: Boolean

    /** State per Tesseract code, for every downloadable language the app knows. */
    val states: StateFlow<Map<String, PackState>>

    fun download(code: String)

    fun cancel(code: String)

    /** Re-reads what is on disk, after an import or a delete. */
    fun refresh()
}
