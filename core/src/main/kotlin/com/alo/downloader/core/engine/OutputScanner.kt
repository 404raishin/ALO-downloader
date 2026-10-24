package com.alo.downloader.core.engine

import java.io.File

/**
 * Finds the finished media files an engine left in its work directory. Every engine writes
 * into an empty per-task directory, so whatever complete file is in there is a result.
 */
object OutputScanner {
    private val partialSuffixes = listOf(
        ".part", ".ytdl", ".temp", ".tmp", ".aria2", ".frag", ".download",
    )
    private val sidecarSuffixes = listOf(".json", ".description", ".sqlite3", ".txt")

    fun isPartial(file: File): Boolean {
        val name = file.name.lowercase()
        return partialSuffixes.any { name.endsWith(it) } ||
            Regex("""\.part-frag\d+$""").containsMatchIn(name) ||
            Regex("""\.f\d+\.\w+\.part$""").containsMatchIn(name) ||
            name.startsWith(".")
    }

    fun scan(dir: File, includeSidecars: Boolean = false): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.length() > 0 && !isPartial(it) && !it.isInHiddenDir(dir) }
            .filter { includeSidecars || sidecarSuffixes.none { s -> it.name.lowercase().endsWith(s) } }
            .sortedBy { it.lastModified() }
            .toList()
    }

    private fun File.isInHiddenDir(root: File): Boolean =
        relativeTo(root).invariantSeparatorsPath.split('/').dropLast(1).any { it.startsWith(".") }
}
