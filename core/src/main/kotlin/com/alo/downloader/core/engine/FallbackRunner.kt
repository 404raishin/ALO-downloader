package com.alo.downloader.core.engine

import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.core.router.EngineRouter

/** Outcome of running a task through one or more engines. */
data class FallbackOutcome(
    val result: EngineResult,
    /** e.g. ["yt-dlp ✗", "gallery-dl ✓"] */
    val attempts: List<String>,
    val successfulEngine: DownloadEngine?,
)

/** Tries engines in order until one succeeds or a failure makes further attempts pointless. */
object FallbackRunner {
    suspend fun run(
        engines: List<DownloadEngine>,
        jobFor: (DownloadEngine) -> EngineJob,
        listener: ProgressListener,
        onAttempt: suspend (DownloadEngine) -> Unit = {},
    ): FallbackOutcome {
        val errors = mutableListOf<DownloadError>()
        val attempts = mutableListOf<String>()
        for (engine in engines) {
            onAttempt(engine)
            when (val result = engine.download(jobFor(engine), listener)) {
                is EngineResult.Success -> {
                    attempts += "${engine.type.displayName} ✓"
                    return FallbackOutcome(result, attempts, engine)
                }
                is EngineResult.Failure -> {
                    attempts += "${engine.type.displayName} ✗"
                    errors += result.error
                    if (!EngineRouter.shouldFallback(result.error.reason)) break
                }
            }
        }
        val error = DownloadError.mostRelevant(errors)
            ?: DownloadError(FailureReason.ENGINE_UNAVAILABLE, "No engine can handle this link")
        return FallbackOutcome(EngineResult.Failure(error), attempts, null)
    }
}
