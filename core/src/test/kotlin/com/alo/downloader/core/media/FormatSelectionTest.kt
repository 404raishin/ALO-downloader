package com.alo.downloader.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatSelectionTest {
    private fun f(id: String, v: String?, a: String?, h: Int? = null) = MediaFormat(id, "mp4", null, null, h, null, v, a, null, null)

    @Test
    fun `combines video only with audio`() {
        assertEquals("137+140", FormatSelection.combine(f("137", "avc1", "none", 1080), f("140", "none", "mp4a"), false))
        assertEquals("137+ba/137", FormatSelection.combine(f("137", "avc1", "none"), null, false))
        assertEquals("18", FormatSelection.combine(f("18", "avc1", "mp4a"), f("140", "none", "mp4a"), false))
        assertEquals("140", FormatSelection.combine(f("137", "avc1", "none"), f("140", "none", "mp4a"), true))
        assertNull(FormatSelection.combine(null, null, false))
        assertEquals("bv*+251/b", FormatSelection.combine(null, f("251", "none", "opus"), false))
    }

    @Test
    fun `playlist items compress and parse`() {
        assertEquals("1-3,5,7-8", PlaylistItems.compress(listOf(8, 1, 2, 3, 5, 7, 2)))
        assertEquals("", PlaylistItems.compress(emptyList()))
        assertEquals("4", PlaylistItems.compress(listOf(4)))
        assertEquals(setOf(1, 2, 3, 5, 7, 8), PlaylistItems.parse("1-3, 5,7-8,x,9-2"))
    }
}
