package com.alo.downloader.core.engine

import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.EngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GalleryDlArgsTest {
    private fun List<String>.valueOf(flag: String) = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

    @Test
    fun `basic arguments end with url`() {
        val args = GalleryDlArgs.build(
            "https://www.instagram.com/p/x/",
            DownloadOptions(requestSleepSeconds = 1.5, proxy = "socks5://h:1"),
            File("/w"), File("/c.txt"), File("/a.sqlite3"),
        )
        assertEquals("https://www.instagram.com/p/x/", args.last())
        assertEquals(File("/w").absolutePath, args.valueOf("--directory"))
        assertEquals(File("/c.txt").absolutePath, args.valueOf("--cookies"))
        assertEquals(File("/a.sqlite3").absolutePath, args.valueOf("--download-archive"))
        assertEquals("1.5", args.valueOf("--sleep-request"))
        assertEquals("socks5://h:1", args.valueOf("--proxy"))
        assertTrue("output.mode=pipe" in args)
    }

    @Test
    fun `only gallery-dl custom args are used`() {
        val yt = GalleryDlArgs.build("u", DownloadOptions(customArgs = listOf("-x")), File("/w"))
        assertFalse("-x" in yt)
        val gdl = GalleryDlArgs.build("u", DownloadOptions(customArgs = listOf("--range", "1-2"), customArgsEngine = EngineType.GALLERY_DL), File("/w"))
        assertEquals("1-2", gdl.valueOf("--range"))
        assertEquals("1", GalleryDlArgs.build("u", DownloadOptions(requestSleepSeconds = 1.0), File("/w")).valueOf("--sleep-request"))
    }

    @Test
    fun `parses pipe output`() {
        assertEquals(GalleryDlOutput.Line.Downloaded("/w/a.jpg"), GalleryDlOutput.parse("out", "/w/a.jpg"))
        assertEquals(GalleryDlOutput.Line.Skipped("/w/b.jpg"), GalleryDlOutput.parse("out", "# /w/b.jpg"))
        assertEquals(GalleryDlOutput.Line.Log("[instagram][error] x"), GalleryDlOutput.parse("err", "[instagram][error] x"))
    }
}
