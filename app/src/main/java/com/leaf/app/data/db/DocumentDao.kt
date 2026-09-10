package com.leaf.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.leaf.app.data.db.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun findById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC")
    fun observeRecent(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY displayName COLLATE NOCASE")
    fun observeFavourites(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY displayName COLLATE NOCASE")
    fun observeInFolder(folderId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId")
    suspend fun listInFolder(folderId: Long): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE thumbnailPath IS NOT NULL")
    suspend fun listWithThumbnails(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("UPDATE documents SET lastPage = :page, lastZoom = :zoom, lastScrollY = :scrollY WHERE id = :id")
    suspend fun updatePosition(id: Long, page: Int, zoom: Float, scrollY: Int)

    @Query("UPDATE documents SET pageCount = :pageCount WHERE id = :id")
    suspend fun updatePageCount(id: Long, pageCount: Int)

    @Query("UPDATE documents SET lastOpenedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("UPDATE documents SET lastOpenedAt = NULL WHERE id = :id")
    suspend fun clearLastOpened(id: Long)

    @Query("UPDATE documents SET label = :label WHERE id = :id")
    suspend fun setLabel(id: Long, label: String?)

    @Query("UPDATE documents SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE documents SET permissionLost = :lost WHERE id = :id")
    suspend fun setPermissionLost(id: Long, lost: Boolean)

    @Query("UPDATE documents SET thumbnailPath = :path WHERE id = :id")
    suspend fun setThumbnailPath(id: Long, path: String?)

    @Query("UPDATE documents SET thumbnailPath = NULL")
    suspend fun clearAllThumbnailPaths()

    @Query("UPDATE documents SET pageDisplayMode = :mode WHERE id = :id")
    suspend fun setPageDisplayMode(id: Long, mode: String?)

    @Query("UPDATE documents SET isEncrypted = :encrypted WHERE id = :id")
    suspend fun setEncrypted(id: Long, encrypted: Boolean)

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    /** Folder removed: rows nobody opened or starred go away, the rest become ad hoc. */
    @Query("DELETE FROM documents WHERE folderId = :folderId AND isFavorite = 0 AND lastOpenedAt IS NULL")
    suspend fun deleteUntouchedInFolder(folderId: Long)

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachFolder(folderId: Long)

    @Query("UPDATE documents SET lastOpenedAt = NULL WHERE isFavorite = 0 AND folderId IS NOT NULL")
    suspend fun hideFolderDocumentsFromRecents()

    @Query("DELETE FROM documents WHERE isFavorite = 0 AND folderId IS NULL")
    suspend fun deleteAdHocNonFavourites()

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}
