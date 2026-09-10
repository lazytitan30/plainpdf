package com.leaf.app.data.docs

import android.net.Uri
import com.leaf.app.util.saf.GrantFlags
import com.leaf.app.util.saf.SafAccess

class SafDocumentProbe(private val saf: SafAccess, private val ownAuthority: String? = null) : DocumentProbe {

    override fun info(uri: String): DocumentProbe.Info? =
        saf.queryInfo(Uri.parse(uri))?.let { DocumentProbe.Info(it.displayName, it.sizeBytes) }

    override fun canRead(uri: String): Boolean = saf.canRead(Uri.parse(uri))

    override fun persistedGrant(uri: String): GrantFlags {
        val parsed = Uri.parse(uri)
        if (ownAuthority != null && parsed.authority == ownAuthority) return GrantFlags.ReadWrite
        return saf.persistedGrant(parsed)
    }
}
