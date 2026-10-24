package com.alo.downloader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alo.downloader.core.model.DownloadStatus
import com.alo.downloader.di.AppEntryPoint
import com.alo.downloader.download.DownloadManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end through the real app graph: DownloadManager → WorkManager queue worker →
 * router → yt-dlp → MediaStore → Room history.
 */
@RunWith(AndroidJUnit4::class)
class DownloadPipelineTest {
    private val graph = EntryPointAccessors.fromApplication(Fixtures.context, AppEntryPoint::class.java)

    @Test
    fun queuedLinkEndsUpInDownloadsFolder() = runBlocking {
        val manager = graph.downloadManager()
        val id = manager.enqueue(listOf(DownloadManager.Request(Fixtures.clipUrl, options = Fixtures.quietOptions()))).single()
        try {
            val task = withTimeout(180_000) {
                var t = graph.taskDao().get(id)
                while (t == null || !t.status.isFinished) {
                    delay(500)
                    t = graph.taskDao().get(id)
                }
                t
            }
            assertEquals("${task.errorReason}: ${task.errorMessage} (${task.attempts})", DownloadStatus.COMPLETED, task.status)
            assertEquals("yt-dlp", task.engine)
            val files = graph.fileDao().forTask(id)
            assertEquals(1, files.size)
            assertTrue(files[0].location, files[0].location.endsWith("ALO/Other"))
            val bytes = Fixtures.context.contentResolver.openInputStream(Uri.parse(files[0].uri))!!.use { it.readBytes() }
            assertTrue(bytes.size > 1000)
        } finally {
            manager.delete(id, deleteFiles = true)
        }
    }
}
