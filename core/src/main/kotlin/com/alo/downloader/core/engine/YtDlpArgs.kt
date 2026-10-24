package com.alo.downloader.core.engine

import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.SponsorBlockMode
import com.alo.downloader.core.model.VideoContainer
import com.alo.downloader.core.model.VideoQuality
import java.io.File

/**
 * Builds yt-dlp command line options (without the URL) from [DownloadOptions].
 * Kept free of Android types so the exact flags can be unit tested.
 */
object YtDlpArgs {

    /** ffmpeg filter that crops a thumbnail to a centred square, used for audio cover art. */
    const val SQUARE_THUMBNAIL_PPA =
        "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\""

    fun formatSelector(options: DownloadOptions): String {
        options.formatId?.takeIf { it.isNotBlank() }?.let { return it }
        if (options.audioOnly) return "ba/b"
        return when (val q = options.videoQuality) {
            VideoQuality.BEST -> "bv*+ba/b"
            VideoQuality.WORST -> "wv*+wa/w"
            else -> {
                val h = q.maxHeight
                "bv*[height<=$h]+ba/b[height<=$h]/bv*+ba/b"
            }
        }
    }

    fun formatSort(options: DownloadOptions): String? {
        if (options.audioOnly || !options.formatId.isNullOrBlank()) return null
        return when {
            options.preferAv1 -> "vcodec:av01"
            options.container == VideoContainer.MP4 -> "res,ext:mp4:m4a"
            options.container == VideoContainer.WEBM -> "res,ext:webm:webm"
            else -> null
        }
    }

    /**
     * @param aria2cBinary name of the bundled aria2c executable (youtubedl-android ships it as
     *   `libaria2c.so`), only used when [DownloadOptions.useAria2c] is set.
     * @param aria2cCaCertificate CA bundle for aria2c. yt-dlp keeps only the last
     *   `--external-downloader-args aria2c:…`, so ours must repeat the library's
     *   `--ca-certificate` or aria2c cannot verify any HTTPS server (errorCode=1).
     */
    fun build(
        options: DownloadOptions,
        workDir: File,
        cookieFile: File? = null,
        archiveFile: File? = null,
        aria2cBinary: String = "libaria2c.so",
        aria2cCaCertificate: String? = null,
    ): List<String> {
        val args = mutableListOf<String>()
        fun opt(name: String, value: String? = null) {
            args += name
            if (value != null) args += value
        }

        // Always applied, even with custom commands: where to write and how to authenticate.
        opt("--newline")
        opt("--no-mtime")
        opt("--no-colors")
        opt("-P", workDir.absolutePath)
        opt("-P", "temp:" + File(workDir, ".tmp").absolutePath)
        cookieFile?.let { opt("--cookies", it.absolutePath) }
        if (options.useArchive && archiveFile != null) opt("--download-archive", archiveFile.absolutePath)
        options.proxy?.takeIf { it.isNotBlank() }?.let { opt("--proxy", it) }
        options.rateLimit?.takeIf { it.isNotBlank() }?.let { opt("-r", it) }
        if (options.forceIpv4) opt("--force-ipv4")

        if (options.downloadPlaylist) {
            opt("--yes-playlist")
            options.playlistItems?.takeIf { it.isNotBlank() }?.let { opt("--playlist-items", it) }
        } else {
            opt("--no-playlist")
        }

        val custom = options.customArgsFor(EngineType.YT_DLP)
        if (options.customReplacesDefaults && custom.isNotEmpty()) {
            if (!custom.containsOutputTemplate()) opt("-o", outputTemplate(options))
            args += custom
            return args
        }

        opt("-o", outputTemplate(options))
        if (options.restrictFilenames) opt("--restrict-filenames")
        if (options.useAria2c) {
            opt("--downloader", aria2cBinary)
            // Without a CA path, leave the arguments to the library, whose own include it.
            aria2cCaCertificate?.let { opt("--external-downloader-args", "aria2c:--summary-interval=1 --ca-certificate=${shellQuote(it)}") }
        } else if (options.concurrentFragments > 1) {
            opt("-N", options.concurrentFragments.toString())
        }

        opt("-f", formatSelector(options))
        formatSort(options)?.let { opt("-S", it) }

        if (options.audioOnly) {
            opt("-x")
            if (options.audioFormat != AudioFormat.BEST) opt("--audio-format", options.audioFormat.ytDlpName)
            opt("--audio-quality", "0")
        } else if (options.container != VideoContainer.DEFAULT) {
            opt("--merge-output-format", options.container.ytDlpName)
        }

        if (options.subtitles || options.autoSubtitles) {
            if (options.subtitles) opt("--write-subs")
            if (options.autoSubtitles) opt("--write-auto-subs")
            opt("--sub-langs", options.subtitleLanguages.ifBlank { "all" })
            if (options.embedSubtitles && !options.audioOnly) opt("--embed-subs") else opt("--convert-subs", "srt")
        }

        if (options.embedThumbnail) {
            opt("--embed-thumbnail")
            if (options.audioOnly) {
                opt("--convert-thumbnails", "jpg")
                if (options.cropAudioThumbnail) opt("--ppa", SQUARE_THUMBNAIL_PPA)
            }
        }
        if (options.writeThumbnailFile) opt("--write-thumbnail")
        if (options.embedMetadata) opt("--embed-metadata")
        if (options.embedChapters && !options.audioOnly) opt("--embed-chapters")

        when (options.sponsorBlock) {
            SponsorBlockMode.OFF -> Unit
            SponsorBlockMode.MARK -> opt("--sponsorblock-mark", options.sponsorBlockCategories)
            SponsorBlockMode.REMOVE -> opt("--sponsorblock-remove", options.sponsorBlockCategories)
        }

        args += custom
        return args
    }

    /**
     * Options that make yt-dlp use YouTube clients needing no JS player, so it skips solving
     * YouTube's JS challenge in QuickJS (minutes on a phone). Null when the user's cookies or
     * own extractor arguments must decide the clients; callers fall back to the defaults when
     * these clients fail (e.g. YouTube Kids videos, which android_vr does not return).
     */
    fun fastYoutubeArgs(options: DownloadOptions, hasCookies: Boolean): List<String>? {
        if (hasCookies || options.customArgsFor(EngineType.YT_DLP).any { "extractor-args" in it }) return null
        return listOf("--extractor-args", FAST_YOUTUBE_CLIENTS)
    }

    const val FAST_YOUTUBE_CLIENTS = "youtube:player_client=android_vr,android_sdkless"

    /** Options used to fetch metadata (formats, playlist entries) without downloading. */
    fun infoArgs(options: DownloadOptions, cookieFile: File? = null, flatPlaylist: Boolean = true): List<String> {
        val args = mutableListOf("--dump-single-json", "--no-warnings", "--no-colors")
        if (flatPlaylist) args += "--flat-playlist"
        if (!options.downloadPlaylist) args += "--no-playlist"
        cookieFile?.let { args += listOf("--cookies", it.absolutePath) }
        options.proxy?.takeIf { it.isNotBlank() }?.let { args += listOf("--proxy", it) }
        if (options.forceIpv4) args += "--force-ipv4"
        return args
    }

    private fun outputTemplate(options: DownloadOptions): String {
        val template = options.filenameTemplate.ifBlank { DownloadOptions.DEFAULT_FILENAME_TEMPLATE }
        return if (options.downloadPlaylist && !template.contains("playlist")) {
            "%(playlist_title).80B/%(playlist_index)03d - $template"
        } else template
    }

    private fun shellQuote(value: String) =
        if (value.none { it.isWhitespace() || it in "'\"\\" }) value else "'" + value.replace("'", "'\"'\"'") + "'"

    private fun List<String>.containsOutputTemplate() = any { it == "-o" || it == "--output" }
}
