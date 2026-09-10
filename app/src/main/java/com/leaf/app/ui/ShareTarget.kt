package com.leaf.app.ui

/**
 * Which share-sheet entry an intent came through. The manifest exposes two activity-aliases
 * that both land on MainActivity; only the component name tells them apart. Pure so the
 * decision is unit tested without Android.
 */
enum class ShareTarget {
    /** ".share.SignTarget": a PDF goes straight into Sign. */
    SIGN,

    /** ".share.ScanTarget": photos go straight into Scan, with crop, cleanup and recognition. */
    SCAN;

    companion object {
        const val SIGN_SUFFIX = "SignTarget"
        const val SCAN_SUFFIX = "ScanTarget"

        /** Null for the plain MainActivity or any launcher alias. */
        fun fromClassName(className: String?): ShareTarget? = when {
            className == null -> null
            className.endsWith(SIGN_SUFFIX) -> SIGN
            className.endsWith(SCAN_SUFFIX) -> SCAN
            else -> null
        }
    }
}
