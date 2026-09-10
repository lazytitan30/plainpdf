package com.leaf.app.data.docs

import com.leaf.app.data.db.BookmarkDao
import com.leaf.app.data.db.entities.BookmarkEntity
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(
    private val dao: BookmarkDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(documentId: Long): Flow<List<BookmarkEntity>> = dao.observeForDocument(documentId)

    suspend fun add(documentId: Long, pageIndex: Int, label: String?): Long =
        dao.insert(
            BookmarkEntity(
                documentId = documentId,
                pageIndex = pageIndex,
                label = label?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = clock(),
            ),
        )

    suspend fun relabel(id: Long, label: String?) = dao.relabel(id, label?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun remove(id: Long) = dao.delete(id)
}
