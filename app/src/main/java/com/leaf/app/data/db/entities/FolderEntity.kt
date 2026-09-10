package com.leaf.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders", indices = [Index(value = ["treeUri"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val addedAt: Long,
)
