package com.alo.downloader.core.model

enum class DownloadStatus {
    /** Waiting for a free slot in the queue. */
    QUEUED,

    /** Waiting for a scheduled start time. */
    SCHEDULED,

    /** Fetching metadata before the actual download starts. */
    PREPARING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED;

    val isActive: Boolean get() = this == PREPARING || this == RUNNING
    val isFinished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELED
    val canPause: Boolean get() = this == QUEUED || this == PREPARING || this == RUNNING || this == SCHEDULED
    val canResume: Boolean get() = this == PAUSED
    val canRetry: Boolean get() = this == FAILED || this == CANCELED
}
