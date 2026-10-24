package com.alo.downloader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alo.downloader.core.link.LinkExtractor
import com.alo.downloader.data.db.TaskDao
import com.alo.downloader.data.db.TaskWithFiles
import com.alo.downloader.data.settings.AppSettings
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    taskDao: TaskDao,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val queue: StateFlow<List<TaskWithFiles>> = taskDao.observeUnfinished()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** Per-session override of the "audio only" default. */
    private val _audioOnly = MutableStateFlow<Boolean?>(null)
    val audioOnly: StateFlow<Boolean?> = _audioOnly.asStateFlow()

    /** Link found in the clipboard that the user has not acted on yet. */
    private val _clipboardLink = MutableStateFlow<String?>(null)
    val clipboardLink: StateFlow<String?> = _clipboardLink.asStateFlow()
    private val seenClipboard = mutableSetOf<String>()

    fun onClipboard(text: String?) {
        if (!settings.value.clipboardDetection) return
        val link = LinkExtractor.extractFirst(text) ?: return
        if (link in seenClipboard || link == LinkExtractor.extractFirst(_input.value)) return
        _clipboardLink.value = link
    }

    fun dismissClipboard() {
        _clipboardLink.value?.let(seenClipboard::add)
        _clipboardLink.value = null
    }

    fun downloadClipboard(onQueued: (Int) -> Unit) {
        val link = _clipboardLink.value ?: return
        dismissClipboard()
        viewModelScope.launch {
            val ids = downloadManager.enqueue(listOf(DownloadManager.Request(link, options = downloadManager.defaultOptions())))
            onQueued(ids.size)
        }
    }

    fun setInput(text: String) { _input.value = text }

    fun toggleAudioOnly() { _audioOnly.value = !(_audioOnly.value ?: settings.value.audioOnly) }

    /** Queues every link in the input box; returns how many were queued. */
    fun downloadInput(onQueued: (Int) -> Unit) {
        val links = LinkExtractor.extractAll(_input.value)
        if (links.isEmpty()) { onQueued(0); return }
        viewModelScope.launch {
            val options = downloadManager.defaultOptions().let { o ->
                _audioOnly.value?.let { o.copy(audioOnly = it) } ?: o
            }
            val ids = downloadManager.enqueue(links.map { DownloadManager.Request(it, options = options) })
            _input.value = ""
            onQueued(ids.size)
        }
    }

    fun pause(id: String) = viewModelScope.launch { downloadManager.pause(id) }
    fun resume(id: String) = viewModelScope.launch { downloadManager.resume(id) }
    fun cancel(id: String) = viewModelScope.launch { downloadManager.cancel(id) }
    fun retry(id: String) = viewModelScope.launch { downloadManager.retry(id) }
    fun delete(id: String) = viewModelScope.launch { downloadManager.delete(id, deleteFiles = false) }
    fun clearFinished() = viewModelScope.launch { downloadManager.clearFinished() }
}
