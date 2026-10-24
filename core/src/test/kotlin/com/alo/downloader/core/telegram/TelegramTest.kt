package com.alo.downloader.core.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramTest {
    @Test
    fun `parses link shapes`() {
        assertEquals(TelegramLink.PublicPost("durov", 42, false), TelegramLink.parse("https://t.me/durov/42"))
        assertEquals(TelegramLink.PublicPost("durov", 42, true), TelegramLink.parse("https://t.me/s/durov/42?single"))
        assertEquals(TelegramLink.PublicPost("some_group", 99, false), TelegramLink.parse("https://telegram.me/some_group/7/99"))
        assertEquals(TelegramLink.PrivatePost(-1001234567890, 55), TelegramLink.parse("https://t.me/c/1234567890/55"))
        assertEquals(TelegramLink.PrivatePost(-1001234567890, 55), TelegramLink.parse("https://t.me/c/1234567890/3/55"))
        assertEquals(TelegramLink.Channel("durov"), TelegramLink.parse("https://t.me/durov"))
        assertEquals(TelegramLink.Invite("AbCdEf"), TelegramLink.parse("https://t.me/+AbCdEf"))
        assertEquals(TelegramLink.Invite("xyz"), TelegramLink.parse("https://t.me/joinchat/xyz"))
        assertNull(TelegramLink.parse("https://t.me/addstickers/pack"))
        assertNull(TelegramLink.parse("https://example.com/durov/1"))
    }

    @Test
    fun `extracts photos and videos from embed page`() {
        val html = """
            <meta property="og:title" content="Channel &amp; Co">
            <meta property="og:description" content="Look at this">
            <div class="tgme_widget_message_user"><i class="tgme_widget_message_user_photo" style="background-image:url('https://cdn/avatar.jpg')"></i></div>
            <a class="tgme_widget_message_photo_wrap grouped" href="https://t.me/c/1?single" style="width:100px;background-image:url('https://cdn4.telegram-cdn.org/file/a.jpg?x=1&amp;y=2')"></a>
            <a class="tgme_widget_message_video_player" href="#"><video src="https://cdn4.telegram-cdn.org/file/b.mp4?token=1" class="tgme_widget_message_video js-message_video" width="100%"></video></a>
            <a class="link_preview_right_image" style="background-image:url('https://cdn/preview.jpg')"></a>
        """.trimIndent()
        val embed = TelegramEmbedParser.parse(html)
        assertEquals(
            listOf("https://cdn4.telegram-cdn.org/file/a.jpg?x=1&y=2", "https://cdn4.telegram-cdn.org/file/b.mp4?token=1"),
            embed.mediaUrls,
        )
        assertEquals("Channel & Co", embed.channelTitle)
        assertEquals("Look at this", embed.text)
        assertFalse(embed.needsApp)
    }

    @Test
    fun `detects media that needs the app`() {
        val html = """<a class="tgme_widget_message_video_player not_supported"><div class="message_media_not_supported_label">Media is too big</div></a>"""
        val embed = TelegramEmbedParser.parse(html)
        assertTrue(embed.mediaUrls.isEmpty())
        assertTrue(embed.needsApp)
    }

    @Test
    fun `embed url`() {
        assertEquals("https://t.me/durov/42?embed=1&mode=tme", TelegramEmbedParser.embedUrl(TelegramLink.PublicPost("durov", 42, false)))
    }
}
