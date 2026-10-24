package com.alo.downloader.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import com.alo.downloader.data.settings.SettingKeys
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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

    /** Stores a folder picked with the Storage Access Framework (null = Download/ALO). */
    fun setDownloadTree(uri: Uri?) {
        val previous = settings.value.customTreeUri
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (uri != null) runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        if (previous != null && previous != uri?.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(previous), flags) }
        }
        set(SettingKeys.customTreeUri, uri?.toString())
    }

    /** Forgets which items were downloaded, so they can be downloaded again. */
    fun clearArchives() {
        storage.clearArchives()
    }
}
