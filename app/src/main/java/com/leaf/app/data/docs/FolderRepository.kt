package com.leaf.app.data.docs

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.leaf.app.data.db.FolderDao
import com.leaf.app.data.db.entities.FolderEntity
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** Indexed folder trees from ACTION_OPEN_DOCUMENT_TREE and lazy enumeration of the PDFs inside. */
class FolderRepository(
    private val folderDao: FolderDao,
    private val documents: DocumentRepository,
    private val resolver: ContentResolver,
    private val saf: SafAccess,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeAll(): Flow<List<FolderEntity>> = folderDao.observeAll()

    suspend fun findById(id: Long): FolderEntity? = folderDao.findById(id)

    /** Persist the grant and remember the tree. Returns the row, existing or new. */
    suspend fun add(treeUri: Uri): FolderEntity? {
        val grant = saf.takePersistableTree(treeUri)
        if (!grant.read) return null
        val key = treeUri.toString()
        folderDao.findByTreeUri(key)?.let { return it }
        val folder = FolderEntity(treeUri = key, displayName = saf.treeDisplayName(treeUri), addedAt = clock())
        val id = folderDao.insert(folder)
        return if (id > 0) folder.copy(id = id) else folderDao.findByTreeUri(key)
    }

    suspend fun remove(id: Long) {
        val folder = folderDao.findById(id) ?: return
        documents.folderRemoved(id)
        folderDao.delete(id)
        saf.release(Uri.parse(folder.treeUri))
    }

    /**
     * Walk the tree and upsert every PDF as a document row. Returns the number found, or
     * null when the tree can no longer be read (grant revoked, storage removed).
     */
    suspend fun refresh(folderId: Long): Int? = withContext(Dispatchers.IO) {
        val folder = folderDao.findById(folderId) ?: return@withContext null
        val tree = Uri.parse(folder.treeUri)
        val found = ArrayList<FoundPdf>()
        val rootId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (_: IllegalArgumentException) {
            return@withContext null
        }
        if (!walk(tree, rootId, found, depth = 0)) return@withContext null

        val seen = HashSet<String>()
        for (pdf in found) {
            seen += pdf.uri
            documents.indexInFolder(folderId, pdf.uri, pdf.displayName, pdf.sizeBytes)
        }
        // Rows that vanished from the tree: keep them if the user opened or starred them.
        for (stale in documents.listInFolder(folderId)) {
            if (stale.uri !in seen) {
                if (stale.isFavorite || stale.lastOpenedAt != null) documents.setFolderless(stale.id) else documents.remove(stale.id)
            }
        }
        found.size
    }

    private data class FoundPdf(val uri: String, val displayName: String, val sizeBytes: Long?)

    /** Returns false when the directory could not be queried at all. */
    private fun walk(tree: Uri, documentId: String, out: MutableList<FoundPdf>, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return true
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val cursor = try {
            resolver.query(children, projection, null, null, null)
        } catch (_: SecurityException) {
            return false
        } catch (_: FileNotFoundException) {
            return false
        } catch (_: IllegalArgumentException) {
            return false
        } ?: return false

        cursor.use {
            val idIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (it.moveToNext()) {
                val childId = it.getString(idIdx) ?: continue
                val mime = it.getString(mimeIdx) ?: ""
                val name = it.getString(nameIdx) ?: childId
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> walk(tree, childId, out, depth + 1)
                    mime == SafAccess.PDF_MIME || name.endsWith(".pdf", ignoreCase = true) -> {
                        val size = if (sizeIdx >= 0 && !it.isNull(sizeIdx)) it.getLong(sizeIdx) else null
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId)
                        out += FoundPdf(uri.toString(), name, size)
                    }
                }
            }
        }
        return true
    }

    companion object {
        const val MAX_DEPTH = 6
    }
}
