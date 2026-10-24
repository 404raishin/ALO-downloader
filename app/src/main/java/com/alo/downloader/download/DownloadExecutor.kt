package com.alo.downloader.download

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.alo.downloader.core.engine.DownloadEngine
import com.alo.downloader.core.engine.EngineJob
import com.alo.downloader.core.engine.EngineProgress
import com.alo.downloader.core.engine.EngineResult
import com.alo.downloader.core.engine.ProgressListener
import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.DownloadOptionsCodec
import com.alo.downloader.core.model.DownloadStatus
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.core.model.Platform
import com.alo.downloader.core.queue.QueueEntry
import com.alo.downloader.core.queue.QueuePlanner
import com.alo.downloader.data.db.DownloadTaskEntity
import com.alo.downloader.data.db.DownloadedFileEntity
import com.alo.downloader.data.db.FileDao
import com.alo.downloader.data.db.TaskDao
import com.alo.downloader.data.settings.AppSettings
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.engine.EngineRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the unified download queue: picks tasks with [QueuePlanner], runs each through the
 * engines chosen by [EngineRegistry] (falling back to the next engine on failure) and moves
 * the results into public storage.
 */
@Singleton
class DownloadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val fileDao: FileDao,
    private val settingsRepository: SettingsRepository,
    private val storage: OutputStorage,
    private val notifier: DownloadNotifier,
    private val engines: EngineRegistry,
    private val credentials: TaskCredentials,
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val activeEngines = ConcurrentHashMap<String, DownloadEngine>()
    private val state = Mutex()
    private var running = false
    private var pendingWake = false

    /** Called by [DownloadManager]; returns false when no queue loop is running. */
    suspend fun offer(): Boolean = state.withLock {
        if (running) {
            pendingWake = true
            wake.trySend(Unit)
        }
        running
    }

    /** Stops the attempt for [taskId]; its status must already be updated by the caller. */
    suspend fun stop(taskId: String) {
        activeEngines[taskId]?.cancel(taskId)
        jobs[taskId]?.let {
            it.cancel()
            it.join()
        }
    }

    fun isRunning(taskId: String) = jobs.containsKey(taskId)

    /** Runs until the queue is drained. Returns immediately if another loop is active. */
    suspend fun runQueue(onActiveCountChanged: suspend (Int) -> Unit = {}) {
        val acquired = state.withLock { if (running) false else { running = true; pendingWake = false; true } }
        if (!acquired) return
        try {
            coroutineScope {
                var lastCount = -1
                while (isActive) {
                    state.withLock { pendingWake = false }
                    val settings = settingsRepository.current()
                    val tasks = taskDao.withStatus(
                        listOf(DownloadStatus.QUEUED, DownloadStatus.SCHEDULED, DownloadStatus.PREPARING, DownloadStatus.RUNNING),
                    )
                    val entries = tasks.map { it.toQueueEntry() }
                    val plan = QueuePlanner.plan(entries, settings.maxConcurrent, System.currentTimeMillis(), networkAllowed(settings))
                    plan.dueScheduled.forEach { taskDao.setStatus(it, DownloadStatus.QUEUED) }
                    for (id in plan.toStart) {
                        val job = launch { runTask(id) }
                        jobs[id] = job
                        job.invokeOnCompletion {
                            jobs.remove(id)
                            wake.trySend(Unit)
                        }
                    }
                    if (jobs.size != lastCount) {
                        lastCount = jobs.size
                        onActiveCountChanged(jobs.size)
                    }
                    val hasWaiting = entries.any { it.status == DownloadStatus.QUEUED } || plan.dueScheduled.isNotEmpty()
                    if (jobs.isEmpty() && (!hasWaiting || !networkAllowed(settings))) {
                        val exit = state.withLock {
                            if (pendingWake) false else { running = false; true }
                        }
                        if (exit) break else continue
                    }
                    withTimeoutOrNull(POLL_MS) { wake.receive() }
                }
            }
        } finally {
            withContext(NonCancellable) { state.withLock { running = false } }
        }
    }

    private fun DownloadTaskEntity.toQueueEntry(): QueueEntry {
        val effective = when {
            jobs.containsKey(id) -> DownloadStatus.RUNNING
            status.isActive -> DownloadStatus.QUEUED // left over from a killed process
            else -> status
        }
        return QueueEntry(
            id = id,
            status = effective,
            createdAt = createdAt,
            priority = priority,
            scheduledAt = scheduledAt,
            exclusiveGroup = plannedEngine?.takeIf { it == EngineType.GALLERY_DL.id },
        )
    }

    private fun networkAllowed(settings: AppSettings): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        @Suppress("DEPRECATION")
        val connected = cm.activeNetworkInfo?.isConnected ?: false
        if (!connected) return false
        return !settings.wifiOnly || !cm.isActiveNetworkMetered
    }

    private suspend fun runTask(id: String) = coroutineScope {
        val task = taskDao.get(id) ?: return@coroutineScope
        val settings = settingsRepository.current()
        val platform = Platform.fromId(task.platform)
        val options = DownloadOptionsCodec.decode(task.optionsJson)
        val workDir = storage.workDir(id).apply { mkdirs() }
        val forced = EngineType.fromId(task.forcedEngine)
        val plan = engines.plan(task.url, platform, forced, settings.engineFallback)

        var current = task.copy(
            status = DownloadStatus.PREPARING,
            startedAt = System.currentTimeMillis(),
            errorReason = null,
            errorMessage = null,
            progress = -1f,
        )
        taskDao.update(current)
        notifier.showProgress(current)

        val progress = MutableStateFlow<EngineProgress?>(null)
        val reporter = launch {
            var last: EngineProgress? = null
            while (isActive) {
                delay(PROGRESS_MS)
                val p = progress.value ?: continue
                if (p == last) continue
                last = p
                taskDao.setProgress(id, p.percent ?: -1f, p.speed, p.etaSeconds, p.filesDone, p.title)
                taskDao.get(id)?.takeIf { it.status.isActive }?.let(notifier::showProgress)
            }
        }
        val listener = ProgressListener { progress.value = it }
        val errors = mutableListOf<DownloadError>()
        val attempts = mutableListOf<String>()
        var auth: TaskCredentials.Prepared? = null

        try {
            auth = credentials.prepare(platform, File(workDir, ".meta"))
            for (engine in plan) {
                current = current.copy(status = DownloadStatus.RUNNING, engine = engine.type.id)
                taskDao.update(current)
                activeEngines[id] = engine
                val job = EngineJob(
                    taskId = id,
                    url = task.url,
                    platform = platform,
                    workDir = workDir,
                    options = options,
                    cookieFile = auth.cookieFile,
                    archiveFile = storage.archiveFile(archiveName(engine.type)),
                )
                when (val result = engine.download(job, listener)) {
                    is EngineResult.Success -> {
                        attempts += "${engine.type.displayName} ✓"
                        reporter.cancel()
                        finishSuccess(current, result, platform, settings, workDir, attempts)
                        return@coroutineScope
                    }
                    is EngineResult.Failure -> {
                        attempts += "${engine.type.displayName} ✗"
                        errors += result.error
                        Log.w(TAG, "$id failed with ${engine.type}: ${result.error}")
                        if (result.error.reason == FailureReason.CANCELED) break
                        if (!settings.engineFallback) break
                    }
                }
            }
            reporter.cancel()
            val latest = taskDao.get(id)
            if (latest == null || !latest.status.isActive) return@coroutineScope // paused / canceled by the user
            val error = DownloadError.mostRelevant(errors)
                ?: DownloadError(FailureReason.ENGINE_UNAVAILABLE, "No engine can handle this link")
            val failed = latest.copy(
                status = DownloadStatus.FAILED,
                errorReason = error.reason,
                errorMessage = error.message,
                attempts = attempts.joinToString(" → "),
                finishedAt = System.currentTimeMillis(),
            )
            taskDao.update(failed)
            notifier.showFailed(failed, error.reason, error.message)
        } catch (e: CancellationException) {
            plan.forEach { it.cancel(id) }
            withContext(NonCancellable) {
                // A system stop (constraints lost, process shutdown) puts the task back in line;
                // a user pause/cancel already changed the status.
                val latest = taskDao.get(id)
                if (latest != null && latest.status.isActive) taskDao.setStatus(id, DownloadStatus.QUEUED)
            }
            throw e
        } finally {
            reporter.cancel()
            activeEngines.remove(id)
            auth?.cleanup()
        }
    }

    private suspend fun finishSuccess(
        task: DownloadTaskEntity,
        result: EngineResult.Success,
        platform: Platform,
        settings: AppSettings,
        workDir: File,
        attempts: List<String>,
    ) {
        val stored = try {
            storage.publish(result.files, platform, settings.perPlatformFolders, settings.customTreeUri, workDir)
        } catch (e: Exception) {
            Log.e(TAG, "publish failed", e)
            val failed = task.copy(
                status = DownloadStatus.FAILED,
                errorReason = FailureReason.STORAGE,
                errorMessage = e.message ?: e.toString(),
                finishedAt = System.currentTimeMillis(),
            )
            taskDao.update(failed)
            notifier.showFailed(failed, FailureReason.STORAGE, failed.errorMessage.orEmpty())
            return
        }
        fileDao.insertAll(
            stored.map {
                DownloadedFileEntity(
                    taskId = task.id,
                    uri = it.uri.toString(),
                    displayName = it.displayName,
                    mimeType = it.mimeType,
                    sizeBytes = it.sizeBytes,
                    platform = platform.id,
                    location = it.location,
                )
            },
        )
        val latest = taskDao.get(task.id) ?: task
        val done = latest.copy(
            status = DownloadStatus.COMPLETED,
            engine = task.engine,
            progress = 100f,
            speed = null,
            etaSeconds = null,
            filesDone = stored.size,
            title = result.title ?: latest.title ?: stored.firstOrNull()?.displayName?.substringBeforeLast('.'),
            alreadyDownloaded = result.alreadyDownloaded,
            attempts = attempts.joinToString(" → "),
            finishedAt = System.currentTimeMillis(),
        )
        taskDao.update(done)
        workDir.deleteRecursively()
        notifier.showCompleted(done, stored.firstOrNull()?.uri, stored.firstOrNull()?.mimeType, stored.size)
    }

    private fun archiveName(type: EngineType) = when (type) {
        EngineType.GALLERY_DL -> "gallery-dl.sqlite3"
        else -> "${type.id}.txt"
    }

    private companion object {
        const val TAG = "DownloadExecutor"
        const val POLL_MS = 5_000L
        const val PROGRESS_MS = 700L
    }
}
