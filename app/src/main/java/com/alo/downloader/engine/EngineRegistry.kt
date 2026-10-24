package com.alo.downloader.engine

import com.alo.downloader.core.engine.DownloadEngine
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.Platform
import com.alo.downloader.engine.ytdlp.YtDlpEngine
import javax.inject.Inject
import javax.inject.Singleton

/** Knows every engine and decides which ones to try, in order, for a link. */
@Singleton
class EngineRegistry @Inject constructor(
    private val ytDlp: YtDlpEngine,
) {
    private val engines: Map<EngineType, DownloadEngine> = mapOf(EngineType.YT_DLP to ytDlp)

    fun get(type: EngineType): DownloadEngine? = engines[type]

    @Suppress("UNUSED_PARAMETER")
    fun plan(url: String, platform: Platform, forced: EngineType?, allowFallback: Boolean): List<DownloadEngine> =
        listOfNotNull(forced?.let(engines::get) ?: ytDlp)

    fun all(): Collection<DownloadEngine> = engines.values
}
