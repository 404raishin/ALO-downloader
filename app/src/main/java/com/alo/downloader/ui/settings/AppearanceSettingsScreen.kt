package com.alo.downloader.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.data.settings.SettingKeys
import com.alo.downloader.data.settings.ThemeMode
import com.alo.downloader.ui.theme.dynamicColorAvailable
import com.alo.downloader.ui.theme.seedPalettes

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    SettingsPage(title = stringResource(R.string.settings_appearance), onBack = onBack) {
        ChoicePreference(
            title = stringResource(R.string.pref_theme),
            value = s.themeMode,
            options = ThemeMode.entries,
            label = { themeModeLabel(it) },
            onSelect = { viewModel.setEnum(SettingKeys.themeMode, it) },
            icon = Icons.Outlined.DarkMode,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_dynamic_color),
            summary = stringResource(if (dynamicColorAvailable) R.string.pref_dynamic_color_summary else R.string.pref_dynamic_color_unavailable),
            checked = s.dynamicColor && dynamicColorAvailable,
            enabled = dynamicColorAvailable,
            onCheckedChange = { viewModel.set(SettingKeys.dynamicColor, it) },
            icon = Icons.Outlined.Colorize,
        )
        PreferenceCategory(stringResource(R.string.pref_palette))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            seedPalettes.forEachIndexed { index, palette ->
                val selected = (!s.dynamicColor || !dynamicColorAvailable) && s.seedColor == index
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(palette.swatch)
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable {
                            viewModel.set(SettingKeys.seedColor, index)
                            viewModel.set(SettingKeys.dynamicColor, false)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Outlined.Check, palette.name, tint = Color.White)
                }
            }
        }
        SwitchPreference(
            title = stringResource(R.string.pref_pure_black),
            summary = stringResource(R.string.pref_pure_black_summary),
            checked = s.pureBlack,
            onCheckedChange = { viewModel.set(SettingKeys.pureBlack, it) },
            icon = Icons.Outlined.Contrast,
        )
        PreferenceCategory(stringResource(R.string.pref_language))
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val languages = listOf("", "en", "zh-CN")
        ChoicePreference(
            title = stringResource(R.string.pref_language),
            value = languages.firstOrNull { it.isNotEmpty() && current.startsWith(it) } ?: "",
            options = languages,
            label = {
                when (it) {
                    "" -> stringResource(R.string.language_system)
                    "en" -> "English"
                    else -> "简体中文"
                }
            },
            onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
            icon = Icons.Outlined.Translate,
        )
    }
}
