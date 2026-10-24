package com.alo.downloader.di

import android.content.Context
import androidx.room.Room
import com.alo.downloader.data.db.AloDatabase
import com.alo.downloader.data.db.FileDao
import com.alo.downloader.data.db.TaskDao
import com.alo.downloader.data.db.TemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AloDatabase =
        Room.databaseBuilder(context, AloDatabase::class.java, "alo.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun taskDao(db: AloDatabase): TaskDao = db.taskDao()

    @Provides fun fileDao(db: AloDatabase): FileDao = db.fileDao()

    @Provides fun templateDao(db: AloDatabase): TemplateDao = db.templateDao()

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}
