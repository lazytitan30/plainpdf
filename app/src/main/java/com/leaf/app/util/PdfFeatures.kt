package com.leaf.app.util

import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Capabilities that depend on the Android PDF extension module rather than the app.
 * The [ChecksSdkIntAtLeast] annotations let lint treat these as version guards.
 */
object PdfFeatures {

    /** Form editing and writing edited documents need Android 12+ with PDF extension 13. */
    @get:ChecksSdkIntAtLeast(extension = Build.VERSION_CODES.S, api = 13)
    val formEditingAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13

    /**
     * Ink and highlight annotations (androidx.pdf:pdf-ink) need Android 12+ with PDF
     * extension 18, which is where the platform learned to write annotations back.
     */
    @get:ChecksSdkIntAtLeast(extension = Build.VERSION_CODES.S, api = 18)
    val annotationsAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 18
}
