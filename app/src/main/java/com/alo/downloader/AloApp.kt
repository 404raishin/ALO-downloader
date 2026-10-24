package com.alo.downloader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.alo.downloader.download.DownloadManager
import com.alo.downloader.download.DownloadNotifier
import com.alo.downloader.engine.ytdlp.YtDlpRuntime
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AloApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: DownloadNotifier
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var ytDlpRuntime: YtDlpRuntime

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        appScope.launch {
            // Unpack the engine runtime early so the first download starts faster.
            ytDlpRuntime.ensureReady()
        }
        appScope.launch { downloadManager.recoverOnStart() }
    }
}
