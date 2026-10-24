package com.alo.downloader.core.engine

/** What yt-dlp is doing, read from its output lines. */
enum class EngineStage {
    /** Fetching the page, API responses or the player. */
    EXTRACTING,

    /** Running YouTube's JS challenges through the bundled JS runtime; slow on phones. */
    SOLVING_CHALLENGES,
    DOWNLOADING,
    MERGING,
    POST_PROCESSING,
}

/** Reads progress and the current stage from yt-dlp's `--newline` output. */
object YtDlpOutput {
    private val PERCENT = Regex("""^\[download]\s+(\d+(?:\.\d+)?)%""")
    private val TAG = Regex("""^\[([A-Za-z0-9_:+-]+)]""")
    private val POST_PROCESSORS = setOf(
        "ExtractAudio", "EmbedThumbnail", "EmbedSubtitle", "Metadata", "ThumbnailsConvertor",
        "SubtitlesConvertor", "VideoConvertor", "VideoRemuxer", "FixupM3u8", "FixupM4a",
        "FixupStretched", "FixupTimestamp", "FixupDuration", "FixupDuplicateMoov", "SponsorBlock",
        "ModifyChapters", "SplitChapters", "MoveFiles", "Exec",
    )

    /**
     * Download percentage of a `[download]` line. youtubedl-android only reports one when the
     * line also has an `ETA mm:ss`, so lines with `ETA Unknown` (common for fragmented
     * YouTube formats) would otherwise leave the bar indeterminate.
     */
    fun percent(line: String): Float? =
        PERCENT.find(line.trim())?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0f, 100f)

    fun stage(line: String): EngineStage? {
        val text = line.trim()
        if ("Solving JS challenges" in text) return EngineStage.SOLVING_CHALLENGES
        val tag = TAG.find(text)?.groupValues?.get(1) ?: return null
        return when {
            tag == "download" -> EngineStage.DOWNLOADING
            tag == "Merger" -> EngineStage.MERGING
            tag in POST_PROCESSORS -> EngineStage.POST_PROCESSING
            tag == "info" || tag == "debug" -> null
            else -> EngineStage.EXTRACTING
        }
    }
}
