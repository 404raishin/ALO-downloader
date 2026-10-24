package com.alo.downloader.core.telegram

/** Media found on the public web preview of a Telegram post. */
data class TelegramEmbed(
    val mediaUrls: List<String>,
    val text: String?,
    val channelTitle: String?,
    /** True when the page says the media must be opened in the app (e.g. too large). */
    val needsApp: Boolean,
)

/**
 * Reads `https://t.me/<channel>/<id>?embed=1&mode=tme`, the widget page Telegram serves for
 * public posts, and extracts direct links to the photos and videos in it.
 */
object TelegramEmbedParser {
    private val tag = Regex("""<(a|div|video|i)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val classAttr = Regex("""class\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val srcAttr = Regex("""\ssrc\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val bgImage = Regex("""background-image\s*:\s*url\(\s*['"]?([^'")]+)['"]?\s*\)""", RegexOption.IGNORE_CASE)
    private val metaContent = { prop: String ->
        Regex("""<meta\s+(?:property|name)="$prop"\s+content="([^"]*)"""", RegexOption.IGNORE_CASE)
    }

    fun embedUrl(link: TelegramLink.PublicPost): String =
        "https://t.me/${link.username}/${link.messageId}?embed=1&mode=tme" + if (link.single) "&single=1" else ""

    fun parse(html: String): TelegramEmbed {
        val media = mutableListOf<String>()
        for (match in tag.findAll(html)) {
            val element = match.value
            val classes = classAttr.find(element)?.groupValues?.get(1).orEmpty()
            when {
                match.groupValues[1].equals("video", true) &&
                    (classes.contains("message_video") || classes.contains("message_roundvideo")) ->
                    srcAttr.find(element)?.groupValues?.get(1)?.let { media += decode(it) }
                classes.contains("tgme_widget_message_photo_wrap") ->
                    bgImage.find(element)?.groupValues?.get(1)?.let { media += decode(it) }
            }
        }
        val needsApp = html.contains("tgme_widget_message_video_player") && media.none { it.contains(".mp4") || it.contains("video") } ||
            html.contains("Media is too big", ignoreCase = true) ||
            html.contains("tgme_widget_message_document", ignoreCase = true)
        return TelegramEmbed(
            mediaUrls = media.distinct().filter { it.startsWith("http") },
            text = metaContent("og:description").find(html)?.groupValues?.get(1)?.let(::decode)?.takeIf { it.isNotBlank() },
            channelTitle = metaContent("og:title").find(html)?.groupValues?.get(1)?.let(::decode),
            needsApp = needsApp,
        )
    }

    private fun decode(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
