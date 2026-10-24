package com.alo.downloader.share

import android.content.Intent
import android.os.Build

/** Pulls the shared text out of SEND / SEND_MULTIPLE / PROCESS_TEXT / VIEW intents. */
object SharedText {
    fun from(intent: Intent?): String? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.contains("http") },
            ).joinToString("\n").ifBlank { null }
            Intent.ACTION_SEND_MULTIPLE -> {
                val texts = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                }
                texts?.joinToString("\n") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_VIEW -> intent.dataString
            else -> intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}
