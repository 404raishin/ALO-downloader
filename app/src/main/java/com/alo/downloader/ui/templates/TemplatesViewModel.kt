package com.alo.downloader.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alo.downloader.data.db.CommandTemplateEntity
import com.alo.downloader.data.db.TemplateDao
import com.alo.downloader.data.settings.SettingKeys
import com.alo.downloader.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val dao: TemplateDao,
    private val settings: SettingsRepository,
) : ViewModel() {
    val templates: StateFlow<List<CommandTemplateEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedId: StateFlow<Long> = settings.settings.map { if (it.customCommandEnabled) it.selectedTemplateId else -1L }
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    fun save(template: CommandTemplateEntity) = viewModelScope.launch { dao.upsert(template) }

    fun delete(id: Long) = viewModelScope.launch {
        dao.delete(id)
        if (selectedId.value == id) settings.set(SettingKeys.customCommandEnabled, false)
    }

    /** Makes [id] the active template (or turns custom commands off with -1). */
    fun select(id: Long) = viewModelScope.launch {
        settings.set(SettingKeys.selectedTemplateId, id)
        settings.set(SettingKeys.customCommandEnabled, id >= 0)
    }
}
