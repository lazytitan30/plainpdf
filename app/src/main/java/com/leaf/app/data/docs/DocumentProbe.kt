package com.leaf.app.data.docs

import com.leaf.app.util.saf.GrantFlags

/**
 * The slice of SAF the document repository needs. String URIs keep it usable from
 * plain JVM unit tests, where android.net.Uri is a stub.
 */
interface DocumentProbe {
    data class Info(val displayName: String, val sizeBytes: Long?)

    /** Name and size, or null when the provider refuses or the document is gone. */
    fun info(uri: String): Info?

    /** True when the document can be opened for reading right now. */
    fun canRead(uri: String): Boolean

    /** What the system still remembers for this URI. */
    fun persistedGrant(uri: String): GrantFlags
}
