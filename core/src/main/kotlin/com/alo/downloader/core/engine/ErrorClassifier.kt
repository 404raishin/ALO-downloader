package com.alo.downloader.core.engine

import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason

/**
 * Turns raw engine output (yt-dlp / gallery-dl stderr, HTTP errors) into a [FailureReason]
 * the UI can explain and offer a fix for.
 */
object ErrorClassifier {

    private val rules: List<Pair<FailureReason, List<Regex>>> = listOf(
        FailureReason.CANCELED to listOf(
            rx("""\bcancel(l)?ed\b"""), rx("""KeyboardInterrupt"""), rx("""\binterrupted\b"""),
        ),
        FailureReason.RATE_LIMITED to listOf(
            rx("""\b429\b"""), rx("""too many requests"""), rx("""rate[- ]?limit"""),
            rx("""please wait a few minutes"""), rx("""temporarily (blocked|locked)"""),
            rx("""try again later"""), rx("""FLOOD_WAIT"""), rx("""feedback_required"""),
        ),
        FailureReason.LOGIN_REQUIRED to listOf(
            rx("""sign in to confirm"""), rx("""login required"""), rx("""log ?in to"""),
            rx("""requires? (authentication|login|an account|cookies)"""), rx("""use --cookies"""),
            rx("""--cookies-from-browser"""), rx("""\b401\b"""), rx("""unauthori[sz]ed"""),
            rx("""AuthorizationError"""), rx("""AuthRequired"""), rx("""not logged in"""),
            rx("""checkpoint_required"""), rx("""login_required"""), rx("""age[- ]restricted"""),
            rx("""confirm your age"""), rx("""inappropriate for some users"""),
            rx("""you need to log in"""), rx("""members[- ]only"""), rx("""join this channel"""),
            rx("""AuthenticationError"""), rx("""sensitive content"""), rx("""NSFW"""),
            // Instagram sends anonymous requests to its login page instead of the post.
            rx("""redirect(ed)? to (the )?login"""), rx("""/accounts/login"""),
        ),
        FailureReason.PRIVATE_CONTENT to listOf(
            rx("""private (video|account|profile|post|channel|playlist)"""), rx("""this (video|account|post) is private"""),
            rx("""\b403\b"""), rx("""forbidden"""), rx("""CHANNEL_PRIVATE"""), rx("""CHAT_ACCESS_REQUIRED"""),
            rx("""you (do not|don't) have (access|permission)"""),
        ),
        FailureReason.GEO_BLOCKED to listOf(
            rx("""not (made )?available in your (country|region|location)"""), rx("""geo[- ]?restrict"""),
            rx("""blocked in your country"""),
        ),
        FailureReason.NOT_FOUND to listOf(
            rx("""\b404\b"""), rx("""not found"""), rx("""does not exist"""), rx("""video unavailable"""),
            rx("""has been removed"""), rx("""no longer available"""), rx("""content isn't available"""),
            rx("""NotFoundError"""), rx("""MESSAGE_ID_INVALID"""), rx("""USERNAME_NOT_OCCUPIED"""),
            rx("""account (has been )?(terminated|suspended)"""),
        ),
        FailureReason.STORAGE to listOf(
            rx("""no space left"""), rx("""ENOSPC"""), rx("""disk full"""), rx("""read-only file system"""),
            rx("""permission denied"""),
        ),
        FailureReason.NO_MEDIA to listOf(
            rx("""no video (could be )?found"""), rx("""there'?s no video"""), rx("""no media found"""),
            rx("""NoSuchMedia"""), rx("""no (video|audio) formats? found"""), rx("""requested format is not available"""),
            rx("""does not contain (any )?(video|media)"""), rx("""no downloadable media"""),
        ),
        FailureReason.UNSUPPORTED_URL to listOf(
            rx("""unsupported url"""), rx("""no suitable extractor"""), rx("""NoExtractorError"""),
            rx("""is not a valid url"""), rx("""no extractor found"""),
        ),
        FailureReason.NETWORK to listOf(
            rx("""unable to download (webpage|json|video data)"""), rx("""connection (reset|refused|aborted)"""),
            rx("""timed? ?out"""), rx("""temporary failure in name resolution"""), rx("""network is unreachable"""),
            rx("""failed to resolve"""), rx("""SSLError"""), rx("""\bSSL\b"""), rx("""ConnectionError"""),
            rx("""UnknownHostException"""), rx("""Remote end closed connection"""), rx("""HTTP Error 5\d\d"""),
            rx("""IncompleteRead"""),
        ),
        FailureReason.ENGINE_UNAVAILABLE to listOf(
            rx("""failed to initiali[sz]e"""), rx("""python (is )?not (started|available)"""),
            rx("""ModuleNotFoundError"""), rx("""engine (is )?not available"""),
        ),
    )

    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    /**
     * @param hasCookies whether cookies were passed to the engine. A login error despite
     *   cookies means they are stale, which gets its own reason so the UI can say so.
     */
    fun classify(output: String, hasCookies: Boolean = false): FailureReason {
        // Look at error lines first, they carry the real cause; fall back to everything.
        val errorLines = output.lineSequence()
            .filter { it.contains("error", ignoreCase = true) || it.contains("exception", ignoreCase = true) }
            .joinToString("\n")
        val reason = match(errorLines) ?: match(output) ?: FailureReason.UNKNOWN
        return if (reason == FailureReason.LOGIN_REQUIRED && hasCookies) FailureReason.COOKIE_EXPIRED else reason
    }

    private fun match(text: String): FailureReason? {
        if (text.isBlank()) return null
        return rules.firstOrNull { (_, patterns) -> patterns.any { it.containsMatchIn(text) } }?.first
    }

    /** Builds a [DownloadError] with a short, readable message out of a noisy log. */
    fun toError(output: String, engine: EngineType, hasCookies: Boolean = false): DownloadError =
        DownloadError(classify(output, hasCookies), summarize(output), engine)

    fun summarize(output: String, maxLength: Int = 400): String {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val errors = lines.filter { it.contains("error", ignoreCase = true) }
        val picked = (errors.ifEmpty { lines }).takeLast(3).joinToString("\n")
        return if (picked.length > maxLength) picked.take(maxLength) + "…" else picked
    }
}
