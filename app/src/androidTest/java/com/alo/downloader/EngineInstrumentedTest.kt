package com.alo.downloader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alo.downloader.core.engine.EngineResult
import com.alo.downloader.core.engine.FallbackRunner
import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.engine.gallerydl.GalleryDlEngine
import com.alo.downloader.engine.gallerydl.GalleryDlRuntime
import com.alo.downloader.engine.ytdlp.YtDlpEngine
import com.alo.downloader.engine.ytdlp.YtDlpRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real engines (bundled Python, yt-dlp, ffmpeg, gallery-dl) on the device. */
@RunWith(AndroidJUnit4::class)
class EngineInstrumentedTest {
    private val ytRuntime = YtDlpRuntime(Fixtures.context)
    private val ytDlp = YtDlpEngine(ytRuntime)
    private val gdlRuntime = GalleryDlRuntime(Fixtures.context)
    private val galleryDl = GalleryDlEngine(gdlRuntime)

    private fun EngineResult.success(): EngineResult.Success = when (this) {
        is EngineResult.Success -> this
        is EngineResult.Failure -> throw AssertionError("expected success, got $error")
    }

    @Test
    fun ytDlpRuntimeStarts() = runBlocking {
        assertTrue("init failed: ${ytRuntime.initError}", ytRuntime.ensureReady())
        assertNotNull(ytRuntime.version())
    }

    @Test
    fun ytDlpDownloadsDirectVideo() = runBlocking {
        val job = Fixtures.job(Fixtures.clipUrl)
        val result = ytDlp.download(job) { }.success()
        assertEquals(1, result.files.size)
        assertTrue(result.files.first().length() > 1000)
    }

    @Test
    fun ytDlpExtractsAudioWithFfmpeg() = runBlocking {
        val options = Fixtures.quietOptions().copy(audioOnly = true, audioFormat = AudioFormat.MP3)
        val result = ytDlp.download(Fixtures.job(Fixtures.clipUrl, options)) { }.success()
        assertTrue(result.files.joinToString { it.name }, result.files.any { it.name.endsWith(".mp3") })
    }

    @Test
    fun galleryDlStartsAndReportsVersion() {
        assertNotNull("python failed: ${gdlRuntime.initError}", gdlRuntime.version())
    }

    @Test
    fun galleryDlDownloadsDirectImage() = runBlocking {
        val result = galleryDl.download(Fixtures.job(Fixtures.imageUrl)) { }.success()
        assertEquals(1, result.files.size)
        assertTrue(result.files.first().name.endsWith(".png"))
    }

    @Test
    fun galleryDlReportsUnsupportedUrl() = runBlocking {
        val result = galleryDl.download(Fixtures.job("https://example.com/")) { }
        assertTrue(result is EngineResult.Failure)
        assertEquals(FailureReason.UNSUPPORTED_URL, (result as EngineResult.Failure).error.reason)
    }

    @Test
    fun fallbackReachesSecondEngine() = runBlocking {
        // A gallery-dl-only template with an invalid flag makes the first engine fail;
        // yt-dlp ignores gallery-dl arguments and must take over.
        val options = Fixtures.quietOptions().copy(
            customArgs = listOf("--alo-invalid-flag"),
            customArgsEngine = EngineType.GALLERY_DL,
        )
        val outcome = FallbackRunner.run(
            engines = listOf(galleryDl, ytDlp),
            jobFor = { Fixtures.job(Fixtures.clipUrl, options) },
            listener = { },
        )
        assertTrue(outcome.attempts.toString(), outcome.result is EngineResult.Success)
        assertEquals(listOf("gallery-dl ✗", "yt-dlp ✓"), outcome.attempts)
    }
}
