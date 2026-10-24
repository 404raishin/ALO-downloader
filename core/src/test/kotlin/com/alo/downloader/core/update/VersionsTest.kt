package com.alo.downloader.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun `numeric comparison`() {
        assertTrue(Versions.compare("1.32.17", "1.32.2") > 0)
        assertTrue(Versions.compare("2025.10.22", "2025.9.30") > 0)
        assertEquals(0, Versions.compare("1.2", "1.2.0"))
        assertTrue(Versions.compare("1.3.0.dev0", "1.3.0") < 0)
        assertTrue(Versions.isNewer("1.33.0", "1.32.17"))
        assertFalse(Versions.isNewer("1.32.17", "1.32.17"))
        assertTrue(Versions.isNewer("1.0", null))
    }

    @Test
    fun `picks the universal wheel from pypi json`() {
        val text = """
            {"info":{"version":"1.40.0"},"urls":[
              {"packagetype":"sdist","filename":"gallery_dl-1.40.0.tar.gz","url":"https://f/sdist"},
              {"packagetype":"bdist_wheel","filename":"gallery_dl-1.40.0-py3-none-any.whl","url":"https://f/whl","digests":{"sha256":"abc"}}
            ]}
        """.trimIndent()
        assertEquals(WheelRelease("1.40.0", "https://f/whl", "abc", "gallery_dl-1.40.0-py3-none-any.whl"), PyPiRelease.parse(text))
        assertNull(PyPiRelease.parse("""{"info":{"version":"1"},"urls":[]}"""))
        assertNull(PyPiRelease.parse("oops"))
    }
}
