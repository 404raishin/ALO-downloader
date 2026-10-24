package com.alo.downloader.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Long running foreground worker that drains the download queue. */
@HiltWorker
class DownloadQueueWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val executor: DownloadExecutor,
    private val notifier: DownloadNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            setForeground(notifier.foregroundInfo(0))
        } catch (e: Exception) {
            // Android 12+ may refuse a foreground service from the background; keep going
            // as a regular worker instead of failing the queue.
            Log.w("DownloadQueueWorker", "foreground not allowed", e)
        }
        executor.runQueue { active ->
            runCatching { setForeground(notifier.foregroundInfo(active)) }
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notifier.foregroundInfo(0)
}

/** Fires at a scheduled download time and wakes the queue. */
@HiltWorker
class ScheduledStartWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadManager: DownloadManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        downloadManager.kick()
        return Result.success()
    }
}
