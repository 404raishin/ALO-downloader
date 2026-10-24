package com.alo.downloader.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkExtractorTest {
    @Test
    fun `extracts link from shared text`() {
        val text = "Check out this video! https://youtu.be/dQw4w9WgXcQ?si=abc via YouTube."
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc", LinkExtractor.extractFirst(text))
    }

    @Test
    fun `chinese punctuation terminates link`() {
        val text = "看看这个https://www.bilibili.com/video/BV1xx411c7mD，很好看"
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", LinkExtractor.extractFirst(text))
    }

    @Test
    fun `multiple links and www prefix`() {
        val text = "a https://x.com/u/status/1 b www.instagram.com/p/abc/ c https://x.com/u/status/1"
        assertEquals(
            listOf("https://x.com/u/status/1", "https://www.instagram.com/p/abc/"),
            LinkExtractor.extractAll(text),
        )
    }

    @Test
    fun `accepts known platforms without scheme`() {
        assertEquals(listOf("https://youtu.be/dQw4w9WgXcQ"), LinkExtractor.extractAll("youtu.be/dQw4w9WgXcQ"))
        assertEquals(
            listOf("https://m.youtube.com/watch?v=x", "https://instagram.com/p/abc/"),
            LinkExtractor.extractAll("see m.youtube.com/watch?v=x and instagram.com/p/abc/"),
        )
        // Not doubled when the scheme is present, and unknown bare domains are left alone.
        assertEquals(listOf("https://x.com/u/status/1"), LinkExtractor.extractAll("https://x.com/u/status/1"))
        assertEquals(emptyList<String>(), LinkExtractor.extractAll("example.org/page and me@x.com/a"))
    }

    @Test
    fun `rejects non links`() {
        assertNull(LinkExtractor.extractFirst("hello world"))
        assertNull(LinkExtractor.extractFirst(null))
    }

    @Test
    fun `host without www`() {
        assertEquals("instagram.com", LinkExtractor.hostOf("https://www.instagram.com/p/x"))
    }
}
