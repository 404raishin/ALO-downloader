package com.alo.downloader.core.engine

import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.EngineType
import java.io.File

/** Builds gallery-dl command line arguments (URL last) from [DownloadOptions]. */
object GalleryDlArgs {
    fun build(
        url: String,
        options: DownloadOptions,
        workDir: File,
        cookieFile: File? = null,
        archiveFile: File? = null,
    ): List<String> {
        val args = mutableListOf<String>()
        fun opt(name: String, value: String? = null) {
            args += name
            if (value != null) args += value
        }
        // Write straight into the task directory; ALO sorts files into platform folders.
        opt("--directory", workDir.absolutePath)
        // One finished path per stdout line, "# path" for skipped files.
        opt("-o", "output.mode=pipe")
        opt("-o", "output.progress=false")
        opt("-o", "output.shorten=false")
        // gallery-dl sleeps 60 s and retries four times on HTTP 429; fail at once instead so the
        // task reports "rate limited" (and offers a login) rather than sitting still for minutes.
        opt("-o", "sleep-429=0")
        cookieFile?.let { opt("--cookies", it.absolutePath) }
        if (options.useArchive && archiveFile != null) opt("--download-archive", archiveFile.absolutePath)
        options.proxy?.takeIf { it.isNotBlank() }?.let { opt("--proxy", it) }
        options.rateLimit?.takeIf { it.isNotBlank() }?.let { opt("--limit-rate", it) }
        if (options.requestSleepSeconds > 0) opt("--sleep-request", trim(options.requestSleepSeconds))
        if (options.forceIpv4) opt("--force-ipv4")
        if (options.downloadPlaylist) options.playlistItems?.takeIf { it.isNotBlank() }?.let { opt("--range", it) }
        args += options.customArgsFor(EngineType.GALLERY_DL)
        args += url
        return args
    }

    private fun trim(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}

/** Interprets gallery-dl's pipe-mode output lines. */
object GalleryDlOutput {
    sealed interface Line {
        data class Downloaded(val path: String) : Line
        data class Skipped(val path: String) : Line
        data class Log(val text: String) : Line
    }

    fun parse(stream: String, line: String): Line {
        val text = line.trimEnd()
        if (stream == "out") {
            if (text.startsWith("# ")) return Line.Skipped(text.removePrefix("# "))
            if (text.startsWith("/")) return Line.Downloaded(text)
        }
        return Line.Log(text)
    }
}
