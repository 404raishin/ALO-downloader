package com.alo.downloader.core.media

object FormatSelection {
    /**
     * Builds a yt-dlp `-f` value from the formats picked in the UI. Video-only streams are
     * merged with the chosen (or best) audio; muxed streams are used as they are.
     */
    fun combine(video: MediaFormat?, audio: MediaFormat?, audioOnly: Boolean): String? = when {
        audioOnly -> audio?.id
        video == null -> audio?.let { "bv*+${it.id}/b" }
        video.hasAudio -> video.id
        audio != null -> "${video.id}+${audio.id}"
        else -> "${video.id}+ba/${video.id}"
    }

    /** Distinct heights for quick quality chips, highest first. */
    fun heights(info: MediaInfo): List<Int> =
        info.formats.mapNotNull { f -> f.height?.takeIf { f.hasVideo && it > 0 } }.distinct().sortedDescending()
}

object PlaylistItems {
    /** `[1,2,3,5,7,8]` → `"1-3,5,7-8"` (yt-dlp `--playlist-items`). */
    fun compress(indices: Collection<Int>): String {
        val sorted = indices.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var prev = start
        for (i in sorted.drop(1) + Int.MIN_VALUE) {
            if (i == prev + 1) { prev = i; continue }
            parts += if (start == prev) "$start" else "$start-$prev"
            start = i
            prev = i
        }
        return parts.joinToString(",")
    }

    fun parse(spec: String): Set<Int> = spec.split(',').flatMap { part ->
        val p = part.trim()
        when {
            p.isEmpty() -> emptyList()
            '-' in p -> {
                val (a, b) = p.split('-', limit = 2).map { it.trim().toIntOrNull() }
                if (a != null && b != null && a <= b) (a..b).toList() else emptyList()
            }
            else -> listOfNotNull(p.toIntOrNull())
        }
    }.toSet()
}
