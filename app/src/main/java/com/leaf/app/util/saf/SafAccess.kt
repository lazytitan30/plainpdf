package com.leaf.app.util.saf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Every Storage Access Framework call the app makes goes through here, so the
 * SecurityException and FileNotFoundException handling lives in one place.
 */
class SafAccess(
    private val resolver: ContentResolver,
    private val ownAuthority: String? = null,
    /** Display name when the provider offers none; the app passes the localised string. */
    private val fallbackName: String = "document.pdf",
) {

    /**
     * Persist whatever the picker actually granted. Tries read+write first when write was
     * offered, then falls back to read only. Returns what was kept.
     */
    fun takePersistable(uri: Uri, resultFlags: Int): GrantFlags {
        val offered = GrantFlags.fromIntentFlags(resultFlags)
        if (offered.write) {
            if (tryTake(uri, GrantFlags.ReadWrite.intentFlags)) return GrantFlags.ReadWrite
        }
        if (offered.read && tryTake(uri, GrantFlags.ReadOnly.intentFlags)) return GrantFlags.ReadOnly
        return GrantFlags.None
    }

    /** Tree URIs from ACTION_OPEN_DOCUMENT_TREE do not come with result flags; ask for both. */
    fun takePersistableTree(uri: Uri): GrantFlags = takePersistable(uri, GrantFlags.ReadWrite.intentFlags)

    private fun tryTake(uri: Uri, flags: Int): Boolean = try {
        resolver.takePersistableUriPermission(uri, flags)
        true
    } catch (_: SecurityException) {
        false
    }

    /** What the system still remembers for this URI. Grants can be revoked behind our back. */
    fun persistedGrant(uri: Uri): GrantFlags {
        val perm = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return GrantFlags.None
        return GrantFlags(read = perm.isReadPermission, write = perm.isWritePermission)
    }

    fun release(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, GrantFlags.ReadWrite.intentFlags)
        } catch (_: SecurityException) {
            // Already gone. Nothing to release.
        }
    }

    /** True when the document can be opened for reading right now. */
    fun canRead(uri: Uri): Boolean = try {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: FileNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    data class DocumentInfo(val displayName: String, val sizeBytes: Long?)

    fun queryInfo(uri: Uri): DocumentInfo? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else null
                DocumentInfo(displayName = name ?: uri.lastPathSegment ?: fallbackName, sizeBytes = size)
            }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * Human label for where a document lives: "primary:Documents/PDFs/a.pdf" -> "Documents/PDFs".
     * Null when the URI is not a documents-provider URI or carries no folder, so callers
     * can leave the row out rather than show a raw content:// string.
     */
    fun documentFolderName(uri: Uri): String? {
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val path = docId.substringAfter(':', "")
        if (path.isEmpty() || !path.contains('/')) return null
        val folder = path.trimEnd('/').substringBeforeLast('/')
        return folder.ifEmpty { null }
    }

    /** Human label for a tree URI: "primary:Documents/PDFs" -> "PDFs". */
    fun treeDisplayName(treeUri: Uri): String {
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: IllegalArgumentException) {
            return treeUri.lastPathSegment ?: treeUri.toString()
        }
        val path = docId.substringAfter(':', docId)
        return path.trimEnd('/').substringAfterLast('/').ifEmpty { docId.substringBefore(':') }
    }

    /**
     * Stream a verified temp file into [destination]. Uses "wt" so an existing file is
     * truncated rather than appended. Throws on failure; the caller keeps the temp file.
     */
    @Throws(IOException::class)
    fun commit(temp: File, destination: Uri) {
        val output = try {
            resolver.openOutputStream(destination, "wt")
        } catch (e: SecurityException) {
            throw IOException("No write permission for destination", e)
        } ?: throw IOException("Could not open destination for writing")
        output.use { out ->
            temp.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE) }
            out.flush()
        }
    }

    /** Create a child document inside a tree. Returns null if the provider refuses. */
    fun createChild(treeUri: Uri, mimeType: String, displayName: String): Uri? = try {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        DocumentsContract.createDocument(resolver, parent, mimeType, displayName)
    } catch (_: SecurityException) {
        null
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun delete(uri: Uri): Boolean = try {
        // Files the app made itself (scans) sit behind a FileProvider, which does not
        // implement the DocumentsContract call; delete through the provider instead.
        if (ownAuthority != null && uri.authority == ownAuthority) resolver.delete(uri, null, null) > 0
        else DocumentsContract.deleteDocument(resolver, uri)
    } catch (_: SecurityException) {
        false
    } catch (_: FileNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        const val PDF_MIME = "application/pdf"

        /** Intent for ACTION_OPEN_DOCUMENT that asks for write up front, per the spec. */
        fun openPdfIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = PDF_MIME
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
    }
}
