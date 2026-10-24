package com.alo.downloader.core.engine

import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.SponsorBlockMode
import com.alo.downloader.core.model.VideoContainer
import com.alo.downloader.core.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YtDlpArgsTest {
    private val dir = File("/work/task1")

    private fun List<String>.valueOf(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

    @Test
    fun `default video download`() {
        val args = YtDlpArgs.build(DownloadOptions(), dir)
        assertEquals("bv*+ba/b", args.valueOf("-f"))
        assertTrue("--no-playlist" in args)
        assertTrue("--embed-thumbnail" in args)
        assertTrue("--embed-metadata" in args)
        assertFalse("-x" in args)
        assertEquals(dir.absolutePath, args.valueOf("-P"))
    }

    @Test
    fun `quality cap`() {
        val args = YtDlpArgs.build(DownloadOptions(videoQuality = VideoQuality.P720, container = VideoContainer.MP4), dir)
        assertEquals("bv*[height<=720]+ba/b[height<=720]/bv*+ba/b", args.valueOf("-f"))
        assertEquals("mp4", args.valueOf("--merge-output-format"))
        assertEquals("res,ext:mp4:m4a", args.valueOf("-S"))
    }

    @Test
    fun `audio only mp3 with square cover`() {
        val args = YtDlpArgs.build(DownloadOptions(audioOnly = true, audioFormat = AudioFormat.MP3), dir)
        assertTrue("-x" in args)
        assertEquals("mp3", args.valueOf("--audio-format"))
        assertEquals("ba/b", args.valueOf("-f"))
        assertEquals(YtDlpArgs.SQUARE_THUMBNAIL_PPA, args.valueOf("--ppa"))
        assertFalse("--embed-chapters" in args)
    }

    @Test
    fun `explicit format id wins`() {
        val args = YtDlpArgs.build(DownloadOptions(formatId = "137+140", videoQuality = VideoQuality.P360), dir)
        assertEquals("137+140", args.valueOf("-f"))
    }

    @Test
    fun `cookies archive proxy and rate limit`() {
        val args = YtDlpArgs.build(
            DownloadOptions(proxy = "socks5://127.0.0.1:1080", rateLimit = "2M"),
            dir, cookieFile = File("/c/cookies.txt"), archiveFile = File("/a/archive.txt"),
        )
        assertEquals(File("/c/cookies.txt").absolutePath, args.valueOf("--cookies"))
        assertEquals(File("/a/archive.txt").absolutePath, args.valueOf("--download-archive"))
        assertEquals("socks5://127.0.0.1:1080", args.valueOf("--proxy"))
        assertEquals("2M", args.valueOf("-r"))
    }

    @Test
    fun `archive disabled`() {
        val args = YtDlpArgs.build(DownloadOptions(useArchive = false), dir, archiveFile = File("/a"))
        assertFalse("--download-archive" in args)
    }

    @Test
    fun `subtitles and sponsorblock`() {
        val args = YtDlpArgs.build(
            DownloadOptions(subtitles = true, subtitleLanguages = "en", sponsorBlock = SponsorBlockMode.REMOVE), dir,
        )
        assertTrue("--write-subs" in args)
        assertTrue("--embed-subs" in args)
        assertEquals("en", args.valueOf("--sub-langs"))
        assertEquals("sponsor,selfpromo,interaction", args.valueOf("--sponsorblock-remove"))
    }

    @Test
    fun `playlist selection`() {
        val args = YtDlpArgs.build(DownloadOptions(downloadPlaylist = true, playlistItems = "1,3-4"), dir)
        assertTrue("--yes-playlist" in args)
        assertEquals("1,3-4", args.valueOf("--playlist-items"))
        assertTrue(args.valueOf("-o")!!.startsWith("%(playlist_title)"))
    }

    @Test
    fun `custom command replaces generated format options`() {
        val opts = DownloadOptions(customArgs = listOf("-f", "worst", "--write-description"), customReplacesDefaults = true)
        val args = YtDlpArgs.build(opts, dir, cookieFile = File("/c.txt"))
        assertEquals("worst", args.valueOf("-f"))
        assertFalse("--embed-thumbnail" in args)
        assertTrue("--cookies" in args)
        assertTrue("-o" in args)
    }

    @Test
    fun `gallery-dl template is ignored by yt-dlp`() {
        val opts = DownloadOptions(customArgs = listOf("--range", "1-3"), customArgsEngine = EngineType.GALLERY_DL)
        assertFalse("--range" in YtDlpArgs.build(opts, dir))
    }

    @Test
    fun `aria2c downloader`() {
        val args = YtDlpArgs.build(DownloadOptions(useAria2c = true, concurrentFragments = 8), dir)
        assertEquals("libaria2c.so", args.valueOf("--downloader"))
        assertFalse("-N" in args)
        // Without a CA path the library's own aria2c arguments (which carry one) must win.
        assertFalse("--external-downloader-args" in args)
    }

    @Test
    fun `aria2c arguments keep the CA certificate`() {
        val args = YtDlpArgs.build(DownloadOptions(useAria2c = true), dir, aria2cCaCertificate = "/data/py/usr/etc/tls/cert.pem")
        assertEquals("aria2c:--summary-interval=1 --ca-certificate=/data/py/usr/etc/tls/cert.pem", args.valueOf("--external-downloader-args"))
        val spaced = YtDlpArgs.build(DownloadOptions(useAria2c = true), dir, aria2cCaCertificate = "/data/my files/cert.pem")
        assertEquals("aria2c:--summary-interval=1 --ca-certificate='/data/my files/cert.pem'", spaced.valueOf("--external-downloader-args"))
    }

    @Test
    fun `fast youtube clients unless cookies or own extractor args`() {
        assertEquals(listOf("--extractor-args", YtDlpArgs.FAST_YOUTUBE_CLIENTS), YtDlpArgs.fastYoutubeArgs(DownloadOptions(), hasCookies = false))
        assertEquals(null, YtDlpArgs.fastYoutubeArgs(DownloadOptions(), hasCookies = true))
        val custom = DownloadOptions(customArgs = listOf("--extractor-args", "youtube:player_client=web"))
        assertEquals(null, YtDlpArgs.fastYoutubeArgs(custom, hasCookies = false))
    }
}
