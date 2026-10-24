package com.alo.downloader.core.engine

import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.Platform
import java.io.File

/** One attempt of one engine at one task. */
data class EngineJob(
    val taskId: String,
    val url: String,
    val platform: Platform,
    /** Empty, task-private directory the engine writes into. */
    val workDir: File,
    val options: DownloadOptions,
    /** Netscape cookies.txt for the platform, if the user has stored cookies. */
    val cookieFile: File? = null,
    /** Engine specific archive file used for de-duplication. */
    val archiveFile: File? = null,
)

data class EngineProgress(
    /** 0..100, or null when the engine cannot tell. */
    val percent: Float? = null,
    val etaSeconds: Long? = null,
    val speed: String? = null,
    /** Number of files finished so far (gallery-dl, Telegram albums). */
    val filesDone: Int = 0,
    val title: String? = null,
    val line: String? = null,
    val stage: EngineStage? = null,
)

fun interface ProgressListener {
    fun onProgress(progress: EngineProgress)
}

sealed interface EngineResult {
    data class Success(
        val files: List<File>,
        val title: String? = null,
        /** True when the archive said everything was already downloaded. */
        val alreadyDownloaded: Boolean = false,
    ) : EngineResult

    data class Failure(val error: DownloadError) : EngineResult
}

interface DownloadEngine {
    val type: EngineType

    /** Whether the engine can be used at all on this device/build. */
    suspend fun isAvailable(): Boolean = true

    suspend fun download(job: EngineJob, listener: ProgressListener): EngineResult

    /** Stops the running attempt for [taskId]; partial files are kept for resuming. */
    fun cancel(taskId: String)
}
