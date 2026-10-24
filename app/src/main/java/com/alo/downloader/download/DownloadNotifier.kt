package com.alo.downloader.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.alo.downloader.MainActivity
import com.alo.downloader.R
import com.alo.downloader.core.model.DownloadStatus
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.data.db.DownloadTaskEntity
import com.alo.downloader.ui.common.failureReasonText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_QUEUE, context.getString(R.string.channel_queue), NotificationManager.IMPORTANCE_MIN),
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
                NotificationChannel(CHANNEL_RESULT, context.getString(R.string.channel_result), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun foregroundInfo(active: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_QUEUE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_queue_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_queue_active, active, active))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(FOREGROUND_ID, notification)
        }
    }

    fun showProgress(task: DownloadTaskEntity) {
        val title = task.title ?: task.url
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        val percent = task.progress
        if (task.status == DownloadStatus.PREPARING || percent < 0) {
            builder.setProgress(0, 0, true)
            builder.setContentText(
                if (task.filesDone > 0) context.resources.getQuantityString(R.plurals.files_done, task.filesDone, task.filesDone)
                else context.getString(R.string.status_preparing),
            )
        } else {
            builder.setProgress(100, percent.toInt(), false)
            builder.setContentText(listOfNotNull("${percent.toInt()}%", task.speed, task.etaSeconds?.let(::formatEta)).joinToString(" · "))
        }
        builder.addAction(0, context.getString(R.string.action_pause), actionIntent(NotificationActionReceiver.ACTION_PAUSE, task.id))
        builder.addAction(0, context.getString(R.string.action_cancel), actionIntent(NotificationActionReceiver.ACTION_CANCEL, task.id))
        notify(task.id.notificationId(), builder.build())
    }

    fun showPaused(task: DownloadTaskEntity) {
        val n = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title ?: task.url)
            .setContentText(context.getString(R.string.status_paused))
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.action_resume), actionIntent(NotificationActionReceiver.ACTION_RESUME, task.id))
            .addAction(0, context.getString(R.string.action_cancel), actionIntent(NotificationActionReceiver.ACTION_CANCEL, task.id))
            .build()
        notify(task.id.notificationId(), n)
    }

    fun showCompleted(task: DownloadTaskEntity, firstFile: Uri?, mime: String?, fileCount: Int) {
        val open = firstFile?.let {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(it, mime ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, task.id.notificationId(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } ?: openAppIntent()
        val text = if (task.alreadyDownloaded) context.getString(R.string.status_already_downloaded)
        else context.resources.getQuantityString(R.plurals.notif_files_saved, fileCount, fileCount)
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title ?: task.url)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notify(task.id.notificationId(), n)
    }

    fun showFailed(task: DownloadTaskEntity, reason: FailureReason, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_failed_title, task.title ?: task.url))
            .setContentText(context.getString(failureReasonText(reason)))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(failureReasonText(reason)) + "\n" + message))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.action_retry), actionIntent(NotificationActionReceiver.ACTION_RETRY, task.id))
        if (reason.needsLogin) {
            builder.addAction(0, context.getString(R.string.action_login), openAppIntent(loginPlatform = task.platform))
        }
        notify(task.id.notificationId(), builder.build())
    }

    fun cancel(taskId: String) = manager.cancel(taskId.notificationId())

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: android.app.Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.notify(id, notification)
    }

    private fun openAppIntent(loginPlatform: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        loginPlatform?.let { intent.putExtra(MainActivity.EXTRA_LOGIN_PLATFORM, it) }
        return PendingIntent.getActivity(
            context, loginPlatform?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun actionIntent(action: String, taskId: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context, (action + taskId).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun String.notificationId(): Int = 1000 + (hashCode() and 0x0fffffff)

    companion object {
        const val CHANNEL_QUEUE = "queue"
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_RESULT = "result"
        const val FOREGROUND_ID = 1

        fun formatEta(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
