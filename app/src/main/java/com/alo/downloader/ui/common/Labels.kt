package com.alo.downloader.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.alo.downloader.R
import com.alo.downloader.core.model.DownloadStatus
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.core.model.Platform

@StringRes
fun failureReasonText(reason: FailureReason): Int = when (reason) {
    FailureReason.LOGIN_REQUIRED -> R.string.error_login_required
    FailureReason.COOKIE_EXPIRED -> R.string.error_cookie_expired
    FailureReason.PRIVATE_CONTENT -> R.string.error_private
    FailureReason.RATE_LIMITED -> R.string.error_rate_limited
    FailureReason.GEO_BLOCKED -> R.string.error_geo_blocked
    FailureReason.NOT_FOUND -> R.string.error_not_found
    FailureReason.STORAGE -> R.string.error_storage
    FailureReason.NETWORK -> R.string.error_network
    FailureReason.NO_MEDIA -> R.string.error_no_media
    FailureReason.ENGINE_UNAVAILABLE -> R.string.error_engine_unavailable
    FailureReason.UNSUPPORTED_URL -> R.string.error_unsupported
    FailureReason.CANCELED -> R.string.status_canceled
    FailureReason.UNKNOWN -> R.string.error_unknown
}

@StringRes
fun failureHintText(reason: FailureReason): Int? = when (reason) {
    FailureReason.LOGIN_REQUIRED, FailureReason.PRIVATE_CONTENT -> R.string.hint_login
    FailureReason.COOKIE_EXPIRED -> R.string.hint_cookie_expired
    FailureReason.RATE_LIMITED -> R.string.hint_rate_limited
    FailureReason.NETWORK -> R.string.hint_network
    FailureReason.ENGINE_UNAVAILABLE, FailureReason.UNSUPPORTED_URL, FailureReason.NO_MEDIA -> R.string.hint_update_engines
    else -> null
}

@StringRes
fun statusText(status: DownloadStatus): Int = when (status) {
    DownloadStatus.QUEUED -> R.string.status_queued
    DownloadStatus.SCHEDULED -> R.string.status_scheduled
    DownloadStatus.PREPARING -> R.string.status_preparing
    DownloadStatus.RUNNING -> R.string.status_running
    DownloadStatus.PAUSED -> R.string.status_paused
    DownloadStatus.COMPLETED -> R.string.status_completed
    DownloadStatus.FAILED -> R.string.status_failed
    DownloadStatus.CANCELED -> R.string.status_canceled
}

/** Neutral accent per platform for the letter badge (no brand logos are bundled). */
fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> Color(0xFFE53935)
    Platform.INSTAGRAM -> Color(0xFFC13584)
    Platform.X -> Color(0xFF37474F)
    Platform.TIKTOK -> Color(0xFF00897B)
    Platform.FACEBOOK -> Color(0xFF1E5BD8)
    Platform.THREADS -> Color(0xFF424242)
    Platform.REDDIT -> Color(0xFFF4511E)
    Platform.PINTEREST -> Color(0xFFD32F2F)
    Platform.TUMBLR -> Color(0xFF3949AB)
    Platform.BILIBILI -> Color(0xFF00A1D6)
    Platform.WEIBO -> Color(0xFFE6162D)
    Platform.PIXIV -> Color(0xFF0096FA)
    Platform.DEVIANTART -> Color(0xFF05CC47)
    Platform.IMGUR -> Color(0xFF1BB76E)
    Platform.FLICKR -> Color(0xFFFF0084)
    Platform.VIMEO -> Color(0xFF1AB7EA)
    Platform.TWITCH -> Color(0xFF9146FF)
    Platform.SOUNDCLOUD -> Color(0xFFFF5500)
    Platform.TELEGRAM -> Color(0xFF229ED9)
    Platform.GENERIC -> Color(0xFF757575)
}
