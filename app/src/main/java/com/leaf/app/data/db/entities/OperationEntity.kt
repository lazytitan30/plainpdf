package com.leaf.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Powers the "Recent output" strip on the Tools screen. Capped at 30 rows. */
@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** MERGE, SPLIT, ORGANISE, IMAGES_TO_PDF, PDF_TO_IMAGES, PASSWORD, WATERMARK */
    val type: String,
    /** "3 documents", "report.pdf" */
    val sourceSummary: String,
    val outputUri: String?,
    val outputName: String?,
    val completedAt: Long,
    val succeeded: Boolean,
    val errorMessage: String?,
)
