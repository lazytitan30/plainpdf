package com.leaf.app.util.saf

import android.content.Intent

/** Which persistable permissions a URI actually carries. Never assume write; check. */
data class GrantFlags(val read: Boolean, val write: Boolean) {

    val intentFlags: Int
        get() = (if (read) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)

    companion object {
        val None = GrantFlags(read = false, write = false)
        val ReadOnly = GrantFlags(read = true, write = false)
        val ReadWrite = GrantFlags(read = true, write = true)

        fun fromIntentFlags(flags: Int): GrantFlags = GrantFlags(
            read = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
            write = flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
        )
    }
}
