package com.alo.downloader.engine

import com.alo.downloader.core.engine.DownloadEngine
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.Platform
import com.alo.downloader.core.router.EngineRouter
import com.alo.downloader.engine.gallerydl.GalleryDlEngine
import com.alo.downloader.engine.ytdlp.YtDlpEngine
import com.alo.downloader.telegram.TelegramEngine
import javax.inject.Inject
import javax.inject.Singleton

/** Knows every engine and asks [EngineRouter] which ones to try, in order, for a link. */
@Singleton
class EngineRegistry @Inject constructor(
    ytDlp: YtDlpEngine,
    galleryDl: GalleryDlEngine,
    telegram: TelegramEngine,
) {
    private val engines: Map<EngineType, DownloadEngine> = mapOf(
        EngineType.YT_DLP to ytDlp,
        EngineType.GALLERY_DL to galleryDl,
        EngineType.TELEGRAM to telegram,
    )

    fun plan(url: String, platform: Platform, forced: EngineType?, allowFallback: Boolean): List<DownloadEngine> =
        EngineRouter.route(url, platform, forced, engines.keys, allowFallback).mapNotNull(engines::get)
}
