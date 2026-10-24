package com.alo.downloader.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.alo.downloader.core.model.Platform
import com.alo.downloader.core.util.FileNames
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class StoredFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Human readable location, e.g. "Download/ALO/Instagram". */
    val location: String,
)

/**
 * Moves finished files out of the private work directory into user visible storage:
 * `Download/ALO/<Platform>/` through MediaStore (Android 10+), the legacy public directory
 * (Android 7–9), or a folder the user picked with the Storage Access Framework.
 */
@Singleton
class OutputStorage @Inject constructor(@ApplicationContext private val context: Context) {

    fun folderFor(platform: Platform, perPlatform: Boolean): String =
        if (perPlatform) "$ROOT/${platform.folderName}" else ROOT

    suspend fun publish(
        files: List<File>,
        platform: Platform,
        perPlatform: Boolean,
        treeUri: String?,
        workDir: File,
    ): List<StoredFile> = withContext(Dispatchers.IO) {
        files.map { file ->
            // Keep sub folders created by the engine (playlists, gallery albums).
            val subDir = file.parentFile?.relativeTo(workDir)?.invariantSeparatorsPath
                ?.takeIf { it.isNotEmpty() && it != "." }
                ?.split('/')?.joinToString("/") { FileNames.sanitize(it, 80) }
            when {
                treeUri != null -> publishToTree(file, treeUri.toUri(), platform, perPlatform, subDir)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> publishMediaStore(file, platform, perPlatform, subDir)
                else -> publishLegacy(file, platform, perPlatform, subDir)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishMediaStore(file: File, platform: Platform, perPlatform: Boolean, subDir: String?): StoredFile {
        val name = FileNames.sanitize(file.name)
        val mime = FileNames.mimeType(name)
        val relative = listOfNotNull(Environment.DIRECTORY_DOWNLOADS, folderFor(platform, perPlatform), subDir)
            .joinToString("/") + "/"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed for $name")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Cannot open $uri")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        val size = file.length()
        file.delete()
        return StoredFile(uri, name, mime, size, relative.trimEnd('/'))
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(file: File, platform: Platform, perPlatform: Boolean, subDir: String?): StoredFile {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(base, listOfNotNull(folderFor(platform, perPlatform), subDir).joinToString("/")).apply { mkdirs() }
        val target = uniqueFile(dir, FileNames.sanitize(file.name))
        if (!file.renameTo(target)) {
            file.copyTo(target, overwrite = false)
            file.delete()
        }
        val mime = FileNames.mimeType(target.name)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        return StoredFile(
            Uri.fromFile(target), target.name, mime, target.length(),
            "${Environment.DIRECTORY_DOWNLOADS}/${target.parentFile?.relativeTo(base)?.path ?: ROOT}",
        )
    }

    private fun publishToTree(file: File, tree: Uri, platform: Platform, perPlatform: Boolean, subDir: String?): StoredFile {
        var dir = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Folder is no longer accessible")
        val segments = buildList {
            if (perPlatform) add(platform.folderName)
            subDir?.split('/')?.let { addAll(it) }
        }
        for (segment in segments) {
            dir = dir.findFile(segment)?.takeIf { it.isDirectory } ?: dir.createDirectory(segment)
                ?: throw IOException("Cannot create folder $segment")
        }
        val name = FileNames.sanitize(file.name)
        val mime = FileNames.mimeType(name)
        val doc = dir.createFile(mime, name) ?: throw IOException("Cannot create $name")
        context.contentResolver.openOutputStream(doc.uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IOException("Cannot write $name")
        val size = file.length()
        file.delete()
        return StoredFile(doc.uri, doc.name ?: name, mime, size, dir.name.orEmpty())
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (candidate.exists()) candidate = File(dir, "$base ($i)$ext").also { i++ }
        return candidate
    }

    /** Private scratch space for one task; partial files survive pause/resume. */
    fun workDir(taskId: String): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "work/$taskId")

    fun archiveFile(name: String): File = File(context.filesDir, "archive/$name").apply { parentFile?.mkdirs() }

    companion object {
        const val ROOT = "ALO"
    }
}
