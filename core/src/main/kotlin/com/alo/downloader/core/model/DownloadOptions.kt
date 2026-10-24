package com.alo.downloader.core.model

enum class AudioFormat(val ytDlpName: String) { BEST("best"), MP3("mp3"), M4A("m4a"), OPUS("opus"), FLAC("flac"), WAV("wav") }

enum class VideoContainer(val ytDlpName: String) { DEFAULT(""), MP4("mp4"), MKV("mkv"), WEBM("webm") }

/** Upper bound for the video height; [BEST] means no limit. */
enum class VideoQuality(val maxHeight: Int?) {
    BEST(null), P2160(2160), P1440(1440), P1080(1080), P720(720), P480(480), P360(360), WORST(0)
}

enum class SponsorBlockMode { OFF, MARK, REMOVE }

/**
 * Everything that influences a single download. Produced from the global settings and
 * optionally tweaked per task in the format sheet.
 */
data class DownloadOptions(
    val audioOnly: Boolean = false,
    val audioFormat: AudioFormat = AudioFormat.BEST,
    val videoQuality: VideoQuality = VideoQuality.BEST,
    val container: VideoContainer = VideoContainer.DEFAULT,
    /** Explicit yt-dlp format id(s), e.g. "137+140"; overrides quality selection. */
    val formatId: String? = null,
    val preferAv1: Boolean = false,
    val subtitles: Boolean = false,
    val autoSubtitles: Boolean = false,
    val subtitleLanguages: String = "en.*,zh.*",
    val embedSubtitles: Boolean = true,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val embedChapters: Boolean = true,
    val writeThumbnailFile: Boolean = false,
    val cropAudioThumbnail: Boolean = true,
    val downloadPlaylist: Boolean = false,
    /** yt-dlp `--playlist-items` spec, e.g. "1,3,5-7". */
    val playlistItems: String? = null,
    val sponsorBlock: SponsorBlockMode = SponsorBlockMode.OFF,
    val sponsorBlockCategories: String = "sponsor,selfpromo,interaction",
    val useArchive: Boolean = true,
    val restrictFilenames: Boolean = false,
    val filenameTemplate: String = DEFAULT_FILENAME_TEMPLATE,
    val rateLimit: String? = null,
    val proxy: String? = null,
    val concurrentFragments: Int = 1,
    val useAria2c: Boolean = false,
    val forceIpv4: Boolean = false,
    /** Delay between requests for gallery-dl, in seconds. Helps against rate limits. */
    val requestSleepSeconds: Double = 0.0,
    /** Raw extra arguments appended to the engine command line (custom templates). */
    val customArgs: List<String> = emptyList(),
    /** When true, [customArgs] replace the generated format/post-processing options. */
    val customReplacesDefaults: Boolean = false,
    /** Engine the custom arguments are written for; other engines ignore them. Null = yt-dlp. */
    val customArgsEngine: EngineType? = null,
) {
    fun customArgsFor(engine: EngineType): List<String> =
        if ((customArgsEngine ?: EngineType.YT_DLP) == engine) customArgs else emptyList()

    companion object {
        const val DEFAULT_FILENAME_TEMPLATE = "%(title).150B [%(id)s].%(ext)s"
    }
}
