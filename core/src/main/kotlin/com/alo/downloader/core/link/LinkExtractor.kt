package com.alo.downloader.core.link

import com.alo.downloader.core.model.Platform
import java.net.URI

/** Pulls downloadable links out of shared text, clipboard contents or user input. */
object LinkExtractor {

    private val urlRegex = Regex(
        """(?i)\b((?:https?://|www\.)[^\s<>"'`，。、！？「」【】（）]+)""",
    )
    private val trailingJunk = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"', '>', '…')

    /**
     * Known platform domains written without a scheme, e.g. "youtu.be/abc" or
     * "instagram.com/p/xyz" as some apps copy them.
     */
    private val bareRegex: Regex by lazy {
        val domains = Platform.entries.flatMap { it.domains }.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        Regex("""(?i)(?<![\w@./-])((?:[a-z0-9-]+\.)*(?:$domains)/[^\s<>"'`，。、！？「」【】（）]+)""")
    }

    /** All distinct links in [text], in order of appearance. */
    fun extractAll(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val found = mutableListOf<Pair<Int, String>>()
        val masked = StringBuilder(text)
        for (m in urlRegex.findAll(text)) {
            normalize(m.value)?.let { found += m.range.first to it }
            for (i in m.range) masked.setCharAt(i, ' ')
        }
        for (m in bareRegex.findAll(masked)) {
            normalize("https://" + m.value)?.let { found += m.range.first to it }
        }
        return found.sortedBy { it.first }.map { it.second }.distinct()
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
}
