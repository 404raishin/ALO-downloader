package com.alo.downloader.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles pause / resume / cancel / retry buttons on download notifications. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var downloadManager: DownloadManager

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> downloadManager.pause(taskId)
                    ACTION_RESUME -> downloadManager.resume(taskId)
                    ACTION_CANCEL -> downloadManager.cancel(taskId)
                    ACTION_RETRY -> downloadManager.retry(taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.alo.downloader.action.PAUSE"
        const val ACTION_RESUME = "com.alo.downloader.action.RESUME"
        const val ACTION_CANCEL = "com.alo.downloader.action.CANCEL"
        const val ACTION_RETRY = "com.alo.downloader.action.RETRY"
        const val EXTRA_TASK_ID = "task_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
