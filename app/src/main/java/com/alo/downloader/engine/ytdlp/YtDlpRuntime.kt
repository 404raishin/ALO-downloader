package com.alo.downloader.engine.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    fun version(): String? = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()

    private companion object { const val TAG = "YtDlpRuntime" }
}
