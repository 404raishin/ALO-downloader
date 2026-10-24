package com.alo.downloader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alo.downloader.core.model.Platform
import com.alo.downloader.data.db.TaskDao
import com.alo.downloader.data.db.TaskWithFiles
import com.alo.downloader.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MediaFilter { ALL, VIDEO, AUDIO, IMAGE }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    taskDao: TaskDao,
    private val downloadManager: DownloadManager,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(MediaFilter.ALL)
    val platform = MutableStateFlow<Platform?>(null)

    private val all = taskDao.observeCompleted()

    val platforms: StateFlow<List<Platform>> = all.combine(query) { list, _ ->
        list.map { Platform.fromId(it.task.platform) }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<TaskWithFiles>> = combine(all, query, filter, platform) { list, q, f, p ->
        list.filter { item ->
            (p == null || item.task.platform == p.id) &&
                (q.isBlank() || (item.task.title ?: "").contains(q, true) || item.task.url.contains(q, true) ||
                    item.files.any { it.displayName.contains(q, true) }) &&
                when (f) {
                    MediaFilter.ALL -> true
                    MediaFilter.VIDEO -> item.files.any { it.mimeType.startsWith("video/") }
                    MediaFilter.AUDIO -> item.files.any { it.mimeType.startsWith("audio/") }
                    MediaFilter.IMAGE -> item.files.any { it.mimeType.startsWith("image/") }
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String, deleteFiles: Boolean) = viewModelScope.launch { downloadManager.delete(id, deleteFiles) }
    fun redownload(id: String) = viewModelScope.launch { downloadManager.retry(id) }
    fun clearHistory() = viewModelScope.launch { downloadManager.clearFinished() }
}
