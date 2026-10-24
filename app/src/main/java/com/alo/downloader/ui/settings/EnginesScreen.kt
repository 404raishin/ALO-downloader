package com.alo.downloader.ui.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alo.downloader.R
import com.alo.downloader.data.settings.AppSettings
import com.alo.downloader.data.settings.SettingKeys
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.data.settings.UpdateChannel
import com.alo.downloader.update.EngineUpdateWorker
import com.alo.downloader.update.EngineUpdater
import com.alo.downloader.update.EngineVersions
import com.alo.downloader.update.UpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class EnginesState(
    val versions: EngineVersions? = null,
    val updatingYtDlp: Boolean = false,
    val updatingGalleryDl: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class EnginesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updater: EngineUpdater,
    private val repository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EnginesState())
    val state: StateFlow<EnginesState> = _state.asStateFlow()
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init { refresh() }

    private fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(versions = updater.versions())
    }

    fun updateYtDlp() = viewModelScope.launch {
        _state.value = _state.value.copy(updatingYtDlp = true, message = null)
        val result = runCatching { updater.updateYtDlp(settings.value.updateChannel) }
        _state.value = _state.value.copy(updatingYtDlp = false, message = describe("yt-dlp", result))
        refresh()
    }

    fun updateGalleryDl() = viewModelScope.launch {
        _state.value = _state.value.copy(updatingGalleryDl = true, message = null)
        val result = runCatching { updater.updateGalleryDl() }
        _state.value = _state.value.copy(updatingGalleryDl = false, message = describe("gallery-dl", result))
        refresh()
    }

    fun resetGalleryDl() = viewModelScope.launch {
        updater.resetGalleryDl()
        refresh()
    }

    fun setChannel(channel: UpdateChannel) = viewModelScope.launch { repository.setEnum(SettingKeys.updateChannel, channel) }

    fun setAutoUpdate(enabled: Boolean) = viewModelScope.launch {
        repository.set(SettingKeys.autoUpdateEngines, enabled)
        EngineUpdateWorker.schedule(context, enabled)
    }

    private fun describe(name: String, result: Result<UpdateResult>): String = result.fold(
        onSuccess = {
            when (it) {
                is UpdateResult.Updated -> context.getString(R.string.engine_updated, name, it.version)
                is UpdateResult.UpToDate -> context.getString(R.string.engine_up_to_date, name, it.version ?: "?")
            }
        },
        onFailure = { context.getString(R.string.engine_update_failed, name, it.message ?: it.toString()) },
    )
}

@Composable
fun EnginesScreen(onBack: () -> Unit, viewModel: EnginesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val loading = stringResource(R.string.loading)
    SettingsPage(title = stringResource(R.string.settings_engines), onBack = onBack) {
        Text(
            stringResource(R.string.engines_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        PreferenceItem(
            title = "yt-dlp",
            summary = state.versions?.ytDlp ?: loading,
            icon = Icons.Outlined.SmartDisplay,
            trailing = { if (state.updatingYtDlp) CircularProgressIndicator(Modifier.size(24.dp)) },
            onClick = if (state.updatingYtDlp) null else { { viewModel.updateYtDlp() } },
        )
        PreferenceItem(
            title = "gallery-dl",
            summary = state.versions?.galleryDl ?: loading,
            icon = Icons.Outlined.Collections,
            trailing = { if (state.updatingGalleryDl) CircularProgressIndicator(Modifier.size(24.dp)) },
            onClick = if (state.updatingGalleryDl) null else { { viewModel.updateGalleryDl() } },
        )
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        PreferenceCategory(stringResource(R.string.pref_cat_updates))
        ChoicePreference(
            title = stringResource(R.string.pref_update_channel),
            value = s.updateChannel,
            options = UpdateChannel.entries,
            label = { stringResource(if (it == UpdateChannel.STABLE) R.string.channel_stable else R.string.channel_nightly) },
            onSelect = { viewModel.setChannel(it) },
            icon = Icons.Outlined.NewReleases,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_auto_update),
            summary = if (s.lastEngineUpdateCheck > 0) {
                stringResource(R.string.pref_auto_update_last, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastEngineUpdateCheck)))
            } else stringResource(R.string.pref_auto_update_summary),
            checked = s.autoUpdateEngines,
            onCheckedChange = { viewModel.setAutoUpdate(it) },
            icon = Icons.Outlined.Autorenew,
        )
        PreferenceItem(
            title = stringResource(R.string.engine_reset_gallerydl),
            summary = stringResource(R.string.engine_reset_gallerydl_summary),
            icon = Icons.Outlined.RestartAlt,
            onClick = { viewModel.resetGalleryDl() },
        )
    }
}
