package com.alo.downloader.ui.configure

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.core.media.FormatSelection
import com.alo.downloader.core.media.MediaFormat
import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.VideoQuality
import com.alo.downloader.core.router.PlatformResolver
import com.alo.downloader.core.util.FileNames
import com.alo.downloader.download.DownloadNotifier
import com.alo.downloader.ui.common.Thumbnail
import com.alo.downloader.ui.common.failureReasonText
import com.alo.downloader.ui.settings.audioFormatLabel
import com.alo.downloader.ui.settings.qualityLabel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Bottom sheet for picking format, quality, playlist items, engine and start time before
 * queueing. Used from the home screen and from the share sheet.
 */
@Composable
fun ConfigureSheet(text: String, onDismiss: () -> Unit, viewModel: ConfigureViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(text) { viewModel.load(text) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Header(state)
            Spacer(Modifier.height(16.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !state.audioOnly,
                    onClick = { viewModel.setAudioOnly(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.mode_video)) }
                SegmentedButton(
                    selected = state.audioOnly,
                    onClick = { viewModel.setAudioOnly(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.audio_only)) }
            }

            if (state.loading) {
                Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.fetching_info), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.infoError?.let {
                Text(
                    stringResource(R.string.info_failed, stringResource(failureReasonText(it.reason))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            if (!state.audioOnly) {
                Label(stringResource(R.string.pref_video_quality))
                val heights = state.info?.let { FormatSelection.heights(it) }.orEmpty()
                val presets = VideoQuality.entries.filter { q ->
                    q == VideoQuality.BEST || q == VideoQuality.WORST || heights.isEmpty() || heights.any { h -> h >= (q.maxHeight ?: 0) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { q ->
                        FilterChip(
                            selected = state.videoFormat == null && state.quality == q,
                            onClick = { viewModel.setQuality(q) },
                            label = { Text(qualityLabel(q)) },
                        )
                    }
                }
            } else {
                Label(stringResource(R.string.pref_audio_format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFormat.entries.forEach { f ->
                        FilterChip(
                            selected = state.audioFormat == f,
                            onClick = { viewModel.setAudioFormat(f) },
                            label = { Text(audioFormatLabel(f)) },
                        )
                    }
                }
            }

            val info = state.info
            if (info != null && !info.isPlaylist && state.urls.size == 1 && info.formats.isNotEmpty()) {
                FormatPicker(
                    title = stringResource(if (state.audioOnly) R.string.audio_streams else R.string.video_streams),
                    formats = if (state.audioOnly) info.audioFormats else info.videoFormats,
                    selected = if (state.audioOnly) state.audioStream else state.videoFormat,
                    onSelect = { if (state.audioOnly) viewModel.selectAudioStream(it) else viewModel.selectVideoFormat(it) },
                )
                if (!state.audioOnly && state.videoFormat?.isVideoOnly == true) {
                    FormatPicker(
                        title = stringResource(R.string.audio_streams),
                        formats = info.audioFormats,
                        selected = state.audioStream,
                        onSelect = viewModel::selectAudioStream,
                    )
                }
            }

            if (info?.isPlaylist == true && info.entries.isNotEmpty()) {
                PlaylistPicker(state, viewModel)
            }

            Spacer(Modifier.height(8.dp))
            ToggleRow(stringResource(R.string.pref_subtitles), state.subtitles, viewModel::setSubtitles)
            if (state.templateAvailable) {
                ToggleRow(stringResource(R.string.use_custom_command), state.useTemplate, viewModel::setUseTemplate)
            }

            Label(stringResource(R.string.engine))
            val auto = state.urls.firstOrNull()?.let { PlatformResolver.resolve(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.engine == null, onClick = { viewModel.setEngine(null) }, label = { Text(stringResource(R.string.engine_auto)) })
                listOf(EngineType.YT_DLP, EngineType.GALLERY_DL).forEach { e ->
                    FilterChip(selected = state.engine == e, onClick = { viewModel.setEngine(e) }, label = { Text(e.displayName) })
                }
            }
            auto?.let {
                Text(
                    stringResource(R.string.saved_to, "Download/ALO/${it.folderName}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.scheduleAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
                            ?: stringResource(R.string.schedule),
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = {
                        viewModel.download { count ->
                            Toast.makeText(context, context.resources.getQuantityString(R.plurals.queued_count, count, count), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = state.urls.isNotEmpty() && (info?.isPlaylist != true || state.playlistSelection.isNotEmpty()),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.download))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        ScheduleDialog(
            onDismiss = { showTimePicker = false },
            onClear = { viewModel.setSchedule(null); showTimePicker = false },
            onConfirm = { viewModel.setSchedule(it); showTimePicker = false },
        )
    }
}

@Composable
private fun Header(state: ConfigureState) {
    val info = state.info
    val platform = state.urls.firstOrNull()?.let { PlatformResolver.resolve(it) } ?: com.alo.downloader.core.model.Platform.GENERIC
    Row(verticalAlignment = Alignment.CenterVertically) {
        Thumbnail(info?.thumbnail, platform)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                info?.title ?: state.urls.firstOrNull() ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                info?.uploader?.let(::add)
                info?.durationSeconds?.let { add(DownloadNotifier.formatEta(it)) }
                if (info?.isPlaylist == true) add(stringResource(R.string.playlist_count, info.entries.size))
                if (state.urls.size > 1) add(stringResource(R.string.links_count, state.urls.size))
            }
            if (sub.isNotEmpty()) {
                Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FormatPicker(title: String, formats: List<MediaFormat>, selected: MediaFormat?, onSelect: (MediaFormat?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                selected?.label() ?: stringResource(R.string.format_automatic),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
    }
    if (expanded) {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            FormatRow(stringResource(R.string.format_automatic), null, selected == null) { onSelect(null) }
            formats.forEach { f ->
                FormatRow(f.label(), f.sizeBytes?.let(FileNames::humanSize), selected?.id == f.id) { onSelect(f) }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun FormatRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PlaylistPicker(state: ConfigureState, viewModel: ConfigureViewModel) {
    val entries = state.info?.entries.orEmpty()
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.playlist_selected, state.playlistSelection.size, entries.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        val all = state.playlistSelection.size == entries.size
        TextButton(onClick = { viewModel.selectAllPlaylist(!all) }) {
            Text(stringResource(if (all) R.string.select_none else R.string.select_all))
        }
    }
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
        entries.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.togglePlaylistItem(e.index) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = e.index in state.playlistSelection, onCheckedChange = { viewModel.togglePlaylistItem(e.index) })
                Text("${e.index}. ${e.title ?: e.id ?: e.url ?: ""}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                e.durationSeconds?.let {
                    Text(DownloadNotifier.formatEta(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ScheduleDialog(onDismiss: () -> Unit, onClear: () -> Unit, onConfirm: (Long) -> Unit) {
    val now = Calendar.getInstance()
    val picker = rememberTimePickerState(initialHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24, initialMinute = 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_title)) },
        text = { TimePicker(state = picker) },
        confirmButton = {
            TextButton(onClick = {
                val at = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, picker.hour)
                    set(Calendar.MINUTE, picker.minute)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis
                onConfirm(at)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.schedule_now)) } },
    )
}
