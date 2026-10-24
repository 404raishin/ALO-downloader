package com.alo.downloader.core.queue

import com.alo.downloader.core.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePlannerTest {
    private fun e(id: String, status: DownloadStatus, t: Long, prio: Int = 0, at: Long? = null, group: String? = null) =
        QueueEntry(id, status, t, prio, at, group)

    @Test
    fun `respects concurrency limit and order`() {
        val entries = listOf(
            e("a", DownloadStatus.RUNNING, 1),
            e("b", DownloadStatus.QUEUED, 3),
            e("c", DownloadStatus.QUEUED, 2),
            e("d", DownloadStatus.QUEUED, 4),
        )
        val plan = QueuePlanner.plan(entries, maxConcurrent = 2, now = 10)
        assertEquals(listOf("c"), plan.toStart)
    }

    @Test
    fun `priority first`() {
        val entries = listOf(e("old", DownloadStatus.QUEUED, 1), e("urgent", DownloadStatus.QUEUED, 5, prio = 1))
        assertEquals(listOf("urgent"), QueuePlanner.plan(entries, 1, 10).toStart)
    }

    @Test
    fun `nothing starts without network`() {
        val plan = QueuePlanner.plan(listOf(e("a", DownloadStatus.QUEUED, 1)), 3, 10, networkAllowed = false)
        assertTrue(plan.toStart.isEmpty())
    }

    @Test
    fun `scheduled tasks become due`() {
        val entries = listOf(
            e("s1", DownloadStatus.SCHEDULED, 1, at = 5),
            e("s2", DownloadStatus.SCHEDULED, 1, at = 50),
        )
        val plan = QueuePlanner.plan(entries, 3, now = 10)
        assertEquals(listOf("s1"), plan.dueScheduled)
        assertEquals(listOf("s1"), plan.toStart)
        assertEquals(50L, plan.nextWakeAt)
    }

    @Test
    fun `exclusive group runs one at a time`() {
        val entries = listOf(
            e("g1", DownloadStatus.RUNNING, 1, group = "gallery-dl"),
            e("g2", DownloadStatus.QUEUED, 2, group = "gallery-dl"),
            e("v1", DownloadStatus.QUEUED, 3),
        )
        assertEquals(listOf("v1"), QueuePlanner.plan(entries, 3, 10).toStart)
    }

    @Test
    fun `paused and finished tasks are ignored`() {
        val entries = listOf(
            e("p", DownloadStatus.PAUSED, 1),
            e("f", DownloadStatus.FAILED, 1),
            e("c", DownloadStatus.COMPLETED, 1),
        )
        assertTrue(QueuePlanner.plan(entries, 3, 10).toStart.isEmpty())
    }
}
