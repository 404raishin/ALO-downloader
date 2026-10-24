package com.alo.downloader.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.alo.downloader.core.link.LinkExtractor
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.DownloadOptionsCodec
import com.alo.downloader.core.model.DownloadStatus
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.router.PlatformResolver
import com.alo.downloader.data.db.DownloadTaskEntity
import com.alo.downloader.data.db.FileDao
import com.alo.downloader.data.db.TaskDao
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.engine.EngineRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Public entry point for creating and controlling downloads. */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val fileDao: FileDao,
    private val settingsRepository: SettingsRepository,
    private val executor: DownloadExecutor,
    private val notifier: DownloadNotifier,
    private val storage: OutputStorage,
    private val engines: EngineRegistry,
) {

    data class Request(
        val url: String,
        val options: DownloadOptions? = null,
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val forcedEngine: EngineType? = null,
        /** Epoch millis; null starts as soon as a slot is free. */
        val scheduledAt: Long? = null,
        val priority: Int = 0,
    )

    /** Extracts every link in [text] and queues it with the default options. */
    suspend fun enqueueText(text: String?): List<String> {
        val links = LinkExtractor.extractAll(text)
        return enqueue(links.map { Request(it) })
    }

    suspend fun enqueue(requests: List<Request>): List<String> {
        if (requests.isEmpty()) return emptyList()
        val settings = settingsRepository.current()
        val tasks = requests.map { r ->
            val platform = PlatformResolver.resolve(r.url)
            val options = r.options ?: settings.toDownloadOptions()
            val scheduled = r.scheduledAt?.takeIf { it > System.currentTimeMillis() }
            DownloadTaskEntity(
                id = UUID.randomUUID().toString(),
                url = r.url,
                title = r.title,
                thumbnailUrl = r.thumbnailUrl,
                platform = platform.id,
                status = if (scheduled != null) DownloadStatus.SCHEDULED else DownloadStatus.QUEUED,
                forcedEngine = r.forcedEngine?.id,
                plannedEngine = engines.plan(r.url, platform, r.forcedEngine, settings.engineFallback).firstOrNull()?.type?.id,
                optionsJson = DownloadOptionsCodec.encode(options),
                scheduledAt = scheduled,
                priority = r.priority,
            )
        }
        taskDao.insertAll(tasks)
        tasks.filter { it.scheduledAt != null }.forEach { scheduleStart(it.id, it.scheduledAt!!) }
        if (tasks.any { it.status == DownloadStatus.QUEUED }) kick()
        return tasks.map { it.id }
    }

    suspend fun pause(id: String) {
        val task = taskDao.get(id) ?: return
        if (!task.status.canPause) return
        taskDao.setStatus(id, DownloadStatus.PAUSED)
        executor.stop(id)
        taskDao.get(id)?.let(notifier::showPaused)
    }

    suspend fun resume(id: String) {
        val task = taskDao.get(id) ?: return
        if (!task.status.canResume) return
        taskDao.requeue(id)
        notifier.cancel(id)
        kick()
    }

    suspend fun retry(id: String) {
        val task = taskDao.get(id) ?: return
        if (!task.status.canRetry && task.status != DownloadStatus.COMPLETED) return
        taskDao.requeue(id)
        notifier.cancel(id)
        kick()
    }

    suspend fun cancel(id: String) {
        val task = taskDao.get(id) ?: return
        if (task.status.isFinished) return
        taskDao.setStatus(id, DownloadStatus.CANCELED)
        executor.stop(id)
        storage.workDir(id).deleteRecursively()
        WorkManager.getInstance(context).cancelUniqueWork(scheduleName(id))
        notifier.cancel(id)
    }

    /** Removes the task from history; optionally deletes the downloaded files too. */
    suspend fun delete(id: String, deleteFiles: Boolean) {
        cancel(id)
        if (deleteFiles) {
            fileDao.forTask(id).forEach { file ->
                runCatching { context.contentResolver.delete(android.net.Uri.parse(file.uri), null, null) }
            }
        }
        taskDao.delete(id)
        notifier.cancel(id)
    }

    suspend fun clearFinished() = taskDao.clearFinished()

    /** Re-queues tasks interrupted by a process death and restarts the queue. */
    suspend fun recoverOnStart() {
        taskDao.resetInterrupted()
        if (taskDao.pendingCount() > 0) kick()
    }

    /** Makes sure a queue worker is (or will be) running. */
    suspend fun kick() {
        if (executor.offer()) return
        val settings = settingsRepository.current()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadQueueWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(QUEUE_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleStart(id: String, at: Long) {
        val request = OneTimeWorkRequestBuilder<ScheduledStartWorker>()
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(scheduleName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleName(id: String) = "schedule-$id"

    companion object {
        const val QUEUE_WORK = "download-queue"
    }
}
