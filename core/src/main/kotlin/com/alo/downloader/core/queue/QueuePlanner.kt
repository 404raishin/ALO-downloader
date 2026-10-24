package com.alo.downloader.core.queue

import com.alo.downloader.core.model.DownloadStatus

/** Minimal view of a task the planner needs. */
data class QueueEntry(
    val id: String,
    val status: DownloadStatus,
    val createdAt: Long,
    /** Tasks with a higher priority start first (e.g. "download now" from the share sheet). */
    val priority: Int = 0,
    /** Epoch millis; only relevant for [DownloadStatus.SCHEDULED]. */
    val scheduledAt: Long? = null,
    /** Some engines (gallery-dl inside one embedded interpreter) run one task at a time. */
    val exclusiveGroup: String? = null,
)

/**
 * Decides which queued tasks may start, given the concurrency limit. Pure logic so the
 * scheduling rules can be tested without WorkManager.
 */
object QueuePlanner {

    data class Plan(
        val toStart: List<String>,
        /** Scheduled tasks whose time has come and should move back to QUEUED. */
        val dueScheduled: List<String>,
        /** Earliest future scheduled time, used to arm the next wake-up. */
        val nextWakeAt: Long?,
    )

    fun plan(
        entries: List<QueueEntry>,
        maxConcurrent: Int,
        now: Long,
        networkAllowed: Boolean = true,
    ): Plan {
        val limit = maxConcurrent.coerceIn(1, 16)
        val running = entries.filter { it.status.isActive }
        val busyGroups = running.mapNotNull { it.exclusiveGroup }.toMutableSet()

        val due = entries.filter { it.status == DownloadStatus.SCHEDULED && (it.scheduledAt ?: 0) <= now }
        val nextWake = entries
            .filter { it.status == DownloadStatus.SCHEDULED && (it.scheduledAt ?: 0) > now }
            .minOfOrNull { it.scheduledAt!! }

        if (!networkAllowed) return Plan(emptyList(), due.map { it.id }, nextWake)

        var free = limit - running.size
        val start = mutableListOf<String>()
        val candidates = (entries.filter { it.status == DownloadStatus.QUEUED } + due)
            .sortedWith(compareByDescending<QueueEntry> { it.priority }.thenBy { it.createdAt })
        for (entry in candidates) {
            if (free <= 0) break
            val group = entry.exclusiveGroup
            if (group != null && group in busyGroups) continue
            start += entry.id
            if (group != null) busyGroups += group
            free--
        }
        return Plan(start, due.map { it.id }, nextWake)
    }
}
