package com.alo.downloader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alo.downloader.core.model.DownloadStatus
import com.alo.downloader.core.model.FailureReason

@Entity(tableName = "tasks", indices = [Index("status"), Index("createdAt")])
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    /** [com.alo.downloader.core.model.Platform.id] */
    val platform: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** Engine currently (or last) working on the task. */
    val engine: String? = null,
    /** Engine forced by the user; null lets the router decide. */
    val forcedEngine: String? = null,
    /** JSON from [com.alo.downloader.core.model.DownloadOptionsCodec]. */
    val optionsJson: String,
    /** 0..100, negative while indeterminate. */
    val progress: Float = -1f,
    val speed: String? = null,
    val etaSeconds: Long? = null,
    val filesDone: Int = 0,
    val errorReason: FailureReason? = null,
    val errorMessage: String? = null,
    /** Human readable list of attempts, e.g. "yt-dlp ✗ → gallery-dl ✓". */
    val attempts: String? = null,
    val alreadyDownloaded: Boolean = false,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val scheduledAt: Long? = null,
    /** Engine expected to run first, used for exclusive scheduling. */
    val plannedEngine: String? = null,
)

@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId"), Index("createdAt")],
)
data class DownloadedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    /** content:// (MediaStore / SAF) or file:// uri. */
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val platform: String,
    /** e.g. "Download/ALO/Instagram" */
    @ColumnInfo(defaultValue = "") val location: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "command_templates")
data class CommandTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.alo.downloader.core.model.EngineType.id] the arguments are meant for. */
    val engine: String,
    val arguments: String,
    /** When true the template replaces ALO's generated format options. */
    val replaceDefaults: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Task joined with its first file, for list rows. */
data class TaskWithFiles(
    @androidx.room.Embedded val task: DownloadTaskEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "taskId")
    val files: List<DownloadedFileEntity>,
)
