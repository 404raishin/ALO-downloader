package com.alo.downloader.engine.ytdlp

import android.content.Context
import com.alo.downloader.core.engine.ErrorClassifier
import com.alo.downloader.core.engine.YtDlpArgs
import com.alo.downloader.core.media.MediaInfo
import com.alo.downloader.core.media.MediaInfoParser
import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.core.router.PlatformResolver
import com.alo.downloader.data.cookie.CookieRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class MediaInfoException(val error: DownloadError) : Exception(error.message)

/** Asks yt-dlp for title, formats, subtitles and playlist entries without downloading. */
@Singleton
class MediaInfoFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: YtDlpRuntime,
    private val cookies: CookieRepository,
) {
    suspend fun fetch(url: String, options: DownloadOptions): Result<MediaInfo> = withContext(Dispatchers.IO) {
        if (!runtime.ensureReady()) {
            return@withContext Result.failure(
                MediaInfoException(DownloadError(FailureReason.ENGINE_UNAVAILABLE, runtime.initError.orEmpty(), EngineType.YT_DLP)),
            )
        }
        val id = "info-" + UUID.randomUUID()
        val dir = File(context.cacheDir, id)
        val platform = PlatformResolver.resolve(url)
        val cookieFile = cookies.writeCookieFile(platform, dir)
        try {
            val request = YoutubeDLRequest(url).addCommands(YtDlpArgs.infoArgs(options, cookieFile)).withCacheDir(runtime)
            val response = runInterruptible { YoutubeDL.getInstance().execute(request, id, false, null) }
            MediaInfoParser.parse(response.out)?.let { Result.success(it) }
                ?: Result.failure(MediaInfoException(ErrorClassifier.toError(response.err, EngineType.YT_DLP, cookieFile != null)))
        } catch (e: CancellationException) {
            YoutubeDL.getInstance().destroyProcessById(id)
            throw e
        } catch (e: YoutubeDLException) {
            Result.failure(MediaInfoException(ErrorClassifier.toError(e.message.orEmpty(), EngineType.YT_DLP, cookieFile != null)))
        } catch (e: Exception) {
            Result.failure(MediaInfoException(DownloadError(FailureReason.UNKNOWN, e.message ?: e.toString(), EngineType.YT_DLP)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
