package com.alo.downloader.ui.accounts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.core.model.Platform
import com.alo.downloader.data.cookie.AccountStatus
import com.alo.downloader.data.cookie.CookieSource
import com.alo.downloader.ui.common.PlatformBadge
import com.alo.downloader.ui.settings.PreferenceCategory
import com.alo.downloader.ui.settings.PreferenceItem
import com.alo.downloader.ui.settings.SettingsPage
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onLogin: (Platform) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.import(uri) { result ->
                val msg = result.fold(
                    onSuccess = { r ->
                        context.getString(
                            R.string.cookies_imported, r.total,
                            r.perPlatform.keys.joinToString { it.displayName },
                        )
                    },
                    onFailure = { context.getString(R.string.cookies_import_failed, it.message ?: "") },
                )
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsPage(title = stringResource(R.string.settings_accounts), onBack = onBack) {
        if (!viewModel.encryptionAvailable) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(stringResource(R.string.cookies_encryption_unavailable), Modifier.padding(16.dp))
            }
        }
        Text(
            stringResource(R.string.accounts_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        PreferenceItem(
            title = stringResource(R.string.cookies_import),
            summary = stringResource(R.string.cookies_import_summary),
            icon = Icons.Outlined.FileOpen,
            onClick = { importLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream", "*/*")) },
        )
        PreferenceCategory(stringResource(R.string.accounts_platforms))
        accounts.forEach { status ->
            AccountRow(
                status = status,
                onLogin = { onLogin(status.platform) },
                onRefresh = {
                    viewModel.refresh(status.platform) { ok ->
                        if (ok) Toast.makeText(context, R.string.cookies_refreshed, Toast.LENGTH_SHORT).show()
                        else onLogin(status.platform)
                    }
                },
                onClear = { viewModel.clear(status.platform) },
            )
        }
    }
}

@Composable
private fun AccountRow(status: AccountStatus, onLogin: () -> Unit, onRefresh: () -> Unit, onClear: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val summary = when {
        status.cookieCount == 0 -> stringResource(R.string.account_not_connected)
        status.loggedIn -> stringResource(
            R.string.account_connected,
            stringResource(if (status.source == CookieSource.WEB_LOGIN) R.string.source_web_login else R.string.source_import),
            status.savedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "",
        )
        else -> stringResource(R.string.account_cookies_only, status.cookieCount)
    }
    ListItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        leadingContent = { PlatformBadge(status.platform, size = 40.dp) },
        headlineContent = { Text(status.platform.displayName) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Row {
                if (status.platform.supportsWebLogin && !status.loggedIn) {
                    IconButton(onClick = onLogin) { Icon(Icons.AutoMirrored.Outlined.Login, stringResource(R.string.action_login)) }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (status.platform.supportsWebLogin) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_login)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Login, null) },
                                onClick = { menu = false; onLogin() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cookies_refresh)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = { menu = false; onRefresh() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cookies_clear)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            enabled = status.cookieCount > 0,
                            onClick = { menu = false; onClear() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun InstagramWarning(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                stringResource(R.string.instagram_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
