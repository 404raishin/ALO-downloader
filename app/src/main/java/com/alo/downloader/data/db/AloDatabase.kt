package com.alo.downloader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadTaskEntity::class, DownloadedFileEntity::class, CommandTemplateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AloDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun fileDao(): FileDao
    abstract fun templateDao(): TemplateDao
}
