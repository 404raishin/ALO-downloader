package com.alo.downloader.core.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class MediaFormat(
    val id: String,
    val ext: String,
    val note: String?,
    val width: Int?,
    val height: Int?,
    val fps: Double?,
    val vcodec: String?,
    val acodec: String?,
    val sizeBytes: Long?,
    val tbr: Double?,
) {
    val hasVideo: Boolean get() = vcodec != null && vcodec != "none"
    val hasAudio: Boolean get() = acodec != null && acodec != "none"
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
    val isVideoOnly: Boolean get() = hasVideo && !hasAudio

    /** e.g. "1080p60 · mp4 · avc1" */
    fun label(): String = buildList {
        if (hasVideo) add(height?.let { h -> "${h}p" + (fps?.takeIf { it > 30 }?.let { f -> f.toInt().toString() } ?: "") } ?: note ?: id)
        else add(tbr?.let { "${it.toInt()}k" } ?: note ?: id)
        add(ext)
        (if (hasVideo) vcodec else acodec)?.substringBefore('.')?.let(::add)
    }.joinToString(" · ")
}

data class PlaylistEntry(val index: Int, val id: String?, val title: String?, val url: String?, val durationSeconds: Long?)

data class MediaInfo(
    val id: String?,
    val title: String?,
    val uploader: String?,
    val thumbnail: String?,
    val durationSeconds: Long?,
    val webpageUrl: String?,
    val extractor: String?,
    val formats: List<MediaFormat>,
    val subtitleLanguages: List<String>,
    val autoSubtitleLanguages: List<String>,
    val isPlaylist: Boolean,
    val entries: List<PlaylistEntry>,
) {
    val videoFormats: List<MediaFormat> get() = formats.filter { it.hasVideo }.sortedByDescending { (it.height ?: 0) * 1000 + (it.tbr ?: 0.0).toInt() }
    val audioFormats: List<MediaFormat> get() = formats.filter { it.isAudioOnly }.sortedByDescending { it.tbr ?: 0.0 }
}

/** Parses the output of `yt-dlp --dump-single-json`. */
object MediaInfoParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): MediaInfo? {
        // yt-dlp may print warnings before the JSON line.
        val start = text.indexOf('{')
        if (start < 0) return null
        val obj = runCatching { json.parseToJsonElement(text.substring(start)).jsonObject }.getOrNull() ?: return null
        return fromObject(obj)
    }

    private fun fromObject(o: JsonObject): MediaInfo {
        val type = o.str("_type")
        val entries = (o["entries"] as? JsonArray)?.mapIndexedNotNull { i, e ->
            val eo = e as? JsonObject ?: return@mapIndexedNotNull null
            PlaylistEntry(
                index = eo.long("playlist_index")?.toInt() ?: (i + 1),
                id = eo.str("id"),
                title = eo.str("title"),
                url = eo.str("webpage_url") ?: eo.str("url"),
                durationSeconds = eo.double("duration")?.toLong(),
            )
        }.orEmpty()
        val formats = (o["formats"] as? JsonArray)?.mapNotNull { f ->
            val fo = f as? JsonObject ?: return@mapNotNull null
            val id = fo.str("format_id") ?: return@mapNotNull null
            // Storyboards and similar are not downloadable media.
            if (fo.str("ext") == "mhtml") return@mapNotNull null
            MediaFormat(
                id = id,
                ext = fo.str("ext") ?: "?",
                note = fo.str("format_note"),
                width = fo.long("width")?.toInt(),
                height = fo.long("height")?.toInt(),
                fps = fo.double("fps"),
                vcodec = fo.str("vcodec"),
                acodec = fo.str("acodec"),
                sizeBytes = fo.long("filesize") ?: fo.long("filesize_approx"),
                tbr = fo.double("tbr"),
            )
        }.orEmpty()
        val thumbnail = o.str("thumbnail")
            ?: (o["thumbnails"] as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }
        return MediaInfo(
            id = o.str("id"),
            title = o.str("title") ?: o.str("fulltitle"),
            uploader = o.str("uploader") ?: o.str("channel") ?: o.str("uploader_id"),
            thumbnail = thumbnail,
            durationSeconds = o.double("duration")?.toLong(),
            webpageUrl = o.str("webpage_url") ?: o.str("original_url"),
            extractor = o.str("extractor_key") ?: o.str("extractor"),
            formats = formats,
            subtitleLanguages = (o["subtitles"] as? JsonObject)?.keys?.toList().orEmpty(),
            autoSubtitleLanguages = (o["automatic_captions"] as? JsonObject)?.keys?.toList().orEmpty(),
            isPlaylist = type == "playlist" || type == "multi_video" || entries.isNotEmpty(),
            entries = entries,
        )
    }

    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.long(key: String): Long? = prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.double(key: String): Double? = prim(key)?.doubleOrNull
}
