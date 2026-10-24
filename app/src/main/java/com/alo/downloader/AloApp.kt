package com.alo.downloader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.download.DownloadManager
import com.alo.downloader.update.EngineUpdateWorker
import com.alo.downloader.download.DownloadNotifier
import com.alo.downloader.engine.ytdlp.YtDlpRuntime
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AloApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: DownloadNotifier
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var ytDlpRuntime: YtDlpRuntime
    @Inject lateinit var settingsRepository: SettingsRepository

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
        appScope.launch {
            EngineUpdateWorker.schedule(this@AloApp, settingsRepository.current().autoUpdateEngines)
        }
    }

    /** Coil loader that can also show frames of downloaded videos in the gallery. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
