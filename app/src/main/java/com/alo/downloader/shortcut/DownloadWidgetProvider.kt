package com.alo.downloader.shortcut

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.alo.downloader.MainActivity
import com.alo.downloader.R
import com.alo.downloader.share.ShareActivity

/** Home screen widget: queue status plus a "download from clipboard" button. */
class DownloadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids, lastActive)
    }

    companion object {
        @Volatile private var lastActive = 0

        /** Called by the queue whenever the number of running downloads changes. */
        fun update(context: Context, active: Int) {
            lastActive = active
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DownloadWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, active)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, active: Int) {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val paste = PendingIntent.getActivity(
                context, 1, ShareActivity.clipboardIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val status = if (active > 0) {
                context.resources.getQuantityString(R.plurals.notif_queue_active, active, active)
            } else context.getString(R.string.widget_idle)
            val views = RemoteViews(context.packageName, R.layout.widget_download).apply {
                setTextViewText(R.id.widget_status, status)
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_paste, paste)
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
