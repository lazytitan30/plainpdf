package com.leaf.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    /** null = show "Page N". */
    val label: String?,
    val createdAt: Long,
)
