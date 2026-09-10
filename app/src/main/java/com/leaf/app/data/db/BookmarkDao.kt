package com.leaf.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.leaf.app.data.db.entities.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY pageIndex, createdAt")
    fun observeForDocument(documentId: Long): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("UPDATE bookmarks SET label = :label WHERE id = :id")
    suspend fun relabel(id: Long, label: String?)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}
