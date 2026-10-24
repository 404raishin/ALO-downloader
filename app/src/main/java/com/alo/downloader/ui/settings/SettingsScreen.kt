package com.alo.downloader.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alo.downloader.R

enum class SettingsDestination { APPEARANCE, DOWNLOAD, NETWORK, ACCOUNTS, TEMPLATES, ENGINES, ABOUT }

@Composable
fun SettingsScreen(onNavigate: (SettingsDestination) -> Unit) {
    SettingsPage(title = stringResource(R.string.nav_settings), onBack = null) {
        PreferenceItem(
            title = stringResource(R.string.settings_appearance),
            summary = stringResource(R.string.settings_appearance_summary),
            icon = Icons.Outlined.Palette,
            onClick = { onNavigate(SettingsDestination.APPEARANCE) },
        )
        PreferenceItem(
            title = stringResource(R.string.settings_download),
            summary = stringResource(R.string.settings_download_summary),
            icon = Icons.Outlined.Download,
            onClick = { onNavigate(SettingsDestination.DOWNLOAD) },
        )
        PreferenceItem(
            title = stringResource(R.string.settings_network),
            summary = stringResource(R.string.settings_network_summary),
            icon = Icons.Outlined.Language,
            onClick = { onNavigate(SettingsDestination.NETWORK) },
        )
        PreferenceItem(
            title = stringResource(R.string.settings_accounts),
            summary = stringResource(R.string.settings_accounts_summary),
            icon = Icons.Outlined.ManageAccounts,
            onClick = { onNavigate(SettingsDestination.ACCOUNTS) },
        )
        PreferenceItem(
            title = stringResource(R.string.settings_templates),
            summary = stringResource(R.string.settings_templates_summary),
            icon = Icons.Outlined.Code,
            onClick = { onNavigate(SettingsDestination.TEMPLATES) },
        )
        PreferenceItem(
            title = stringResource(R.string.settings_about),
            summary = stringResource(R.string.settings_about_summary),
            icon = Icons.Outlined.Info,
            onClick = { onNavigate(SettingsDestination.ABOUT) },
        )
    }
}
