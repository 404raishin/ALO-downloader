package com.alo.downloader.core.engine

import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.DownloadOptions
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
     */
    fun build(
        options: DownloadOptions,
        workDir: File,
        cookieFile: File? = null,
        archiveFile: File? = null,
        aria2cBinary: String = "libaria2c.so",
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

        if (options.customReplacesDefaults && options.customArgs.isNotEmpty()) {
            if (!options.customArgs.containsOutputTemplate()) opt("-o", outputTemplate(options))
            args += options.customArgs
            return args
        }

        opt("-o", outputTemplate(options))
        if (options.restrictFilenames) opt("--restrict-filenames")
        if (options.useAria2c) {
            opt("--downloader", aria2cBinary)
            opt("--external-downloader-args", "aria2c:--summary-interval=1")
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

        args += options.customArgs
        return args
    }

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

    private fun List<String>.containsOutputTemplate() = any { it == "-o" || it == "--output" }
}
