package com.alo.downloader.core.router

import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.core.model.Platform
import java.net.URI

/**
 * Chooses which engines handle a link and in which order. Videos go to yt-dlp first,
 * galleries and image posts to gallery-dl, t.me links to the Telegram engine; the other
 * engines follow as fallbacks.
 */
object EngineRouter {
    private val YT = EngineType.YT_DLP
    private val GDL = EngineType.GALLERY_DL
    private val TG = EngineType.TELEGRAM

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "bmp")
    private val galleryPlatforms = setOf(
        Platform.PINTEREST, Platform.PIXIV, Platform.DEVIANTART, Platform.IMGUR,
        Platform.FLICKR, Platform.TUMBLR, Platform.WEIBO,
    )

    fun route(
        url: String,
        platform: Platform = PlatformResolver.resolve(url),
        forced: EngineType? = null,
        available: Set<EngineType> = EngineType.entries.toSet(),
        allowFallback: Boolean = true,
    ): List<EngineType> {
        val preferred = preferredOrder(url, platform)
        val ordered = if (forced != null) listOf(forced) + preferred.filter { it != forced } else preferred
        val usable = ordered.filter { it in available }
        return if (allowFallback) usable else usable.take(1)
    }

    /** Full preference order for a link, before availability and fallback settings. */
    fun preferredOrder(url: String, platform: Platform): List<EngineType> {
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when (platform) {
            Platform.TELEGRAM -> listOf(TG, YT)
            Platform.INSTAGRAM -> when (segments.firstOrNull()) {
                "reel", "reels", "tv" -> listOf(YT, GDL)
                else -> listOf(GDL, YT) // posts (often carousels), stories, highlights, profiles
            }
            Platform.X -> when {
                "status" in segments && "video" in segments -> listOf(YT, GDL)
                "status" in segments -> listOf(YT, GDL) // video tweets; image tweets fall back to gallery-dl
                else -> listOf(GDL, YT) // profiles, media tabs, likes
            }
            Platform.TIKTOK -> if ("photo" in segments) listOf(GDL, YT) else listOf(YT, GDL)
            Platform.REDDIT -> if ("gallery" in segments || url.contains("i.redd.it")) listOf(GDL, YT) else listOf(YT, GDL)
            Platform.FACEBOOK -> if (segments.any { it.startsWith("photo") }) listOf(GDL, YT) else listOf(YT, GDL)
            in galleryPlatforms -> listOf(GDL, YT)
            Platform.GENERIC -> if (path.substringAfterLast('.', "") in imageExtensions) listOf(GDL, YT) else listOf(YT, GDL)
            else -> listOf(YT, GDL)
        }
    }

    /** Whether trying another engine can help after a failure with [reason]. */
    fun shouldFallback(reason: FailureReason): Boolean = when (reason) {
        FailureReason.CANCELED, FailureReason.STORAGE -> false
        else -> true
    }
}
