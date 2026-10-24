package com.alo.downloader.core.util

object FileNames {
    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

    /** Makes [name] safe for FAT/ext4/MediaStore while keeping the extension. */
    fun sanitize(name: String, maxLength: Int = 180): String {
        val cleaned = name.replace(illegal, "_").trim().trim('.').ifBlank { "download" }
        if (cleaned.length <= maxLength) return cleaned
        val dot = cleaned.lastIndexOf('.')
        val ext = if (dot > 0 && cleaned.length - dot <= 10) cleaned.substring(dot) else ""
        return cleaned.take(maxLength - ext.length) + ext
    }

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun mimeType(name: String): String = when (extension(name)) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "opus", "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "avif" -> "image/avif"
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "zip" -> "application/zip"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var i = 0
        while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
        return String.format(java.util.Locale.US, "%.1f %s", value, units[i])
    }
}
