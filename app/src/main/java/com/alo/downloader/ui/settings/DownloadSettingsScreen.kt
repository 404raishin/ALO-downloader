package com.alo.downloader.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.documentfile.provider.DocumentFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.SponsorBlockMode
import com.alo.downloader.core.model.VideoContainer
import com.alo.downloader.core.model.VideoQuality
import com.alo.downloader.data.settings.SettingKeys

@Composable
fun DownloadSettingsScreen(
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.settings_download), onBack = onBack) {
        PreferenceCategory(stringResource(R.string.pref_cat_general))
        SwitchPreference(
            title = stringResource(R.string.pref_configure_before),
            summary = stringResource(R.string.pref_configure_before_summary),
            checked = s.configureBeforeDownload,
            onCheckedChange = { viewModel.set(SettingKeys.configureBeforeDownload, it) },
            icon = Icons.Outlined.Tune,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_clipboard),
            summary = stringResource(R.string.pref_clipboard_summary),
            checked = s.clipboardDetection,
            onCheckedChange = { viewModel.set(SettingKeys.clipboardDetection, it) },
            icon = Icons.Outlined.ContentPaste,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_playlist),
            summary = stringResource(R.string.pref_playlist_summary),
            checked = s.downloadPlaylist,
            onCheckedChange = { viewModel.set(SettingKeys.downloadPlaylist, it) },
            icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
        )

        PreferenceCategory(stringResource(R.string.pref_cat_format))
        SwitchPreference(
            title = stringResource(R.string.audio_only),
            summary = stringResource(R.string.pref_audio_only_summary),
            checked = s.audioOnly,
            onCheckedChange = { viewModel.set(SettingKeys.audioOnly, it) },
            icon = Icons.Outlined.MusicNote,
        )
        ChoicePreference(
            title = stringResource(R.string.pref_audio_format),
            value = s.audioFormat,
            options = AudioFormat.entries,
            label = { audioFormatLabel(it) },
            onSelect = { viewModel.setEnum(SettingKeys.audioFormat, it) },
            icon = Icons.Outlined.AudioFile,
        )
        ChoicePreference(
            title = stringResource(R.string.pref_video_quality),
            value = s.videoQuality,
            options = VideoQuality.entries,
            label = { qualityLabel(it) },
            onSelect = { viewModel.setEnum(SettingKeys.videoQuality, it) },
            icon = Icons.Outlined.HighQuality,
        )
        ChoicePreference(
            title = stringResource(R.string.pref_container),
            value = s.container,
            options = VideoContainer.entries,
            label = { containerLabel(it) },
            onSelect = { viewModel.setEnum(SettingKeys.container, it) },
            icon = Icons.Outlined.VideoFile,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_prefer_av1),
            summary = stringResource(R.string.pref_prefer_av1_summary),
            checked = s.preferAv1,
            onCheckedChange = { viewModel.set(SettingKeys.preferAv1, it) },
            icon = Icons.Outlined.Movie,
        )

        PreferenceCategory(stringResource(R.string.pref_cat_subtitles))
        SwitchPreference(
            title = stringResource(R.string.pref_subtitles),
            checked = s.subtitles,
            onCheckedChange = { viewModel.set(SettingKeys.subtitles, it) },
            icon = Icons.Outlined.Subtitles,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_auto_subtitles),
            summary = stringResource(R.string.pref_auto_subtitles_summary),
            checked = s.autoSubtitles,
            onCheckedChange = { viewModel.set(SettingKeys.autoSubtitles, it) },
            icon = Icons.Outlined.Translate,
        )
        TextPreference(
            title = stringResource(R.string.pref_subtitle_languages),
            value = s.subtitleLanguages,
            onValueChange = { viewModel.set(SettingKeys.subtitleLanguages, it) },
            placeholder = "en.*,zh.*",
            icon = Icons.Outlined.TextFields,
            enabled = s.subtitles || s.autoSubtitles,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_embed_subtitles),
            checked = s.embedSubtitles,
            onCheckedChange = { viewModel.set(SettingKeys.embedSubtitles, it) },
            enabled = s.subtitles || s.autoSubtitles,
            icon = Icons.Outlined.Subtitles,
        )

        PreferenceCategory(stringResource(R.string.pref_cat_post))
        SwitchPreference(
            title = stringResource(R.string.pref_embed_thumbnail),
            checked = s.embedThumbnail,
            onCheckedChange = { viewModel.set(SettingKeys.embedThumbnail, it) },
            icon = Icons.Outlined.Image,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_crop_thumbnail),
            summary = stringResource(R.string.pref_crop_thumbnail_summary),
            checked = s.cropAudioThumbnail,
            enabled = s.embedThumbnail,
            onCheckedChange = { viewModel.set(SettingKeys.cropAudioThumbnail, it) },
            icon = Icons.Outlined.Crop,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_write_thumbnail),
            checked = s.writeThumbnailFile,
            onCheckedChange = { viewModel.set(SettingKeys.writeThumbnailFile, it) },
            icon = Icons.Outlined.Image,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_embed_metadata),
            checked = s.embedMetadata,
            onCheckedChange = { viewModel.set(SettingKeys.embedMetadata, it) },
            icon = Icons.Outlined.Badge,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_embed_chapters),
            checked = s.embedChapters,
            onCheckedChange = { viewModel.set(SettingKeys.embedChapters, it) },
            icon = Icons.Outlined.ViewTimeline,
        )
        ChoicePreference(
            title = stringResource(R.string.pref_sponsorblock),
            value = s.sponsorBlock,
            options = SponsorBlockMode.entries,
            label = { sponsorBlockLabel(it) },
            onSelect = { viewModel.setEnum(SettingKeys.sponsorBlock, it) },
            icon = Icons.Outlined.FastForward,
        )
        TextPreference(
            title = stringResource(R.string.pref_sponsorblock_categories),
            value = s.sponsorBlockCategories,
            onValueChange = { viewModel.set(SettingKeys.sponsorBlockCategories, it.ifBlank { "sponsor" }) },
            placeholder = "sponsor,selfpromo,interaction,intro,outro,preview,music_offtopic,filler",
            enabled = s.sponsorBlock != SponsorBlockMode.OFF,
            icon = Icons.Outlined.FastForward,
        )

        PreferenceCategory(stringResource(R.string.pref_cat_storage))
        val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) viewModel.setDownloadTree(uri)
        }
        val treeName = s.customTreeUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }
        PreferenceItem(
            title = stringResource(R.string.pref_location),
            summary = treeName ?: "Download/ALO",
            icon = Icons.Outlined.Folder,
            onClick = { treeLauncher.launch(null) },
        )
        if (s.customTreeUri != null) {
            PreferenceItem(
                title = stringResource(R.string.pref_location_reset),
                summary = "Download/ALO",
                icon = Icons.Outlined.FolderOff,
                onClick = { viewModel.setDownloadTree(null) },
            )
        }
        SwitchPreference(
            title = stringResource(R.string.pref_platform_folders),
            summary = stringResource(R.string.pref_platform_folders_summary),
            checked = s.perPlatformFolders,
            onCheckedChange = { viewModel.set(SettingKeys.perPlatformFolders, it) },
            icon = Icons.Outlined.CreateNewFolder,
        )

        PreferenceCategory(stringResource(R.string.pref_cat_files))
        TextPreference(
            title = stringResource(R.string.pref_filename_template),
            value = s.filenameTemplate,
            onValueChange = { viewModel.set(SettingKeys.filenameTemplate, it.ifBlank { DownloadOptions.DEFAULT_FILENAME_TEMPLATE }) },
            icon = Icons.Outlined.TextFields,
            validate = { if (it.contains("%(ext)s") || it.isBlank()) null else context.getString(R.string.pref_filename_template_error) },
        )
        SwitchPreference(
            title = stringResource(R.string.pref_restrict_filenames),
            summary = stringResource(R.string.pref_restrict_filenames_summary),
            checked = s.restrictFilenames,
            onCheckedChange = { viewModel.set(SettingKeys.restrictFilenames, it) },
            icon = Icons.Outlined.TextFields,
        )
        SwitchPreference(
            title = stringResource(R.string.pref_archive),
            summary = stringResource(R.string.pref_archive_summary),
            checked = s.useArchive,
            onCheckedChange = { viewModel.set(SettingKeys.useArchive, it) },
            icon = Icons.Outlined.Inventory2,
        )
        PreferenceItem(
            title = stringResource(R.string.pref_clear_archive),
            summary = stringResource(R.string.pref_clear_archive_summary),
            icon = Icons.Outlined.DeleteSweep,
            onClick = {
                viewModel.clearArchives()
                android.widget.Toast.makeText(context, R.string.done, android.widget.Toast.LENGTH_SHORT).show()
            },
        )

        PreferenceCategory(stringResource(R.string.pref_cat_advanced))
        SwitchPreference(
            title = stringResource(R.string.pref_custom_command),
            summary = stringResource(R.string.pref_custom_command_summary),
            checked = s.customCommandEnabled,
            onCheckedChange = { viewModel.set(SettingKeys.customCommandEnabled, it) },
            icon = Icons.Outlined.Terminal,
        )
        PreferenceItem(
            title = stringResource(R.string.settings_templates),
            summary = stringResource(R.string.settings_templates_summary),
            icon = Icons.Outlined.Bookmarks,
            onClick = onOpenTemplates,
        )
    }
}
