package com.alo.downloader.ui.viewer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.alo.downloader.R
import com.alo.downloader.data.db.DownloadedFileEntity
import com.alo.downloader.data.db.FileDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(private val fileDao: FileDao) : ViewModel() {
    private val _files = MutableStateFlow<List<DownloadedFileEntity>>(emptyList())
    val files: StateFlow<List<DownloadedFileEntity>> = _files.asStateFlow()
    private val _start = MutableStateFlow(0)
    val start: StateFlow<Int> = _start.asStateFlow()

    fun load(fileId: Long) = viewModelScope.launch {
        val file = fileDao.get(fileId) ?: return@launch
        val siblings = fileDao.forTask(file.taskId).sortedBy { it.id }
        _files.value = siblings
        _start.value = siblings.indexOfFirst { it.id == fileId }.coerceAtLeast(0)
    }
}

/** Full-screen viewer for downloaded photos, videos and audio, swiping through one download. */
@Composable
fun ViewerScreen(fileId: Long, onBack: () -> Unit, viewModel: ViewerViewModel = hiltViewModel()) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val start by viewModel.start.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(fileId) { viewModel.load(fileId) }
    if (files.isEmpty()) return

    val pager = rememberPagerState(initialPage = start) { files.size }
    val context = LocalContext.current
    val current = files.getOrNull(pager.currentPage)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current?.displayName.orEmpty() + if (files.size > 1) "  ${pager.currentPage + 1}/${files.size}" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    current?.let { f ->
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType(f.mimeType)
                                .putExtra(Intent.EXTRA_STREAM, f.uri.toUri())
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                        }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
                        IconButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(f.uri.toUri(), f.mimeType)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                )
                            }
                        }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_with)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            val file = files[page]
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                when {
                    file.mimeType.startsWith("image/") -> ZoomableImage(file.uri)
                    file.mimeType.startsWith("video/") || file.mimeType.startsWith("audio/") ->
                        MediaPlayer(file.uri, active = pager.currentPage == page)
                    else -> Text(file.displayName, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(uri: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

@Composable
private fun MediaPlayer(uri: String, active: Boolean) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    androidx.compose.runtime.LaunchedEffect(active) { player.playWhenReady = active }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize(),
    )
}
