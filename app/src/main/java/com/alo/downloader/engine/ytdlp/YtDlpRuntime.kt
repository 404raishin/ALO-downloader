package com.alo.downloader.engine.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Lazily unpacks the bundled Python/yt-dlp/ffmpeg/aria2c runtime (slow on first launch). */
@Singleton
class YtDlpRuntime @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()

    @Volatile var isReady = false
        private set

    @Volatile var initError: String? = null
        private set

    suspend fun ensureReady(): Boolean {
        if (isReady) return true
        return mutex.withLock {
            if (isReady) return@withLock true
            withContext(Dispatchers.IO) {
                runCatching {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                    Aria2c.getInstance().init(context)
                }.onFailure {
                    Log.e(TAG, "yt-dlp init failed", it)
                    initError = it.message ?: it.toString()
                }.isSuccess
            }.also { isReady = it }
        }
    }

    /**
     * yt-dlp's cache. youtubedl-android passes `--no-cache-dir` unless a cache dir is set, which
     * makes yt-dlp parse YouTube's whole player script in QuickJS on every download (minutes on a
     * phone) instead of reusing the preprocessed player.
     */
    val cacheDir: File get() = File(context.cacheDir, "yt-dlp")

    /**
     * CA bundle the library hands to aria2c (inside its unpacked Python). It is a private field,
     * read by reflection after init; null means aria2c arguments are left to the library.
     */
    val caCertificate: String?
        get() = if (!isReady) null else runCatching {
            YoutubeDL::class.java.getDeclaredField("ENV_SSL_CERT_FILE").apply { isAccessible = true }.get(null) as String?
        }.getOrNull()?.takeIf { File(it).exists() }

    @Volatile private var reportedVersion: String? = null

    /**
     * Version of the yt-dlp in use. youtubedl-android only records it after an in-app update,
     * so for the bundled copy ask yt-dlp itself once and cache the answer.
     */
    fun version(): String? {
        runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        reportedVersion?.let { return it }
        if (!isReady) return null
        return runCatching {
            YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList<String>()).addOption("--version")).out.trim()
        }.getOrNull()?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }?.also { reportedVersion = it }
    }

    /** Forget the cached version after the binary was replaced. */
    fun invalidateVersion() {
        reportedVersion = null
    }

    private companion object { const val TAG = "YtDlpRuntime" }
}

/** Must go through addOption: the library only skips its `--no-cache-dir` for options, not commands. */
fun YoutubeDLRequest.withCacheDir(runtime: YtDlpRuntime): YoutubeDLRequest =
    addOption("--cache-dir", runtime.cacheDir.apply { mkdirs() }.absolutePath)
