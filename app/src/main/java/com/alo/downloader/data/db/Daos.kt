package com.alo.downloader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.alo.downloader.core.model.DownloadStatus
import com.alo.downloader.core.model.FailureReason
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<DownloadTaskEntity>)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<DownloadTaskEntity?>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAllWithFiles(): Flow<List<TaskWithFiles>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE status NOT IN ('COMPLETED') ORDER BY createdAt DESC")
    fun observeUnfinished(): Flow<List<TaskWithFiles>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY finishedAt DESC")
    fun observeCompleted(): Flow<List<TaskWithFiles>>

    @Query("SELECT * FROM tasks WHERE status IN (:statuses)")
    suspend fun withStatus(statuses: List<DownloadStatus>): List<DownloadTaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE status IN ('QUEUED','PREPARING','RUNNING')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE status IN ('QUEUED','PREPARING','RUNNING')")
    suspend fun pendingCount(): Int

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: DownloadStatus)

    @Query(
        "UPDATE tasks SET progress = :progress, speed = :speed, etaSeconds = :eta, filesDone = :filesDone, " +
            "title = COALESCE(:title, title) WHERE id = :id",
    )
    suspend fun setProgress(id: String, progress: Float, speed: String?, eta: Long?, filesDone: Int, title: String?)

    @Query("UPDATE tasks SET status = 'FAILED', errorReason = :reason, errorMessage = :message, finishedAt = :at WHERE id = :id")
    suspend fun setFailed(id: String, reason: FailureReason, message: String, at: Long)

    @Query(
        "UPDATE tasks SET status = 'QUEUED', errorReason = NULL, errorMessage = NULL, progress = -1, " +
            "speed = NULL, etaSeconds = NULL, finishedAt = NULL WHERE id = :id",
    )
    suspend fun requeue(id: String)

    /** Tasks left RUNNING by a killed process go back to the queue on next start. */
    @Query("UPDATE tasks SET status = 'QUEUED' WHERE status IN ('PREPARING','RUNNING')")
    suspend fun resetInterrupted(): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks WHERE status IN ('COMPLETED','FAILED','CANCELED')")
    suspend fun clearFinished()

    @Query("SELECT COUNT(*) FROM tasks WHERE url = :url AND status = 'COMPLETED'")
    suspend fun completedCount(url: String): Int
}

@Dao
interface FileDao {
    @Insert
    suspend fun insertAll(files: List<DownloadedFileEntity>)

    @Query("SELECT * FROM files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadedFileEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun get(id: Long): DownloadedFileEntity?

    @Query("SELECT * FROM files WHERE taskId = :taskId")
    suspend fun forTask(taskId: String): List<DownloadedFileEntity>

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM command_templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CommandTemplateEntity>>

    @Query("SELECT * FROM command_templates WHERE id = :id")
    suspend fun get(id: Long): CommandTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: CommandTemplateEntity): Long

    @Query("DELETE FROM command_templates WHERE id = :id")
    suspend fun delete(id: Long)
}
