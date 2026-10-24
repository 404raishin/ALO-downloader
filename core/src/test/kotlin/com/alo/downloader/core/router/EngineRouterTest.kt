package com.alo.downloader.core.router

import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.EngineType.GALLERY_DL
import com.alo.downloader.core.model.EngineType.TELEGRAM
import com.alo.downloader.core.model.EngineType.YT_DLP
import com.alo.downloader.core.model.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRouterTest {
    private fun r(url: String) = EngineRouter.route(url)

    @Test
    fun `videos go to yt-dlp first`() {
        assertEquals(listOf(YT_DLP, GALLERY_DL), r("https://www.youtube.com/watch?v=x"))
        assertEquals(listOf(YT_DLP, GALLERY_DL), r("https://www.instagram.com/reel/abc/"))
        assertEquals(listOf(YT_DLP, GALLERY_DL), r("https://x.com/user/status/123"))
        assertEquals(listOf(YT_DLP, GALLERY_DL), r("https://www.tiktok.com/@u/video/1"))
    }

    @Test
    fun `galleries go to gallery-dl first`() {
        assertEquals(listOf(GALLERY_DL, YT_DLP), r("https://www.instagram.com/p/abc/"))
        assertEquals(listOf(GALLERY_DL, YT_DLP), r("https://www.instagram.com/someone/"))
        assertEquals(listOf(GALLERY_DL, YT_DLP), r("https://x.com/someone/media"))
        assertEquals(listOf(GALLERY_DL, YT_DLP), r("https://www.pinterest.com/pin/123/"))
        assertEquals(listOf(GALLERY_DL, YT_DLP), r("https://www.reddit.com/gallery/abc"))
        assertEquals(listOf(GALLERY_DL, YT_DLP), r("https://www.tiktok.com/@u/photo/1"))
        assertEquals(listOf(GALLERY_DL, YT_DLP), r("https://example.com/pic.JPG"))
    }

    @Test
    fun `telegram first for t me`() {
        assertEquals(listOf(TELEGRAM, YT_DLP), r("https://t.me/channel/42"))
    }

    @Test
    fun `forced engine leads and keeps fallbacks`() {
        assertEquals(listOf(GALLERY_DL, YT_DLP), EngineRouter.route("https://youtu.be/x", forced = GALLERY_DL))
        assertEquals(listOf(GALLERY_DL), EngineRouter.route("https://youtu.be/x", forced = GALLERY_DL, allowFallback = false))
    }

    @Test
    fun `unavailable engines are skipped`() {
        val available = setOf(YT_DLP, GALLERY_DL)
        assertEquals(listOf(YT_DLP), EngineRouter.route("https://t.me/c/1", available = available))
        assertEquals(listOf(YT_DLP), EngineRouter.route("https://www.instagram.com/p/x/", available = setOf(EngineType.YT_DLP)))
    }

    @Test
    fun `fallback decisions`() {
        assertFalse(EngineRouter.shouldFallback(FailureReason.CANCELED))
        assertFalse(EngineRouter.shouldFallback(FailureReason.STORAGE))
        assertTrue(EngineRouter.shouldFallback(FailureReason.NO_MEDIA))
        assertTrue(EngineRouter.shouldFallback(FailureReason.LOGIN_REQUIRED))
    }
}
