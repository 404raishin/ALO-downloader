package com.alo.downloader.core.link

import java.net.URI

/** Pulls downloadable links out of shared text, clipboard contents or user input. */
object LinkExtractor {

    private val urlRegex = Regex(
        """(?i)\b((?:https?://|www\.)[^\s<>"'`，。、！？「」【】（）]+)""",
    )
    private val trailingJunk = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"', '>', '…')

    /** All distinct links in [text], in order of appearance. */
    fun extractAll(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return urlRegex.findAll(text)
            .mapNotNull { normalize(it.value) }
            .distinct()
            .toList()
    }

    fun extractFirst(text: String?): String? = extractAll(text).firstOrNull()

    fun normalize(raw: String): String? {
        var url = raw.trim().trimEnd(*trailingJunk)
        // Balance a trailing ")" that belongs to the URL (e.g. Wikipedia style links).
        if (raw.trim().endsWith(")") && url.count { it == '(' } > url.count { it == ')' }) url += ")"
        if (url.startsWith("www.", ignoreCase = true)) url = "https://$url"
        return runCatching {
            val uri = URI(url)
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) null else url
        }.getOrNull()
    }

    fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()

    fun isLink(text: String?): Boolean = extractFirst(text) != null
}
