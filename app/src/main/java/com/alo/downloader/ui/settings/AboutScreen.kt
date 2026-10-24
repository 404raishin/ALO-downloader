package com.alo.downloader.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alo.downloader.BuildConfig
import com.alo.downloader.R
import com.alo.downloader.ui.home.openLink

const val SOURCE_URL = "https://github.com/404raishin/ALO-downloader"

@Composable
fun AboutScreen(onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.settings_about), onBack = onBack) {
        Image(
            painter = painterResource(R.drawable.alo_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .padding(vertical = 16.dp)
                .size(112.dp)
                .align(Alignment.CenterHorizontally),
        )
        PreferenceItem(
            title = stringResource(R.string.app_name),
            summary = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            icon = Icons.Outlined.Info,
        )
        PreferenceItem(
            title = stringResource(R.string.diagnostics),
            summary = stringResource(R.string.diagnostics_summary),
            icon = Icons.Outlined.BugReport,
            onClick = onDiagnostics,
        )
        PreferenceItem(
            title = stringResource(R.string.about_source),
            summary = SOURCE_URL,
            icon = Icons.Outlined.Code,
            onClick = { openLink(context, SOURCE_URL) },
        )
        PreferenceItem(
            title = stringResource(R.string.about_license),
            summary = stringResource(R.string.about_license_summary),
            icon = Icons.Outlined.Gavel,
            onClick = { openLink(context, "https://www.gnu.org/licenses/gpl-3.0.html") },
        )
        PreferenceItem(
            title = stringResource(R.string.about_distribution),
            summary = stringResource(R.string.about_distribution_summary),
            icon = Icons.Outlined.Storefront,
            onClick = { openLink(context, "$SOURCE_URL/releases") },
        )
        PreferenceItem(
            title = stringResource(R.string.about_credits),
            summary = stringResource(R.string.about_credits_summary),
            icon = Icons.Outlined.Policy,
        )
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
