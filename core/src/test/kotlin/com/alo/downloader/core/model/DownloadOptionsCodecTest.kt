package com.alo.downloader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadOptionsCodecTest {
    @Test
    fun `round trip keeps every field`() {
        val options = DownloadOptions(
            audioOnly = true,
            audioFormat = AudioFormat.OPUS,
            videoQuality = VideoQuality.P1080,
            container = VideoContainer.MKV,
            formatId = "251",
            subtitles = true,
            playlistItems = "1-3",
            sponsorBlock = SponsorBlockMode.MARK,
            proxy = "http://p:8080",
            rateLimit = "500K",
            concurrentFragments = 4,
            requestSleepSeconds = 1.5,
            customArgs = listOf("--embed-chapters", "-o", "%(id)s.%(ext)s"),
            customReplacesDefaults = true,
        )
        assertEquals(options, DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(options)))
    }

    @Test
    fun `garbage and missing keys fall back to defaults`() {
        assertEquals(DownloadOptions(), DownloadOptionsCodec.decode("not json"))
        assertEquals(DownloadOptions(), DownloadOptionsCodec.decode(null))
        assertEquals(DownloadOptions(audioOnly = true), DownloadOptionsCodec.decode("""{"audioOnly":true,"videoQuality":"P9999","x":1}"""))
    }
}
