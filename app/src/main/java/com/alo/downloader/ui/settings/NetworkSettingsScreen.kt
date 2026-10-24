package com.alo.downloader.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.data.settings.SettingKeys

private val RATE = Regex("""^\d+(\.\d+)?[KMG]?$""", RegexOption.IGNORE_CASE)
private val PROXY = Regex("""^(https?|socks4a?|socks5h?)://\S+$""", RegexOption.IGNORE_CASE)

@Composable
fun NetworkSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.settings_network), onBack = onBack) {
        PreferenceCategory(stringResource(R.string.pref_cat_queue))
        SliderPreference(
            title = stringResource(R.string.pref_max_concurrent),
            value = s.maxConcurrent,
            range = 1..5,
            onValueChange = { viewModel.set(SettingKeys.maxConcurrent, it) },
            icon = Icons.Outlined.DynamicFeed,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_wifi_only),
            summary = stringResource(R.string.pref_wifi_only_summary),
            checked = s.wifiOnly,
            onCheckedChange = { viewModel.set(SettingKeys.wifiOnly, it) },
            icon = Icons.Outlined.Wifi,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_engine_fallback),
            summary = stringResource(R.string.pref_engine_fallback_summary),
            checked = s.engineFallback,
            onCheckedChange = { viewModel.set(SettingKeys.engineFallback, it) },
            icon = Icons.Outlined.AltRoute,
        )

        PreferenceCategory(stringResource(R.string.pref_cat_connection))
        TextPreference(
            title = stringResource(R.string.pref_rate_limit),
            value = s.rateLimit,
            onValueChange = { viewModel.set(SettingKeys.rateLimit, it) },
            placeholder = stringResource(R.string.pref_rate_limit_hint),
            icon = Icons.Outlined.Speed,
            validate = { if (it.isBlank() || RATE.matches(it.trim())) null else context.getString(R.string.pref_rate_limit_error) },
        )
        SwitchPreference(
            title = stringResource(R.string.pref_proxy),
            checked = s.proxyEnabled,
            onCheckedChange = { viewModel.set(SettingKeys.proxyEnabled, it) },
            icon = Icons.Outlined.VpnKey,
        )
        TextPreference(
            title = stringResource(R.string.pref_proxy_url),
            value = s.proxyUrl,
            onValueChange = { viewModel.set(SettingKeys.proxyUrl, it) },
            placeholder = "socks5://127.0.0.1:1080",
            enabled = s.proxyEnabled,
            icon = Icons.Outlined.Lan,
            validate = { if (it.isBlank() || PROXY.matches(it.trim())) null else context.getString(R.string.pref_proxy_error) },
        )
        SwitchPreference(
            title = stringResource(R.string.pref_force_ipv4),
            checked = s.forceIpv4,
            onCheckedChange = { viewModel.set(SettingKeys.forceIpv4, it) },
            icon = Icons.Outlined.Hub,
        )

        PreferenceCategory(stringResource(R.string.pref_cat_performance))
        SliderPreference(
            title = stringResource(R.string.pref_fragments),
            value = s.concurrentFragments,
            range = 1..16,
            onValueChange = { viewModel.set(SettingKeys.concurrentFragments, it) },
            enabled = !s.useAria2c,
            icon = Icons.AutoMirrored.Outlined.CallSplit,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_aria2c),
            summary = stringResource(R.string.pref_aria2c_summary),
            checked = s.useAria2c,
            onCheckedChange = { viewModel.set(SettingKeys.useAria2c, it) },
            icon = Icons.Outlined.Speed,
        )
        SliderPreference(
            title = stringResource(R.string.pref_request_sleep),
            value = s.requestSleepSeconds.toInt(),
            range = 0..10,
            onValueChange = { viewModel.set(SettingKeys.requestSleepSeconds, it.toDouble()) },
            icon = Icons.Outlined.Timer,
            valueLabel = { context.getString(R.string.seconds_value, it) },
        )
    }
}
