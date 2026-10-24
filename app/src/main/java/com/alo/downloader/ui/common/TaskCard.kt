package com.alo.downloader.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alo.downloader.R
import com.alo.downloader.core.model.DownloadStatus
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.Platform
import com.alo.downloader.data.db.TaskWithFiles
import com.alo.downloader.download.DownloadNotifier
import java.text.DateFormat
import java.util.Date

data class TaskActions(
    val onPause: (String) -> Unit = {},
    val onResume: (String) -> Unit = {},
    val onCancel: (String) -> Unit = {},
    val onRetry: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onLogin: (Platform) -> Unit = {},
    val onCopyLink: (String) -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onOpen: (TaskWithFiles) -> Unit = {},
)

@Composable
fun TaskCard(item: TaskWithFiles, actions: TaskActions, modifier: Modifier = Modifier) {
    val task = item.task
    val platform = Platform.fromId(task.platform)
    var menu by remember { mutableStateOf(false) }

    Card(
        onClick = { actions.onOpen(item) },
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Thumbnail(task.thumbnailUrl, platform)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title ?: task.url,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    val engine = EngineType.fromId(task.engine)?.displayName
                    Text(
                        text = listOfNotNull(platform.displayName, engine, stringResource(statusText(task.status))).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_link)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { menu = false; actions.onCopyLink(task.url) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_link)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = { menu = false; actions.onOpenLink(task.url) },
                        )
                        if (task.status.canRetry || task.status == DownloadStatus.COMPLETED) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_download_again)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = { menu = false; actions.onRetry(task.id) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; actions.onDelete(task.id) },
                        )
                    }
                }
            }

            when (task.status) {
                DownloadStatus.PREPARING, DownloadStatus.RUNNING -> {
                    Spacer(Modifier.height(12.dp))
                    if (task.progress < 0) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                    } else {
                        LinearProgressIndicator(
                            progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val details = buildList {
                        if (task.progress >= 0) add("${task.progress.toInt()}%")
                        task.speed?.let(::add)
                        task.etaSeconds?.let { add(DownloadNotifier.formatEta(it)) }
                        if (task.filesDone > 0) add(stringResourcePlural(task.filesDone))
                    }
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DownloadStatus.FAILED -> {
                    task.errorReason?.let { reason ->
                        Spacer(Modifier.height(10.dp))
                        ErrorPanel(reason, task.errorMessage, task.attempts, platform, actions.onLogin)
                    }
                }
                DownloadStatus.SCHEDULED -> task.scheduledAt?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.scheduled_for, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                DownloadStatus.COMPLETED -> {
                    if (task.alreadyDownloaded) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.status_already_downloaded), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else if (item.files.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.files.first().location,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> Unit
            }

            ControlRow(task.status, task.id, actions)
        }
    }
}

@Composable
private fun stringResourcePlural(count: Int): String {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    return res.getQuantityString(R.plurals.files_done, count, count)
}

@Composable
private fun ControlRow(status: DownloadStatus, id: String, actions: TaskActions) {
    if (status == DownloadStatus.COMPLETED) return
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (status.canPause) {
            TextButton(onClick = { actions.onPause(id) }) {
                Icon(Icons.Outlined.Pause, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_pause))
            }
        }
        if (status.canResume) {
            TextButton(onClick = { actions.onResume(id) }) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_resume))
            }
        }
        if (status.canRetry) {
            TextButton(onClick = { actions.onRetry(id) }) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
        if (!status.isFinished) {
            TextButton(onClick = { actions.onCancel(id) }) {
                Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
fun ErrorPanel(
    reason: com.alo.downloader.core.model.FailureReason,
    message: String?,
    attempts: String?,
    platform: Platform,
    onLogin: (Platform) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(14.dp),
    ) {
        Text(
            stringResource(failureReasonText(reason)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        failureHintText(reason)?.let {
            Spacer(Modifier.height(2.dp))
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!attempts.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(attempts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
        }
        if (reason.needsLogin && platform.supportsWebLogin) {
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { onLogin(platform) }) {
                Icon(Icons.AutoMirrored.Outlined.Login, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_login_platform, platform.displayName))
            }
        }
    }
}

@Composable
fun Thumbnail(url: String?, platform: Platform, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 96.dp, height = 60.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        PlatformBadge(platform, size = 30.dp)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
