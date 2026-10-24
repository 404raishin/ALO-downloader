package com.alo.downloader.update

import android.content.Context
import com.alo.downloader.core.update.PyPiRelease
import com.alo.downloader.core.update.Versions
import com.alo.downloader.data.db.TaskDao
import com.alo.downloader.data.settings.SettingKeys
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.data.settings.UpdateChannel
import com.alo.downloader.engine.gallerydl.GalleryDlRuntime
import com.alo.downloader.engine.ytdlp.YtDlpRuntime
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class EngineVersions(
    val ytDlp: String?,
    val galleryDl: String?,
    /** Why an engine could not start, shown instead of a version. */
    val ytDlpError: String? = null,
    val galleryDlError: String? = null,
)

sealed interface UpdateResult {
    data class Updated(val version: String) : UpdateResult
    data class UpToDate(val version: String?) : UpdateResult
}

/**
 * Updates the download engines in place, so sites that change their pages keep working
 * without reinstalling ALO: yt-dlp via youtubedl-android's updater, gallery-dl by fetching
 * the newest wheel from PyPI (checksum verified) into the app's private storage.
 */
@Singleton
class EngineUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ytDlp: YtDlpRuntime,
    private val galleryDl: GalleryDlRuntime,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val taskDao: TaskDao,
) {
    /** Thrown when an update would replace an engine that a running download is using. */
    class EngineBusyException : IOException("Downloads are running. Pause or finish them before updating the engines.")

    suspend fun downloadsRunning(): Boolean = taskDao.runningCount() > 0

    private suspend fun requireIdle() {
        if (downloadsRunning()) throw EngineBusyException()
    }
    suspend fun versions(): EngineVersions = withContext(Dispatchers.IO) {
        ytDlp.ensureReady()
        val yt = ytDlp.version()
        val gdl = galleryDl.version()
        EngineVersions(
            ytDlp = yt,
            galleryDl = gdl,
            ytDlpError = if (yt == null) ytDlp.initError ?: "yt-dlp did not start" else null,
            galleryDlError = if (gdl == null) galleryDl.initError ?: "gallery-dl did not start" else null,
        )
    }

    suspend fun updateYtDlp(channel: UpdateChannel): UpdateResult = withContext(Dispatchers.IO) {
        // yt-dlp is a zip that a running process reads lazily; replacing it mid-download breaks it.
        requireIdle()
        if (!ytDlp.ensureReady()) throw IOException(ytDlp.initError ?: "yt-dlp is not available")
        val target = if (channel == UpdateChannel.NIGHTLY) YoutubeDL.UpdateChannel._NIGHTLY else YoutubeDL.UpdateChannel._STABLE
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, target)
        ytDlp.invalidateVersion()
        val version = ytDlp.version()
        if (status == YoutubeDL.UpdateStatus.DONE) UpdateResult.Updated(version ?: "?") else UpdateResult.UpToDate(version)
    }

    suspend fun updateGalleryDl(): UpdateResult = withContext(Dispatchers.IO) {
        requireIdle()
        val current = galleryDl.version()
        val release = http.newCall(Request.Builder().url(PYPI_URL).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("PyPI returned HTTP ${response.code}")
            PyPiRelease.parse(response.body?.string().orEmpty()) ?: throw IOException("No wheel found on PyPI")
        }
        if (!Versions.isNewer(release.version, current)) return@withContext UpdateResult.UpToDate(current)

        val dir = galleryDl.wheelDir
        val partial = File(dir, release.fileName + ".part")
        http.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty download")
            partial.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        release.sha256?.let { expected ->
            val actual = sha256(partial)
            if (!actual.equals(expected, ignoreCase = true)) {
                partial.delete()
                throw IOException("Checksum mismatch for ${release.fileName}")
            }
        }
        val wheel = File(dir, release.fileName)
        if (!partial.renameTo(wheel)) throw IOException("Cannot store ${release.fileName}")
        dir.listFiles()?.filter { it.name.endsWith(".whl") && it != wheel }?.forEach { it.delete() }
        val loaded = galleryDl.version()
        if (loaded != release.version) {
            // The wheel did not import; fall back to the bundled copy.
            wheel.delete()
            throw IOException("gallery-dl ${release.version} could not be loaded")
        }
        UpdateResult.Updated(release.version)
    }

    /** Removes in-app updates and returns to the gallery-dl version bundled with the APK. */
    fun resetGalleryDl() {
        galleryDl.wheelDir.listFiles()?.forEach { it.delete() }
    }

    /** Runs both updates, ignoring individual failures; used by the periodic worker. */
    suspend fun updateAll(): List<Result<UpdateResult>> {
        val channel = settings.current().updateChannel
        val results = listOf(runCatching { updateYtDlp(channel) }, runCatching { updateGalleryDl() })
        settings.set(SettingKeys.lastEngineUpdateCheck, System.currentTimeMillis())
        return results
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PYPI_URL = "https://pypi.org/pypi/gallery-dl/json"
    }
}
