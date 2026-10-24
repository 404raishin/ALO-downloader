package com.alo.downloader.engine.ytdlp

import com.alo.downloader.core.engine.DownloadEngine
import com.alo.downloader.core.engine.EngineJob
import com.alo.downloader.core.engine.EngineProgress
import com.alo.downloader.core.engine.EngineResult
import com.alo.downloader.core.engine.EngineStage
import com.alo.downloader.core.engine.ErrorClassifier
import com.alo.downloader.core.engine.OutputScanner
import com.alo.downloader.core.engine.ProgressListener
import com.alo.downloader.core.engine.YtDlpArgs
import com.alo.downloader.core.engine.YtDlpOutput
import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpEngine @Inject constructor(private val runtime: YtDlpRuntime) : DownloadEngine {
    override val type = EngineType.YT_DLP

    override suspend fun isAvailable(): Boolean = runtime.ensureReady()

    override suspend fun download(job: EngineJob, listener: ProgressListener): EngineResult {
        if (!runtime.ensureReady()) {
            return EngineResult.Failure(
                DownloadError(FailureReason.ENGINE_UNAVAILABLE, runtime.initError ?: "yt-dlp is not available", type),
            )
        }
        job.workDir.mkdirs()
        val metaFile = File(job.workDir, ".meta/info.tsv").apply { parentFile?.mkdirs(); delete() }
        val args = YtDlpArgs.build(job.options, job.workDir, job.cookieFile, job.archiveFile) +
            listOf("--print-to-file", "video:%(title)s\t%(thumbnail)s", metaFile.absolutePath)
        val request = YoutubeDLRequest(job.url).addCommands(args).withCacheDir(runtime)
        val log = OutputTail()
        var title: String? = null
        var lastMetaCheck = 0L
        var stage: EngineStage? = null
        var percent: Float? = null

        return coroutineScope {
            // execute() blocks; kill the yt-dlp process as soon as the coroutine is cancelled.
            val watcher = launch(Dispatchers.Default) {
                try {
                    awaitCancellation()
                } finally {
                    YoutubeDL.getInstance().destroyProcessById(job.taskId)
                }
            }
            try {
                val response = runInterruptible(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request, job.taskId, false) { progress, eta, line ->
                        log.add(line)
                        val now = System.currentTimeMillis()
                        if (title == null && now - lastMetaCheck > 1000) {
                            lastMetaCheck = now
                            title = readTitle(metaFile)
                        }
                        YtDlpOutput.stage(line)?.let {
                            if (it != stage) percent = null
                            stage = it
                        }
                        // Keep the last percentage between download lines so the bar does not flicker.
                        (progress.takeIf { it >= 0 } ?: YtDlpOutput.percent(line))?.let { percent = it }
                        listener.onProgress(
                            EngineProgress(
                                percent = percent,
                                etaSeconds = eta.takeIf { it >= 0 },
                                speed = SPEED.find(line)?.groupValues?.get(1),
                                title = title,
                                line = line,
                                stage = stage,
                            ),
                        )
                    }
                }
                val output = response.out + "\n" + response.err
                val files = OutputScanner.scan(job.workDir)
                val alreadyDone = ALREADY.containsMatchIn(output)
                when {
                    files.isNotEmpty() || alreadyDone ->
                        EngineResult.Success(files, title ?: readTitle(metaFile), alreadyDownloaded = files.isEmpty())
                    else -> EngineResult.Failure(
                        ErrorClassifier.toError(output, type, job.cookieFile != null).let {
                            if (it.reason == FailureReason.UNKNOWN) it.copy(reason = FailureReason.NO_MEDIA) else it
                        },
                    )
                }
            } catch (e: YoutubeDL.CanceledException) {
                EngineResult.Failure(DownloadError(FailureReason.CANCELED, "Canceled", type))
            } catch (e: CancellationException) {
                YoutubeDL.getInstance().destroyProcessById(job.taskId)
                throw e
            } catch (e: InterruptedException) {
                YoutubeDL.getInstance().destroyProcessById(job.taskId)
                EngineResult.Failure(DownloadError(FailureReason.CANCELED, "Interrupted", type))
            } catch (e: YoutubeDLException) {
                val text = (e.message.orEmpty() + "\n" + log.text()).trim()
                EngineResult.Failure(ErrorClassifier.toError(text, type, job.cookieFile != null))
            } catch (e: Exception) {
                EngineResult.Failure(DownloadError(FailureReason.UNKNOWN, e.message ?: e.toString(), type))
            } finally {
                watcher.cancel()
            }
        }
    }

    override fun cancel(taskId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(taskId) }
    }

    private fun readTitle(file: File): String? = runCatching {
        file.takeIf { it.exists() }?.readLines()?.firstOrNull()?.substringBefore('\t')?.takeIf { it.isNotBlank() && it != "NA" }
    }.getOrNull()

    private companion object {
        val SPEED = Regex("""at\s+~?\s*([\d.]+\s*[KMGT]?i?B/s)""")
        val ALREADY = Regex("""has already been (recorded in the archive|downloaded)""")
    }
}

/** Keeps the last lines of engine output for error reporting. */
class OutputTail(private val max: Int = 40) {
    private val lines = ArrayDeque<String>()

    @Synchronized fun add(line: String) {
        if (line.isBlank()) return
        lines.addLast(line)
        while (lines.size > max) lines.removeFirst()
    }

    @Synchronized fun text(): String = lines.joinToString("\n")
}
