package com.alo.downloader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.SponsorBlockMode
import com.alo.downloader.core.model.VideoContainer
import com.alo.downloader.core.model.VideoQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class UpdateChannel { STABLE, NIGHTLY }

/** Snapshot of every user preference. */
data class AppSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val seedColor: Int = 0,
    val pureBlack: Boolean = false,
    // Download behaviour
    val configureBeforeDownload: Boolean = false,
    val clipboardDetection: Boolean = true,
    val audioOnly: Boolean = false,
    val audioFormat: AudioFormat = AudioFormat.BEST,
    val videoQuality: VideoQuality = VideoQuality.BEST,
    val container: VideoContainer = VideoContainer.MP4,
    val preferAv1: Boolean = false,
    val subtitles: Boolean = false,
    val autoSubtitles: Boolean = false,
    val subtitleLanguages: String = "en.*,zh.*",
    val embedSubtitles: Boolean = true,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val embedChapters: Boolean = true,
    val cropAudioThumbnail: Boolean = true,
    val writeThumbnailFile: Boolean = false,
    val downloadPlaylist: Boolean = false,
    val sponsorBlock: SponsorBlockMode = SponsorBlockMode.OFF,
    val sponsorBlockCategories: String = "sponsor,selfpromo,interaction",
    val useArchive: Boolean = true,
    val restrictFilenames: Boolean = false,
    val filenameTemplate: String = DownloadOptions.DEFAULT_FILENAME_TEMPLATE,
    val customCommandEnabled: Boolean = false,
    val selectedTemplateId: Long = -1,
    // Storage
    /** SAF tree uri chosen by the user; null means the public Download/ALO folder. */
    val customTreeUri: String? = null,
    val perPlatformFolders: Boolean = true,
    // Network
    val maxConcurrent: Int = 2,
    val wifiOnly: Boolean = false,
    val rateLimit: String = "",
    val proxyEnabled: Boolean = false,
    val proxyUrl: String = "",
    val concurrentFragments: Int = 4,
    val useAria2c: Boolean = false,
    val forceIpv4: Boolean = false,
    val requestSleepSeconds: Double = 1.0,
    // Engines
    val engineFallback: Boolean = true,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val autoUpdateEngines: Boolean = true,
    val lastEngineUpdateCheck: Long = 0,
    val instagramWarningShown: Boolean = false,
) {
    /** Default options for a new task, derived from the settings. */
    fun toDownloadOptions(): DownloadOptions = DownloadOptions(
        audioOnly = audioOnly,
        audioFormat = audioFormat,
        videoQuality = videoQuality,
        container = container,
        preferAv1 = preferAv1,
        subtitles = subtitles,
        autoSubtitles = autoSubtitles,
        subtitleLanguages = subtitleLanguages,
        embedSubtitles = embedSubtitles,
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedChapters = embedChapters,
        writeThumbnailFile = writeThumbnailFile,
        cropAudioThumbnail = cropAudioThumbnail,
        downloadPlaylist = downloadPlaylist,
        sponsorBlock = sponsorBlock,
        sponsorBlockCategories = sponsorBlockCategories,
        useArchive = useArchive,
        restrictFilenames = restrictFilenames,
        filenameTemplate = filenameTemplate,
        rateLimit = rateLimit.takeIf { it.isNotBlank() },
        proxy = proxyUrl.takeIf { proxyEnabled && it.isNotBlank() },
        concurrentFragments = concurrentFragments,
        useAria2c = useAria2c,
        forceIpv4 = forceIpv4,
        requestSleepSeconds = requestSleepSeconds,
    )
}

object SettingKeys {
    val themeMode = stringPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val seedColor = intPreferencesKey("seed_color")
    val pureBlack = booleanPreferencesKey("pure_black")
    val configureBeforeDownload = booleanPreferencesKey("configure_before_download")
    val clipboardDetection = booleanPreferencesKey("clipboard_detection")
    val audioOnly = booleanPreferencesKey("audio_only")
    val audioFormat = stringPreferencesKey("audio_format")
    val videoQuality = stringPreferencesKey("video_quality")
    val container = stringPreferencesKey("container")
    val preferAv1 = booleanPreferencesKey("prefer_av1")
    val subtitles = booleanPreferencesKey("subtitles")
    val autoSubtitles = booleanPreferencesKey("auto_subtitles")
    val subtitleLanguages = stringPreferencesKey("subtitle_languages")
    val embedSubtitles = booleanPreferencesKey("embed_subtitles")
    val embedThumbnail = booleanPreferencesKey("embed_thumbnail")
    val embedMetadata = booleanPreferencesKey("embed_metadata")
    val embedChapters = booleanPreferencesKey("embed_chapters")
    val cropAudioThumbnail = booleanPreferencesKey("crop_audio_thumbnail")
    val writeThumbnailFile = booleanPreferencesKey("write_thumbnail_file")
    val downloadPlaylist = booleanPreferencesKey("download_playlist")
    val sponsorBlock = stringPreferencesKey("sponsorblock")
    val sponsorBlockCategories = stringPreferencesKey("sponsorblock_categories")
    val useArchive = booleanPreferencesKey("use_archive")
    val restrictFilenames = booleanPreferencesKey("restrict_filenames")
    val filenameTemplate = stringPreferencesKey("filename_template")
    val customCommandEnabled = booleanPreferencesKey("custom_command_enabled")
    val selectedTemplateId = longPreferencesKey("selected_template_id")
    val customTreeUri = stringPreferencesKey("custom_tree_uri")
    val perPlatformFolders = booleanPreferencesKey("per_platform_folders")
    val maxConcurrent = intPreferencesKey("max_concurrent")
    val wifiOnly = booleanPreferencesKey("wifi_only")
    val rateLimit = stringPreferencesKey("rate_limit")
    val proxyEnabled = booleanPreferencesKey("proxy_enabled")
    val proxyUrl = stringPreferencesKey("proxy_url")
    val concurrentFragments = intPreferencesKey("concurrent_fragments")
    val useAria2c = booleanPreferencesKey("use_aria2c")
    val forceIpv4 = booleanPreferencesKey("force_ipv4")
    val requestSleepSeconds = doublePreferencesKey("request_sleep_seconds")
    val engineFallback = booleanPreferencesKey("engine_fallback")
    val updateChannel = stringPreferencesKey("update_channel")
    val autoUpdateEngines = booleanPreferencesKey("auto_update_engines")
    val lastEngineUpdateCheck = longPreferencesKey("last_engine_update_check")
    val instagramWarningShown = booleanPreferencesKey("instagram_warning_shown")
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.dataStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun <T> set(key: Preferences.Key<T>, value: T?) {
        store.edit { prefs -> if (value == null) prefs.remove(key) else prefs[key] = value }
    }

    suspend fun <E : Enum<E>> setEnum(key: Preferences.Key<String>, value: E) = set(key, value.name)

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        val k = SettingKeys
        return AppSettings(
            themeMode = enumOf(this[k.themeMode], d.themeMode),
            dynamicColor = this[k.dynamicColor] ?: d.dynamicColor,
            seedColor = this[k.seedColor] ?: d.seedColor,
            pureBlack = this[k.pureBlack] ?: d.pureBlack,
            configureBeforeDownload = this[k.configureBeforeDownload] ?: d.configureBeforeDownload,
            clipboardDetection = this[k.clipboardDetection] ?: d.clipboardDetection,
            audioOnly = this[k.audioOnly] ?: d.audioOnly,
            audioFormat = enumOf(this[k.audioFormat], d.audioFormat),
            videoQuality = enumOf(this[k.videoQuality], d.videoQuality),
            container = enumOf(this[k.container], d.container),
            preferAv1 = this[k.preferAv1] ?: d.preferAv1,
            subtitles = this[k.subtitles] ?: d.subtitles,
            autoSubtitles = this[k.autoSubtitles] ?: d.autoSubtitles,
            subtitleLanguages = this[k.subtitleLanguages] ?: d.subtitleLanguages,
            embedSubtitles = this[k.embedSubtitles] ?: d.embedSubtitles,
            embedThumbnail = this[k.embedThumbnail] ?: d.embedThumbnail,
            embedMetadata = this[k.embedMetadata] ?: d.embedMetadata,
            embedChapters = this[k.embedChapters] ?: d.embedChapters,
            cropAudioThumbnail = this[k.cropAudioThumbnail] ?: d.cropAudioThumbnail,
            writeThumbnailFile = this[k.writeThumbnailFile] ?: d.writeThumbnailFile,
            downloadPlaylist = this[k.downloadPlaylist] ?: d.downloadPlaylist,
            sponsorBlock = enumOf(this[k.sponsorBlock], d.sponsorBlock),
            sponsorBlockCategories = this[k.sponsorBlockCategories] ?: d.sponsorBlockCategories,
            useArchive = this[k.useArchive] ?: d.useArchive,
            restrictFilenames = this[k.restrictFilenames] ?: d.restrictFilenames,
            filenameTemplate = this[k.filenameTemplate] ?: d.filenameTemplate,
            customCommandEnabled = this[k.customCommandEnabled] ?: d.customCommandEnabled,
            selectedTemplateId = this[k.selectedTemplateId] ?: d.selectedTemplateId,
            customTreeUri = this[k.customTreeUri],
            perPlatformFolders = this[k.perPlatformFolders] ?: d.perPlatformFolders,
            maxConcurrent = this[k.maxConcurrent] ?: d.maxConcurrent,
            wifiOnly = this[k.wifiOnly] ?: d.wifiOnly,
            rateLimit = this[k.rateLimit] ?: d.rateLimit,
            proxyEnabled = this[k.proxyEnabled] ?: d.proxyEnabled,
            proxyUrl = this[k.proxyUrl] ?: d.proxyUrl,
            concurrentFragments = this[k.concurrentFragments] ?: d.concurrentFragments,
            useAria2c = this[k.useAria2c] ?: d.useAria2c,
            forceIpv4 = this[k.forceIpv4] ?: d.forceIpv4,
            requestSleepSeconds = this[k.requestSleepSeconds] ?: d.requestSleepSeconds,
            engineFallback = this[k.engineFallback] ?: d.engineFallback,
            updateChannel = enumOf(this[k.updateChannel], d.updateChannel),
            autoUpdateEngines = this[k.autoUpdateEngines] ?: d.autoUpdateEngines,
            lastEngineUpdateCheck = this[k.lastEngineUpdateCheck] ?: d.lastEngineUpdateCheck,
            instagramWarningShown = this[k.instagramWarningShown] ?: d.instagramWarningShown,
        )
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
