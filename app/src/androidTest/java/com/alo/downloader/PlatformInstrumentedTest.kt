package com.alo.downloader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alo.downloader.core.cookie.CookieEntry
import com.alo.downloader.core.model.Platform
import com.alo.downloader.data.cookie.CookieSource
import com.alo.downloader.data.cookie.CookieVault
import com.alo.downloader.download.OutputStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlatformInstrumentedTest {
    private val context = Fixtures.context

    @Test
    fun publishesIntoPlatformFolder() = runBlocking {
        val storage = OutputStorage(context)
        val work = File(context.cacheDir, "publish-test").apply { deleteRecursively(); mkdirs() }
        val file = File(work, "alo-instrumented-${System.nanoTime()}.txt").apply { writeText("hello") }
        val stored = storage.publish(listOf(file), Platform.INSTAGRAM, perPlatform = true, treeUri = null, workDir = work).single()
        try {
            assertTrue(stored.location, stored.location.endsWith("ALO/Instagram"))
            assertEquals(5L, stored.sizeBytes)
            assertFalse("work file should be moved", file.exists())
            val text = context.contentResolver.openInputStream(stored.uri)!!.bufferedReader().use { it.readText() }
            assertEquals("hello", text)
        } finally {
            context.contentResolver.delete(stored.uri, null, null)
        }
    }

    @Test
    fun cookieVaultEncryptsRoundTrip() {
        val vault = CookieVault(context)
        assertTrue("keystore unavailable", vault.isAvailable)
        val cookie = CookieEntry(".example.org", true, "/", true, 4_000_000_000, "sid", "value with spaces")
        vault.put(Platform.GENERIC, listOf(cookie), CookieSource.IMPORT)
        assertEquals(listOf(cookie), vault.get(Platform.GENERIC)?.cookies)
        vault.clear(Platform.GENERIC)
        assertNull(vault.get(Platform.GENERIC))
        // Nothing readable in plain text on disk.
        val raw = File(context.applicationInfo.dataDir, "shared_prefs/alo_cookies.xml")
        assertFalse(raw.exists() && raw.readText().contains("value with spaces"))
    }
}
