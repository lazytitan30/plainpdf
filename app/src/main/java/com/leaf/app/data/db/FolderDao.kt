package com.leaf.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leaf.app.data.db.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun findById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE treeUri = :treeUri LIMIT 1")
    suspend fun findByTreeUri(treeUri: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}
