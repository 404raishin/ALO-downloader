package com.alo.downloader.download

import com.alo.downloader.core.engine.EngineStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What each running task's engine is doing right now (stage and last output line). Kept in
 * memory only: it means nothing once the attempt has ended.
 */
@Singleton
class LiveStatus @Inject constructor() {
    data class Entry(val stage: EngineStage?, val line: String?)

    private val _entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val entries: StateFlow<Map<String, Entry>> = _entries.asStateFlow()

    fun set(taskId: String, entry: Entry) = _entries.update { it + (taskId to entry) }

    fun clear(taskId: String) = _entries.update { it - taskId }
}
