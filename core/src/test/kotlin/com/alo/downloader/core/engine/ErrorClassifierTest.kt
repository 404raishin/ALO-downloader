package com.alo.downloader.core.engine

import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorClassifierTest {

    @Test
    fun `youtube bot check needs login`() {
        val out = "ERROR: [youtube] abc: Sign in to confirm you're not a bot. Use --cookies-from-browser or --cookies"
        assertEquals(FailureReason.LOGIN_REQUIRED, ErrorClassifier.classify(out))
    }

    @Test
    fun `instagram login redirect needs login`() {
        val out = "[instagram][error] HTTP redirect to login page (https://www.instagram.com/accounts/login/)"
        assertEquals(FailureReason.LOGIN_REQUIRED, ErrorClassifier.classify(out))
        assertEquals(FailureReason.COOKIE_EXPIRED, ErrorClassifier.classify(out, hasCookies = true))
    }

    @Test
    fun `login error with cookies means cookies expired`() {
        val out = "ERROR: [Instagram] xyz: Requested content is not available, rate-limit reached or login required"
        // rate-limit wording wins over login wording because it is checked first
        assertEquals(FailureReason.RATE_LIMITED, ErrorClassifier.classify(out, hasCookies = true))
        val login = "[instagram][error] AuthorizationError: Login required"
        assertEquals(FailureReason.COOKIE_EXPIRED, ErrorClassifier.classify(login, hasCookies = true))
    }

    @Test
    fun `http 429 is rate limited`() {
        assertEquals(FailureReason.RATE_LIMITED, ErrorClassifier.classify("ERROR: HTTP Error 429: Too Many Requests"))
    }

    @Test
    fun `unsupported url`() {
        assertEquals(FailureReason.UNSUPPORTED_URL, ErrorClassifier.classify("ERROR: Unsupported URL: https://example.com/x"))
        assertEquals(FailureReason.UNSUPPORTED_URL, ErrorClassifier.classify("[error] No suitable extractor found for 'https://a.b'"))
    }

    @Test
    fun `no video in tweet`() {
        assertEquals(FailureReason.NO_MEDIA, ErrorClassifier.classify("ERROR: [twitter] 123: No video could be found in this tweet"))
    }

    @Test
    fun `not found and network`() {
        assertEquals(FailureReason.NOT_FOUND, ErrorClassifier.classify("ERROR: [youtube] id: Video unavailable"))
        assertEquals(FailureReason.NETWORK, ErrorClassifier.classify("ERROR: Unable to download webpage: <urlopen error [Errno 7] Temporary failure in name resolution>"))
    }

    @Test
    fun `unknown output`() {
        assertEquals(FailureReason.UNKNOWN, ErrorClassifier.classify("something odd happened"))
        assertEquals(FailureReason.UNKNOWN, ErrorClassifier.classify(""))
    }

    @Test
    fun `most relevant error prefers login`() {
        val errors = listOf(
            DownloadError(FailureReason.UNSUPPORTED_URL, "a", EngineType.GALLERY_DL),
            DownloadError(FailureReason.LOGIN_REQUIRED, "b", EngineType.YT_DLP),
            DownloadError(FailureReason.NETWORK, "c", EngineType.YT_DLP),
        )
        assertEquals(FailureReason.LOGIN_REQUIRED, DownloadError.mostRelevant(errors)?.reason)
    }

    @Test
    fun `summary keeps error lines`() {
        val out = "[info] foo\n[download] 10%\nERROR: boom\n"
        assertEquals("ERROR: boom", ErrorClassifier.summarize(out))
    }
}
