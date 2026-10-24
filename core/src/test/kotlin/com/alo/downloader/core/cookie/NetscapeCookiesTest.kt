package com.alo.downloader.core.cookie

import com.alo.downloader.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetscapeCookiesTest {
    private val sample = """
        # Netscape HTTP Cookie File
        # comment
        .instagram.com	TRUE	/	TRUE	1999999999	sessionid	abc%3A123
        #HttpOnly_.youtube.com	TRUE	/	TRUE	1999999999	__Secure-3PSID	xyz
        .example.org	TRUE	/	FALSE	0	foo	bar
        broken line
    """.trimIndent()

    @Test
    fun `parses entries including HttpOnly prefix`() {
        val cookies = NetscapeCookies.parse(sample)
        assertEquals(3, cookies.size)
        val yt = cookies[1]
        assertTrue(yt.httpOnly)
        assertEquals(".youtube.com", yt.domain)
        assertEquals("__Secure-3PSID", yt.name)
        assertEquals(0L, cookies[2].expiresAt)
    }

    @Test
    fun `round trip`() {
        val cookies = NetscapeCookies.parse(sample)
        assertEquals(cookies, NetscapeCookies.parse(NetscapeCookies.serialize(cookies)))
        assertTrue(NetscapeCookies.serialize(cookies).startsWith(NetscapeCookies.HEADER))
    }

    @Test
    fun `groups by platform`() {
        val groups = NetscapeCookies.groupByPlatform(NetscapeCookies.parse(sample))
        assertEquals(1, groups[Platform.INSTAGRAM]?.size)
        assertEquals(1, groups[Platform.YOUTUBE]?.size)
        assertEquals(1, groups[Platform.GENERIC]?.size)
    }

    @Test
    fun `converts webview cookie header`() {
        val cookies = NetscapeCookies.fromHeader("sessionid=abc; csrftoken=def=ghi; =bad", "www.instagram.com", nowSeconds = 1000)
        assertEquals(listOf("sessionid", "csrftoken"), cookies.map { it.name })
        assertEquals("def=ghi", cookies[1].value)
        assertEquals(".instagram.com", cookies[0].domain)
        assertEquals(1000 + NetscapeCookies.DEFAULT_LIFETIME, cookies[0].expiresAt)
    }

    @Test
    fun `auth cookie detection and expiry`() {
        val now = 2_000_000_000L
        val live = CookieEntry(".instagram.com", true, "/", true, now + 100, "sessionid", "x")
        val dead = live.copy(expiresAt = now - 100)
        assertTrue(NetscapeCookies.hasAuthCookie(Platform.INSTAGRAM, listOf(live), now))
        assertFalse(NetscapeCookies.hasAuthCookie(Platform.INSTAGRAM, listOf(dead), now))
        assertFalse(NetscapeCookies.hasAuthCookie(Platform.INSTAGRAM, emptyList(), now))
        assertTrue(dead.isExpired(now))
        assertFalse(live.copy(expiresAt = 0).isExpired(now))
    }

    @Test
    fun `merge replaces same cookie`() {
        val a = CookieEntry(".x.com", true, "/", true, 1, "auth_token", "old")
        val b = a.copy(domain = "x.com", value = "new")
        val c = a.copy(name = "ct0")
        val merged = NetscapeCookies.merge(listOf(a, c), listOf(b))
        assertEquals(2, merged.size)
        assertEquals("new", merged.first { it.name == "auth_token" }.value)
    }
}
