package com.alo.downloader.core.engine

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OutputScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `ignores partial and hidden files`() {
        val root = tmp.root
        File(root, "video.mp4").writeText("x")
        File(root, "video.f137.mp4.part").writeText("x")
        File(root, "a.ytdl").writeText("x")
        File(root, "empty.mp4").createNewFile()
        File(root, ".tmp").mkdirs()
        File(root, ".tmp/frag.mp4").writeText("x")
        File(root, "sub").mkdirs()
        File(root, "sub/pic.jpg").writeText("x")
        File(root, "info.json").writeText("{}")
        val names = OutputScanner.scan(root).map { it.name }.sorted()
        assertEquals(listOf("pic.jpg", "video.mp4"), names)
    }
}
