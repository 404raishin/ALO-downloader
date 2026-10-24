package com.alo.downloader.ui.home

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextButton
import com.alo.downloader.ui.configure.ConfigureSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.core.model.Platform
import com.alo.downloader.ui.common.EmptyState
import com.alo.downloader.ui.common.SectionHeader
import com.alo.downloader.ui.common.TaskActions
import com.alo.downloader.ui.common.TaskCard

@Composable
fun HomeScreen(
    onLogin: (Platform) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val audioOverride by viewModel.audioOnly.collectAsStateWithLifecycle()
    val audioOnly = audioOverride ?: settings.audioOnly
    val clipboardLink by viewModel.clipboardLink.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var configureText by rememberSaveable { mutableStateOf<String?>(null) }

    // Clipboard access is only allowed while the window has focus (Android 10+).
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (focused) viewModel.onClipboard(clipboardText(context))
    }

    val toastQueued: (Int) -> Unit = { count ->
        val msg = if (count == 0) context.getString(R.string.no_link_found)
        else context.resources.getQuantityString(R.plurals.queued_count, count, count)
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    val download = {
        if (settings.configureBeforeDownload && input.isNotBlank()) configureText = input
        else viewModel.downloadInput(toastQueued)
    }

    val actions = TaskActions(
        onPause = { viewModel.pause(it) },
        onResume = { viewModel.resume(it) },
        onCancel = { viewModel.cancel(it) },
        onRetry = { viewModel.retry(it) },
        onDelete = { viewModel.delete(it) },
        onLogin = onLogin,
        onCopyLink = { copyToClipboard(context, it) },
        onOpenLink = { openLink(context, it) },
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InputCard(
                    text = input,
                    onTextChange = viewModel::setInput,
                    audioOnly = audioOnly,
                    onToggleAudio = viewModel::toggleAudioOnly,
                    onPaste = { clipboardText(context)?.let(viewModel::setInput) },
                    onDownload = download,
                    onConfigure = { if (input.isNotBlank()) configureText = input },
                )
            }
            clipboardLink?.let { link ->
                item {
                    ClipboardCard(
                        link = link,
                        onDownload = { viewModel.downloadClipboard(toastQueued) },
                        onConfigure = { viewModel.dismissClipboard(); configureText = link },
                        onDismiss = viewModel::dismissClipboard,
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.section_queue)) }
            if (queue.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(R.string.queue_empty_title),
                        body = stringResource(R.string.queue_empty_body),
                    )
                }
            } else {
                items(queue, key = { it.task.id }) { item ->
                    TaskCard(item, actions, Modifier.padding(horizontal = 16.dp), live = live[item.task.id])
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    configureText?.let { text ->
        ConfigureSheet(text = text, onDismiss = { configureText = null })
    }
}

@Composable
private fun ClipboardCard(link: String, onDownload: () -> Unit, onConfigure: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.clipboard_link_found),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.dismiss)) }
            }
            Text(
                link,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onConfigure) { Text(stringResource(R.string.configure_download)) }
                TextButton(onClick = onDownload) { Text(stringResource(R.string.download)) }
            }
        }
    }
}

@Composable
private fun InputCard(
    text: String,
    onTextChange: (String) -> Unit,
    audioOnly: Boolean,
    onToggleAudio: () -> Unit,
    onPaste: () -> Unit,
    onDownload: () -> Unit,
    onConfigure: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.input_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Link, null) },
                trailingIcon = {
                    if (text.isEmpty()) {
                        IconButton(onClick = onPaste) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.paste)) }
                    } else {
                        IconButton(onClick = { onTextChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                    }
                },
                shape = MaterialTheme.shapes.medium,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onDownload() }),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = audioOnly,
                    onClick = onToggleAudio,
                    label = { Text(stringResource(R.string.audio_only)) },
                    leadingIcon = { Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = onConfigure, enabled = text.isNotBlank()) {
                    Icon(Icons.Outlined.Tune, stringResource(R.string.configure_download))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDownload, enabled = text.isNotBlank()) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.download))
                }
            }
        }
    }
}

fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText("link", text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
