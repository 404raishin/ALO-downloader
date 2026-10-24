package com.alo.downloader.ui.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alo.downloader.telegram.TelegramAuth
import com.alo.downloader.telegram.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TelegramViewModel @Inject constructor(private val repository: TelegramRepository) : ViewModel() {
    val auth: StateFlow<TelegramAuth> = repository.auth

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureSession() }
    }

    fun configure(apiId: String, apiHash: String) = act { repository.configure(apiId, apiHash) }
    fun sendPhone(phone: String) = act { repository.sendPhone(phone.filter { it.isDigit() || it == '+' }) }
    fun sendCode(code: String) = act { repository.sendCode(code.trim()) }
    fun sendPassword(password: String) = act { repository.sendPassword(password) }
    fun logOut() = act { repository.logOut() }
    fun reset() = act { repository.reset() }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it.message ?: it.toString() }
            _busy.value = false
        }
    }
}
