package com.alo.downloader

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alo.downloader.core.engine.DownloadEngine
import com.alo.downloader.core.engine.EngineResult
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.Platform
import com.alo.downloader.engine.gallerydl.GalleryDlEngine
import com.alo.downloader.engine.gallerydl.GalleryDlRuntime
import com.alo.downloader.engine.ytdlp.YtDlpEngine
import com.alo.downloader.engine.ytdlp.YtDlpRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Downloads from real sites and logs timings under the `ALO-LIVE` tag. Skipped unless the
 * runner gets `liveSites=true` (the "live_sites" input of the Build workflow), because site
 * behaviour and CI IP blocks make these unsuitable as a merge gate.
 */
@RunWith(AndroidJUnit4::class)
class LiveSiteTest {
    private val ytRuntime = YtDlpRuntime(Fixtures.context)
    private val ytDlp = YtDlpEngine(ytRuntime)
    private val galleryDl = GalleryDlEngine(GalleryDlRuntime(Fixtures.context))

    @Before
    fun onlyWhenAsked() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSites") == "true")
    }

    private fun download(name: String, engine: DownloadEngine, url: String, platform: Platform, options: DownloadOptions = DownloadOptions(useArchive = false)) = runBlocking {
        val lines = mutableListOf<String>()
        val start = System.currentTimeMillis()
        val result = engine.download(Fixtures.job(url, options, platform)) { p ->
            p.line?.let {
                lines += "%6.1fs %s".format((System.currentTimeMillis() - start) / 1000.0, it.trim())
            }
        }
        val seconds = (System.currentTimeMillis() - start) / 1000.0
        val summary = when (result) {
            is EngineResult.Success -> "OK ${result.files.joinToString { "${it.name} (${it.length() / 1024} KiB)" }}"
            is EngineResult.Failure -> "FAILED ${result.error.reason}: ${result.error.message}"
        }
        Log.i(TAG, "===== $name: %.1fs $summary".format(seconds))
        lines.filterNot { "[download]" in it && "%" in it && "100%" !in it }.takeLast(60).forEach { Log.i(TAG, "  $it") }
        val log = "$name: $summary\n" + lines.takeLast(30).joinToString("\n")
        assertTrue(log, result is EngineResult.Success)
        // A success that needed the engine's second attempt still means the first path is broken.
        assertTrue(log, lines.none { YtDlpEngine.RETRY_DEFAULT_CLIENTS in it || YtDlpEngine.RETRY_WITHOUT_ARIA2C in it })
    }

    @Test
    fun youtubeShortFastClients() = download("YouTube Short (fast clients)", ytDlp, YOUTUBE_SHORT, Platform.YOUTUBE)

    /** The default clients need the JS challenge solved in QuickJS; run twice to see the cache help. */
    @Test
    fun youtubeShortDefaultClientsColdThenWarm() {
        ytRuntime.cacheDir.deleteRecursively()
        download("YouTube Short (default clients, cold cache)", ytDlp, YOUTUBE_SHORT, Platform.GENERIC)
        download("YouTube Short (default clients, warm cache)", ytDlp, YOUTUBE_SHORT, Platform.GENERIC)
    }

    @Test
    fun instagramReelYtDlp() = download("Instagram reel (yt-dlp)", ytDlp, INSTAGRAM_REEL, Platform.INSTAGRAM)

    @Test
    fun instagramReelYtDlpAria2c() =
        download("Instagram reel (yt-dlp + aria2c)", ytDlp, INSTAGRAM_REEL, Platform.INSTAGRAM, DownloadOptions(useArchive = false, useAria2c = true))

    @Test
    fun instagramReelGalleryDl() = download("Instagram reel (gallery-dl)", galleryDl, INSTAGRAM_REEL, Platform.INSTAGRAM)

    @Test
    fun instagramPostGalleryDl() = download("Instagram post (gallery-dl)", galleryDl, INSTAGRAM_POST, Platform.INSTAGRAM)

    private companion object {
        const val TAG = "ALO-LIVE"
        const val YOUTUBE_SHORT = "https://youtube.com/shorts/aDONt3ylEos"
        const val INSTAGRAM_REEL = "https://www.instagram.com/reel/De3BWMGKafR/"
        const val INSTAGRAM_POST = "https://www.instagram.com/p/De4SwDfgRnv/"
    }
}
