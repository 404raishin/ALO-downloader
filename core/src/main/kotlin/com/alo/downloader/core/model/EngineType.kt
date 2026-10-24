package com.alo.downloader.core.model

enum class EngineType(val id: String, val displayName: String) {
    YT_DLP("yt-dlp", "yt-dlp"),
    GALLERY_DL("gallery-dl", "gallery-dl"),
    TELEGRAM("telegram", "Telegram");

    companion object {
        fun fromId(id: String?): EngineType? = entries.firstOrNull { it.id == id }
    }
}
