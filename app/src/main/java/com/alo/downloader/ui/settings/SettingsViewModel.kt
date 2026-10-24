package com.alo.downloader.ui.settings

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alo.downloader.data.settings.AppSettings
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.download.OutputStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val storage: OutputStorage,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun <T> set(key: Preferences.Key<T>, value: T?) {
        viewModelScope.launch { repository.set(key, value) }
    }

    fun <E : Enum<E>> setEnum(key: Preferences.Key<String>, value: E) {
        viewModelScope.launch { repository.setEnum(key, value) }
    }

    /** Forgets which items were downloaded, so they can be downloaded again. */
    fun clearArchives() {
        storage.clearArchives()
    }
}
