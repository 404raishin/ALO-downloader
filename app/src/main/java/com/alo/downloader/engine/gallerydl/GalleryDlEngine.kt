package com.alo.downloader.engine.gallerydl

import com.alo.downloader.core.engine.DownloadEngine
import com.alo.downloader.core.engine.EngineJob
import com.alo.downloader.core.engine.EngineProgress
import com.alo.downloader.core.engine.EngineResult
import com.alo.downloader.core.engine.ErrorClassifier
import com.alo.downloader.core.engine.GalleryDlArgs
import com.alo.downloader.core.engine.GalleryDlOutput
import com.alo.downloader.core.engine.OutputScanner
import com.alo.downloader.core.engine.ProgressListener
import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.engine.ytdlp.OutputTail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * gallery-dl running inside Chaquopy. Good at image galleries, carousels, profiles and
 * sites yt-dlp does not cover. Runs are serialised by the Python bridge.
 */
@Singleton
class GalleryDlEngine @Inject constructor(private val runtime: GalleryDlRuntime) : DownloadEngine {
    override val type = EngineType.GALLERY_DL

    private val threads = ConcurrentHashMap<String, Long>()
    private val canceled = ConcurrentHashMap.newKeySet<String>()

    override suspend fun isAvailable(): Boolean = runtime.bridge() != null

    override suspend fun download(job: EngineJob, listener: ProgressListener): EngineResult = coroutineScope {
        val bridge = runtime.bridge() ?: return@coroutineScope EngineResult.Failure(
            DownloadError(FailureReason.ENGINE_UNAVAILABLE, runtime.initError ?: "gallery-dl is not available", type),
        )
        job.workDir.mkdirs()
        canceled.remove(job.taskId)
        val args = GalleryDlArgs.build(job.url, job.options, job.workDir, job.cookieFile, job.archiveFile)
        val log = OutputTail()
        val done = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        listener.onProgress(EngineProgress(line = "gallery-dl"))

        val callback = object : GalleryDlCallback {
            override fun onThread(id: Long) {
                threads[job.taskId] = id
            }

            override fun onLine(stream: String, line: String) {
                when (val parsed = GalleryDlOutput.parse(stream, line)) {
                    is GalleryDlOutput.Line.Downloaded -> {
                        val n = done.incrementAndGet()
                        listener.onProgress(EngineProgress(filesDone = n, title = parsed.path.substringAfterLast('/').substringBeforeLast('.'), line = line))
                    }
                    is GalleryDlOutput.Line.Skipped -> skipped.incrementAndGet()
                    is GalleryDlOutput.Line.Log -> log.add(parsed.text)
                }
            }
        }

        // The Python call blocks this thread; a sibling coroutine turns coroutine
        // cancellation into a KeyboardInterrupt inside gallery-dl.
        val watcher = launch(Dispatchers.Default) {
            try {
                awaitCancellation()
            } finally {
                if (threads.containsKey(job.taskId)) cancel(job.taskId)
            }
        }
        val code = try {
            runInterruptible(Dispatchers.IO) {
                bridge.callAttr("run", args.toTypedArray(), callback, runtime.wheelDir.absolutePath).toInt()
            }
        } finally {
            threads.remove(job.taskId)
            watcher.cancel()
        }

        if (canceled.remove(job.taskId)) {
            return@coroutineScope EngineResult.Failure(DownloadError(FailureReason.CANCELED, "Canceled", type))
        }
        val files = OutputScanner.scan(job.workDir)
        when {
            files.isNotEmpty() -> EngineResult.Success(files, title = titleFor(files.size, files.first().nameWithoutExtension))
            code == 0 && skipped.get() > 0 -> EngineResult.Success(emptyList(), alreadyDownloaded = true)
            else -> {
                val text = log.text().ifBlank { "gallery-dl exited with code $code" }
                val error = ErrorClassifier.toError(text, type, job.cookieFile != null)
                EngineResult.Failure(
                    when {
                        error.reason != FailureReason.UNKNOWN -> error
                        (code and 64) != 0 -> error.copy(reason = FailureReason.UNSUPPORTED_URL)
                        (code and 16) != 0 -> error.copy(reason = if (job.cookieFile != null) FailureReason.COOKIE_EXPIRED else FailureReason.LOGIN_REQUIRED)
                        (code and 8) != 0 -> error.copy(reason = FailureReason.NOT_FOUND)
                        else -> error.copy(reason = FailureReason.NO_MEDIA)
                    },
                )
            }
        }
    }

    override fun cancel(taskId: String) {
        val thread = threads[taskId] ?: return
        canceled += taskId
        runCatching { runtime.bridge()?.callAttr("cancel", thread) }
    }

    private fun titleFor(count: Int, first: String): String = if (count == 1) first else "$first (+${count - 1})"
}
