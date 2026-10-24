package com.alo.downloader.di

import com.alo.downloader.data.db.FileDao
import com.alo.downloader.data.db.TaskDao
import com.alo.downloader.download.DownloadManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Access to app singletons from places Hilt cannot inject into, such as instrumented
 * tests that drive the real download pipeline.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun downloadManager(): DownloadManager
    fun taskDao(): TaskDao
    fun fileDao(): FileDao
}
