package com.alo.downloader.core.model

/** Why a download failed, in terms the user can act on. */
enum class FailureReason(
    /** Higher wins when several engines fail for the same task. */
    val priority: Int,
    /** True when logging in (again) is the likely fix. */
    val needsLogin: Boolean = false,
) {
    LOGIN_REQUIRED(100, needsLogin = true),
    COOKIE_EXPIRED(95, needsLogin = true),
    PRIVATE_CONTENT(90, needsLogin = true),
    RATE_LIMITED(80),
    GEO_BLOCKED(70),
    NOT_FOUND(60),
    STORAGE(55),
    NETWORK(50),
    NO_MEDIA(30),
    ENGINE_UNAVAILABLE(20),
    UNSUPPORTED_URL(10),
    CANCELED(0),
    UNKNOWN(5),
}

data class DownloadError(
    val reason: FailureReason,
    /** Last meaningful line(s) of engine output. */
    val message: String,
    val engine: EngineType? = null,
) {
    companion object {
        /** Picks the most actionable error out of several failed attempts. */
        fun mostRelevant(errors: List<DownloadError>): DownloadError? =
            errors.maxByOrNull { it.reason.priority }
    }
}
