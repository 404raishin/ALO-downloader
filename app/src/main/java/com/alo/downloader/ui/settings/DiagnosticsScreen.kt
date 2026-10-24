package com.alo.downloader.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alo.downloader.BuildConfig
import com.alo.downloader.R
import com.alo.downloader.core.util.FileNames
import com.alo.downloader.data.db.TaskDao
import com.alo.downloader.update.EngineUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** Builds a plain-text report users can paste into a bug report. */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updater: EngineUpdater,
    private val taskDao: TaskDao,
) : ViewModel() {
    private val _report = MutableStateFlow<String?>(null)
    val report: StateFlow<String?> = _report.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _report.value = null
        _report.value = buildReport()
    }

    private suspend fun buildReport(): String = buildString {
        appendLine("ALO ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Page size: ${runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(-1)} bytes")
        val free = context.filesDir.usableSpace
        appendLine("Free space: ${FileNames.humanSize(free)}")
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val net = cm?.activeNetworkInfo
        appendLine("Network: ${net?.typeName ?: "none"}, connected=${net?.isConnected}, metered=${cm?.isActiveNetworkMetered}")
        appendLine()
        val v = updater.versions()
        appendLine("yt-dlp: ${v.ytDlp ?: "NOT STARTED — ${v.ytDlpError}"}")
        appendLine("gallery-dl: ${v.galleryDl ?: "NOT STARTED — ${v.galleryDlError}"}")
        appendLine()
        appendLine("Queue: " + taskDao.statusCounts().joinToString { "${it.status.name.lowercase()}=${it.count}" }.ifEmpty { "empty" })
        val failed = taskDao.recentFailed(5)
        if (failed.isNotEmpty()) {
            appendLine()
            appendLine("Recent failures:")
            val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            failed.forEach { t ->
                appendLine("• ${t.finishedAt?.let { fmt.format(Date(it)) } ?: "?"}  ${t.url}")
                appendLine("  ${t.errorReason}  [${t.attempts ?: t.engine}]")
                t.errorMessage?.lineSequence()?.take(6)?.forEach { appendLine("  $it") }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.diagnostics), onBack = onBack) {
        Text(
            stringResource(R.string.diagnostics_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = report != null, onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("ALO diagnostics", report))
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.diagnostics_copy)) }
            OutlinedButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.reload)) }
        }
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            SelectionContainer {
                Text(
                    report ?: stringResource(R.string.diagnostics_collecting),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
