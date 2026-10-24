package com.alo.downloader.core.router

import com.alo.downloader.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformResolverTest {
    @Test
    fun `resolves common platforms`() {
        assertEquals(Platform.YOUTUBE, PlatformResolver.resolve("https://youtu.be/abc"))
        assertEquals(Platform.YOUTUBE, PlatformResolver.resolve("https://m.youtube.com/watch?v=abc"))
        assertEquals(Platform.INSTAGRAM, PlatformResolver.resolve("https://www.instagram.com/p/abc/"))
        assertEquals(Platform.X, PlatformResolver.resolve("https://twitter.com/u/status/1"))
        assertEquals(Platform.X, PlatformResolver.resolve("https://x.com/u/status/1"))
        assertEquals(Platform.TELEGRAM, PlatformResolver.resolve("https://t.me/durov/1"))
        assertEquals(Platform.GENERIC, PlatformResolver.resolve("https://example.org/video"))
    }

    @Test
    fun `does not match lookalike domains`() {
        assertEquals(Platform.GENERIC, PlatformResolver.resolve("https://notyoutube.com/watch"))
        assertEquals(Platform.GENERIC, PlatformResolver.resolve("https://xx.com/a"))
    }

    @Test
    fun `cookie domain`() {
        assertEquals(Platform.INSTAGRAM, PlatformResolver.fromCookieDomain(".instagram.com"))
        assertEquals(Platform.YOUTUBE, PlatformResolver.fromCookieDomain(".youtube.com"))
    }
}
