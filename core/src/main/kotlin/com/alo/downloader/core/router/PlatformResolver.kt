package com.alo.downloader.core.router

import com.alo.downloader.core.link.LinkExtractor
import com.alo.downloader.core.model.Platform

object PlatformResolver {

    fun resolve(url: String): Platform {
        val host = LinkExtractor.hostOf(url) ?: return Platform.GENERIC
        return fromHost(host)
    }

    fun fromHost(host: String): Platform {
        val h = host.lowercase().removePrefix("www.")
        return Platform.entries.firstOrNull { p ->
            p.domains.any { d -> h == d || h.endsWith(".$d") }
        } ?: Platform.GENERIC
    }

    /** Matches a cookie domain such as ".instagram.com" to a platform. */
    fun fromCookieDomain(domain: String): Platform = fromHost(domain.trimStart('.'))
}
