package com.alo.downloader.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaInfoParserTest {
    @Test
    fun `parses video with formats`() {
        val json = """
            WARNING: something
            {"id":"abc","title":"Hello","uploader":"Me","duration":61.5,"thumbnail":"https://i/t.jpg",
             "extractor_key":"Youtube","webpage_url":"https://youtu.be/abc",
             "subtitles":{"en":[]},"automatic_captions":{"zh-Hans":[],"en":[]},
             "formats":[
               {"format_id":"sb0","ext":"mhtml","vcodec":"none","acodec":"none"},
               {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","tbr":129.5,"filesize":1000},
               {"format_id":"137","ext":"mp4","vcodec":"avc1.640028","acodec":"none","height":1080,"width":1920,"fps":30,"tbr":4000},
               {"format_id":"299","ext":"mp4","vcodec":"avc1.64002a","acodec":"none","height":1080,"fps":60,"tbr":6000},
               {"format_id":"18","ext":"mp4","vcodec":"avc1","acodec":"mp4a","height":360,"filesize_approx":5000}
             ]}
        """.trimIndent()
        val info = MediaInfoParser.parse(json)!!
        assertEquals("Hello", info.title)
        assertEquals(61L, info.durationSeconds)
        assertEquals(4, info.formats.size)
        assertEquals(listOf("299", "137", "18"), info.videoFormats.map { it.id })
        assertEquals(listOf("140"), info.audioFormats.map { it.id })
        assertEquals("1080p60 · mp4 · avc1", info.formats.first { it.id == "299" }.label())
        assertEquals(5000L, info.formats.first { it.id == "18" }.sizeBytes)
        assertEquals(listOf("en"), info.subtitleLanguages)
        assertFalse(info.isPlaylist)
    }

    @Test
    fun `parses flat playlist`() {
        val json = """{"_type":"playlist","title":"PL","entries":[{"id":"a","title":"A","url":"https://y/a"},{"id":"b","title":null,"url":"https://y/b","duration":10}]}"""
        val info = MediaInfoParser.parse(json)!!
        assertTrue(info.isPlaylist)
        assertEquals(2, info.entries.size)
        assertEquals(2, info.entries[1].index)
        assertNull(info.entries[1].title)
        assertEquals(10L, info.entries[1].durationSeconds)
    }

    @Test
    fun `garbage returns null`() {
        assertNull(MediaInfoParser.parse("ERROR: nope"))
    }
}
