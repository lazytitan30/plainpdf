package com.leaf.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.leaf.app.data.db.entities.OperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationDao {

    @Query("SELECT * FROM operations ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<OperationEntity>>

    @Insert
    suspend fun insert(operation: OperationEntity): Long

    /** Keep the newest [keep] rows. */
    @Query(
        "DELETE FROM operations WHERE id NOT IN " +
            "(SELECT id FROM operations ORDER BY completedAt DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int = 30)

    @Query("DELETE FROM operations WHERE id = :id")
    suspend fun delete(id: Long)
}
