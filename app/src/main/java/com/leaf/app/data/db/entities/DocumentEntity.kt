package com.leaf.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents", indices = [Index(value = ["uri"], unique = true)])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    /** Name reported by the provider; refreshed on every open. */
    val displayName: String,
    /** User-chosen label shown instead of [displayName]. Never touches the file. */
    val label: String? = null,
    val sizeBytes: Long?,
    val pageCount: Int?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val lastPage: Int = 0,
    val lastZoom: Float = 1f,
    val lastScrollY: Int = 0,
    val isFavorite: Boolean = false,
    /** cacheDir/thumbs/{id}.webp, regenerated if the cache is cleared. */
    val thumbnailPath: String?,
    val permissionLost: Boolean = false,
    val hasWriteGrant: Boolean = false,
    val isEncrypted: Boolean = false,
    /** False when the URI came from a one-shot grant that could not be persisted. Shown as "not saved". */
    val isPersisted: Boolean = true,
    /** null = opened ad hoc, not from an indexed folder. */
    val folderId: Long? = null,
    /** Per-document page display override; null falls back to the global default. */
    val pageDisplayMode: String? = null,
) {
    val title: String get() = label?.takeIf { it.isNotBlank() } ?: displayName
}
