package com.alo.downloader.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandLineTest {
    @Test
    fun `splits quotes and escapes`() {
        val args = CommandLine.split("""-f "bv*+ba/b" -o '%(title)s [%(id)s].%(ext)s' --ppa Merger:-c\ copy""")
        assertEquals(listOf("-f", "bv*+ba/b", "-o", "%(title)s [%(id)s].%(ext)s", "--ppa", "Merger:-c copy"), args)
    }

    @Test
    fun `drops program name url placeholder and comments`() {
        val args = CommandLine.split("# my template\nyt-dlp --embed-chapters\n  -x {url}")
        assertEquals(listOf("--embed-chapters", "-x"), args)
    }

    @Test(expected = CommandLine.ParseException::class)
    fun `unterminated quote fails`() {
        CommandLine.split("-o \"abc")
    }

    @Test
    fun `join round trips`() {
        val args = listOf("-o", "a b's.%(ext)s", "--flag", "")
        assertEquals(args, CommandLine.split(CommandLine.join(args)))
    }
}
