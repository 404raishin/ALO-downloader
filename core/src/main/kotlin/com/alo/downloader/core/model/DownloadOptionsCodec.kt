package com.alo.downloader.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Stores [DownloadOptions] as JSON in the database. Hand written (no compiler plugin) and
 * tolerant: unknown keys are ignored and missing keys fall back to defaults, so options
 * saved by older app versions keep working.
 */
object DownloadOptionsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(o: DownloadOptions): String = buildJsonObject {
        put("audioOnly", o.audioOnly)
        put("audioFormat", o.audioFormat.name)
        put("videoQuality", o.videoQuality.name)
        put("container", o.container.name)
        o.formatId?.let { put("formatId", it) }
        put("preferAv1", o.preferAv1)
        put("subtitles", o.subtitles)
        put("autoSubtitles", o.autoSubtitles)
        put("subtitleLanguages", o.subtitleLanguages)
        put("embedSubtitles", o.embedSubtitles)
        put("embedThumbnail", o.embedThumbnail)
        put("embedMetadata", o.embedMetadata)
        put("embedChapters", o.embedChapters)
        put("writeThumbnailFile", o.writeThumbnailFile)
        put("cropAudioThumbnail", o.cropAudioThumbnail)
        put("downloadPlaylist", o.downloadPlaylist)
        o.playlistItems?.let { put("playlistItems", it) }
        put("sponsorBlock", o.sponsorBlock.name)
        put("sponsorBlockCategories", o.sponsorBlockCategories)
        put("useArchive", o.useArchive)
        put("restrictFilenames", o.restrictFilenames)
        put("filenameTemplate", o.filenameTemplate)
        o.rateLimit?.let { put("rateLimit", it) }
        o.proxy?.let { put("proxy", it) }
        put("concurrentFragments", o.concurrentFragments)
        put("useAria2c", o.useAria2c)
        put("forceIpv4", o.forceIpv4)
        put("requestSleepSeconds", o.requestSleepSeconds)
        put("customArgs", JsonArray(o.customArgs.map { JsonPrimitive(it) }))
        put("customReplacesDefaults", o.customReplacesDefaults)
        o.customArgsEngine?.let { put("customArgsEngine", it.id) }
    }.toString()

    fun decode(text: String?): DownloadOptions {
        if (text.isNullOrBlank()) return DownloadOptions()
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return DownloadOptions()
        val d = DownloadOptions()
        return DownloadOptions(
            audioOnly = obj.bool("audioOnly") ?: d.audioOnly,
            audioFormat = obj.enum("audioFormat", d.audioFormat),
            videoQuality = obj.enum("videoQuality", d.videoQuality),
            container = obj.enum("container", d.container),
            formatId = obj.str("formatId"),
            preferAv1 = obj.bool("preferAv1") ?: d.preferAv1,
            subtitles = obj.bool("subtitles") ?: d.subtitles,
            autoSubtitles = obj.bool("autoSubtitles") ?: d.autoSubtitles,
            subtitleLanguages = obj.str("subtitleLanguages") ?: d.subtitleLanguages,
            embedSubtitles = obj.bool("embedSubtitles") ?: d.embedSubtitles,
            embedThumbnail = obj.bool("embedThumbnail") ?: d.embedThumbnail,
            embedMetadata = obj.bool("embedMetadata") ?: d.embedMetadata,
            embedChapters = obj.bool("embedChapters") ?: d.embedChapters,
            writeThumbnailFile = obj.bool("writeThumbnailFile") ?: d.writeThumbnailFile,
            cropAudioThumbnail = obj.bool("cropAudioThumbnail") ?: d.cropAudioThumbnail,
            downloadPlaylist = obj.bool("downloadPlaylist") ?: d.downloadPlaylist,
            playlistItems = obj.str("playlistItems"),
            sponsorBlock = obj.enum("sponsorBlock", d.sponsorBlock),
            sponsorBlockCategories = obj.str("sponsorBlockCategories") ?: d.sponsorBlockCategories,
            useArchive = obj.bool("useArchive") ?: d.useArchive,
            restrictFilenames = obj.bool("restrictFilenames") ?: d.restrictFilenames,
            filenameTemplate = obj.str("filenameTemplate") ?: d.filenameTemplate,
            rateLimit = obj.str("rateLimit"),
            proxy = obj.str("proxy"),
            concurrentFragments = obj["concurrentFragments"]?.jsonPrimitive?.intOrNull ?: d.concurrentFragments,
            useAria2c = obj.bool("useAria2c") ?: d.useAria2c,
            forceIpv4 = obj.bool("forceIpv4") ?: d.forceIpv4,
            requestSleepSeconds = obj["requestSleepSeconds"]?.jsonPrimitive?.doubleOrNull ?: d.requestSleepSeconds,
            customArgs = runCatching { obj["customArgs"]?.jsonArray?.map { it.jsonPrimitive.content } }.getOrNull()
                ?: d.customArgs,
            customReplacesDefaults = obj.bool("customReplacesDefaults") ?: d.customReplacesDefaults,
            customArgsEngine = EngineType.fromId(obj.str("customArgsEngine")),
        )
    }

    private fun JsonObject.bool(key: String): Boolean? = runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()
    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.takeIf { it.isString }?.content }.getOrNull()

    private inline fun <reified E : Enum<E>> JsonObject.enum(key: String, default: E): E {
        val name = str(key) ?: return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
