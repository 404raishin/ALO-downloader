package com.alo.downloader.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alo.downloader.R
import com.alo.downloader.core.model.AudioFormat
import com.alo.downloader.core.model.SponsorBlockMode
import com.alo.downloader.core.model.VideoContainer
import com.alo.downloader.core.model.VideoQuality
import com.alo.downloader.data.settings.ThemeMode

@Composable
fun audioFormatLabel(f: AudioFormat): String =
    if (f == AudioFormat.BEST) stringResource(R.string.option_best_original) else f.ytDlpName.uppercase()

@Composable
fun qualityLabel(q: VideoQuality): String = when (q) {
    VideoQuality.BEST -> stringResource(R.string.option_best)
    VideoQuality.WORST -> stringResource(R.string.option_smallest)
    else -> "${q.maxHeight}p"
}

@Composable
fun containerLabel(c: VideoContainer): String =
    if (c == VideoContainer.DEFAULT) stringResource(R.string.option_auto) else c.ytDlpName.uppercase()

@Composable
fun sponsorBlockLabel(m: SponsorBlockMode): String = when (m) {
    SponsorBlockMode.OFF -> stringResource(R.string.option_off)
    SponsorBlockMode.MARK -> stringResource(R.string.sponsorblock_mark)
    SponsorBlockMode.REMOVE -> stringResource(R.string.sponsorblock_remove)
}

@Composable
fun themeModeLabel(m: ThemeMode): String = when (m) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}
