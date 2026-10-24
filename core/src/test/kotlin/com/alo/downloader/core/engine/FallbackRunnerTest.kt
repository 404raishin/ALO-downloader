package com.alo.downloader.core.engine

import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.core.model.Platform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FallbackRunnerTest {
    private class FakeEngine(override val type: EngineType, private val result: EngineResult) : DownloadEngine {
        var calls = 0
        override suspend fun download(job: EngineJob, listener: ProgressListener): EngineResult {
            calls++
            return result
        }
        override fun cancel(taskId: String) = Unit
    }

    private val job = { _: DownloadEngine -> EngineJob("t", "https://x", Platform.GENERIC, File("/w"), DownloadOptions()) }
    private fun fail(reason: FailureReason, engine: EngineType) =
        EngineResult.Failure(DownloadError(reason, reason.name, engine))

    @Test
    fun `falls back to the next engine`() = runTest {
        val a = FakeEngine(EngineType.YT_DLP, fail(FailureReason.NO_MEDIA, EngineType.YT_DLP))
        val b = FakeEngine(EngineType.GALLERY_DL, EngineResult.Success(listOf(File("/w/a.jpg"))))
        val outcome = FallbackRunner.run(listOf(a, b), job, {})
        assertTrue(outcome.result is EngineResult.Success)
        assertEquals(b, outcome.successfulEngine)
        assertEquals(listOf("yt-dlp ✗", "gallery-dl ✓"), outcome.attempts)
    }

    @Test
    fun `reports the most actionable error`() = runTest {
        val a = FakeEngine(EngineType.YT_DLP, fail(FailureReason.LOGIN_REQUIRED, EngineType.YT_DLP))
        val b = FakeEngine(EngineType.GALLERY_DL, fail(FailureReason.UNSUPPORTED_URL, EngineType.GALLERY_DL))
        val outcome = FallbackRunner.run(listOf(a, b), job, {})
        assertEquals(FailureReason.LOGIN_REQUIRED, (outcome.result as EngineResult.Failure).error.reason)
        assertEquals(1, b.calls)
    }

    @Test
    fun `cancel stops the chain`() = runTest {
        val a = FakeEngine(EngineType.YT_DLP, fail(FailureReason.CANCELED, EngineType.YT_DLP))
        val b = FakeEngine(EngineType.GALLERY_DL, EngineResult.Success(emptyList()))
        val outcome = FallbackRunner.run(listOf(a, b), job, {})
        assertEquals(0, b.calls)
        assertEquals(FailureReason.CANCELED, (outcome.result as EngineResult.Failure).error.reason)
    }

    @Test
    fun `no engines`() = runTest {
        val outcome = FallbackRunner.run(emptyList(), job, {})
        assertEquals(FailureReason.ENGINE_UNAVAILABLE, (outcome.result as EngineResult.Failure).error.reason)
    }
}
