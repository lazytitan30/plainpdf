package com.leaf.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.leaf.app.data.db.entities.BookmarkEntity
import com.leaf.app.data.db.entities.DocumentEntity
import com.leaf.app.data.db.entities.FolderEntity
import com.leaf.app.data.db.entities.OperationEntity

@Database(
    entities = [DocumentEntity::class, BookmarkEntity::class, FolderEntity::class, OperationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun folderDao(): FolderDao
    abstract fun operationDao(): OperationDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "leaf.db").build()
    }
}
