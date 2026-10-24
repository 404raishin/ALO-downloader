package com.alo.downloader.ui.accounts

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alo.downloader.core.model.Platform
import com.alo.downloader.data.cookie.AccountStatus
import com.alo.downloader.data.cookie.CookieRepository
import com.alo.downloader.data.cookie.ImportResult
import com.alo.downloader.data.settings.SettingKeys
import com.alo.downloader.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cookies: CookieRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val accounts: StateFlow<List<AccountStatus>> = cookies.accounts
    val encryptionAvailable: Boolean get() = cookies.encryptionAvailable
    val instagramWarningShown: StateFlow<Boolean> = settings.settings.map { it.instagramWarningShown }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch { cookies.refreshStatus() }
    }

    fun import(uri: Uri, onDone: (Result<ImportResult>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: error("Cannot read file")
                cookies.importCookiesTxt(text).also { require(it.total > 0) { "No cookies found in file" } }
            }
            onDone(result)
        }
    }

    /** Re-reads the WebView session; returns false when the user must log in again. */
    fun refresh(platform: Platform, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(cookies.captureFromWebView(platform)) }
    }

    fun clear(platform: Platform) {
        viewModelScope.launch { cookies.clear(platform) }
    }

    fun capture(platform: Platform, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(cookies.captureFromWebView(platform)) }
    }

    fun webViewLoggedIn(platform: Platform) = cookies.webViewLoggedIn(platform)

    fun markInstagramWarningShown() {
        viewModelScope.launch { settings.set(SettingKeys.instagramWarningShown, true) }
    }
}
