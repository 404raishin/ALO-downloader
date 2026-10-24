package com.alo.downloader.ui.configure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alo.downloader.core.link.LinkExtractor
import com.alo.downloader.core.media.FormatSelection
import com.alo.downloader.core.media.MediaFormat
import com.alo.downloader.core.media.MediaInfo
import com.alo.downloader.core.media.PlaylistItems
import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.VideoQuality
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.download.DownloadManager
import com.alo.downloader.engine.ytdlp.MediaInfoException
import com.alo.downloader.engine.ytdlp.MediaInfoFetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfigureState(
    val urls: List<String> = emptyList(),
    val loading: Boolean = false,
    val info: MediaInfo? = null,
    val infoError: DownloadError? = null,
    val audioOnly: Boolean = false,
    val quality: VideoQuality = VideoQuality.BEST,
    val audioFormat: AudioFormat = AudioFormat.BEST,
    val videoFormat: MediaFormat? = null,
    val audioStream: MediaFormat? = null,
    val subtitles: Boolean = false,
    val playlistSelection: Set<Int> = emptySet(),
    val engine: EngineType? = null,
    val scheduleAt: Long? = null,
    val useTemplate: Boolean = false,
    val templateAvailable: Boolean = false,
)

@HiltViewModel
class ConfigureViewModel @Inject constructor(
    private val fetcher: MediaInfoFetcher,
    private val settingsRepository: SettingsRepository,
    private val downloadManager: DownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow(ConfigureState())
    val state: StateFlow<ConfigureState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun load(text: String) {
        val urls = LinkExtractor.extractAll(text)
        if (urls == _state.value.urls) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = settingsRepository.current()
            val template = downloadManager.activeTemplate()
            _state.value = ConfigureState(
                urls = urls,
                loading = urls.isNotEmpty(),
                audioOnly = s.audioOnly,
                quality = s.videoQuality,
                audioFormat = s.audioFormat,
                subtitles = s.subtitles,
                useTemplate = template != null,
                templateAvailable = template != null,
            )
            val first = urls.firstOrNull() ?: return@launch
            val result = fetcher.fetch(first, s.toDownloadOptions().copy(downloadPlaylist = true))
            _state.update { st ->
                result.fold(
                    onSuccess = { info ->
                        st.copy(
                            loading = false,
                            info = info,
                            playlistSelection = if (info.isPlaylist) info.entries.map { it.index }.toSet() else emptySet(),
                        )
                    },
                    onFailure = { st.copy(loading = false, infoError = (it as? MediaInfoException)?.error) },
                )
            }
        }
    }

    fun setAudioOnly(value: Boolean) = _state.update { it.copy(audioOnly = value) }
    fun setQuality(q: VideoQuality) = _state.update { it.copy(quality = q, videoFormat = null) }
    fun setAudioFormat(f: AudioFormat) = _state.update { it.copy(audioFormat = f) }
    fun selectVideoFormat(f: MediaFormat?) = _state.update { it.copy(videoFormat = f) }
    fun selectAudioStream(f: MediaFormat?) = _state.update { it.copy(audioStream = f) }
    fun setSubtitles(v: Boolean) = _state.update { it.copy(subtitles = v) }
    fun setEngine(e: EngineType?) = _state.update { it.copy(engine = e) }
    fun setSchedule(at: Long?) = _state.update { it.copy(scheduleAt = at) }
    fun setUseTemplate(v: Boolean) = _state.update { it.copy(useTemplate = v) }

    fun togglePlaylistItem(index: Int) = _state.update {
        val sel = it.playlistSelection
        it.copy(playlistSelection = if (index in sel) sel - index else sel + index)
    }

    fun selectAllPlaylist(all: Boolean) = _state.update { st ->
        st.copy(playlistSelection = if (all) st.info?.entries?.map { it.index }?.toSet().orEmpty() else emptySet())
    }

    fun download(onQueued: (Int) -> Unit) {
        val st = _state.value
        if (st.urls.isEmpty()) return
        viewModelScope.launch {
            val base = settingsRepository.current().toDownloadOptions()
            val playlist = st.info?.isPlaylist == true
            val single = st.urls.size == 1
            val template = if (st.useTemplate) downloadManager.activeTemplate() else null
            val options = base.copy(
                audioOnly = st.audioOnly,
                videoQuality = st.quality,
                audioFormat = st.audioFormat,
                formatId = if (single && !playlist) FormatSelection.combine(st.videoFormat, st.audioStream, st.audioOnly) else null,
                subtitles = st.subtitles,
                downloadPlaylist = playlist,
                playlistItems = if (playlist) PlaylistItems.compress(st.playlistSelection) else null,
                customArgs = template?.args.orEmpty(),
                customReplacesDefaults = template?.replaceDefaults ?: false,
                customArgsEngine = template?.engine,
            )
            val ids = downloadManager.enqueue(
                st.urls.map {
                    DownloadManager.Request(
                        url = it,
                        options = options,
                        title = if (single) st.info?.title else null,
                        thumbnailUrl = if (single) st.info?.thumbnail else null,
                        forcedEngine = st.engine,
                        scheduledAt = st.scheduleAt,
                        priority = 1,
                    )
                },
            )
            onQueued(ids.size)
        }
    }
}
