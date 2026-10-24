package com.alo.downloader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.alo.downloader.data.db.DownloadedFileEntity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.core.model.Platform
import com.alo.downloader.data.db.TaskWithFiles
import com.alo.downloader.ui.common.EmptyState
import com.alo.downloader.ui.common.TaskActions
import com.alo.downloader.ui.common.TaskCard
import com.alo.downloader.ui.home.copyToClipboard
import com.alo.downloader.ui.home.openLink

@Composable
fun LibraryScreen(
    onLogin: (Platform) -> Unit,
    onOpenItem: (TaskWithFiles) -> Unit,
    onOpenFile: (DownloadedFileEntity) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val platform by viewModel.platform.collectAsStateWithLifecycle()
    val platforms by viewModel.platforms.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val actions = TaskActions(
        onRetry = { viewModel.redownload(it) },
        onDelete = { deleting = it },
        onLogin = onLogin,
        onCopyLink = { copyToClipboard(context, it) },
        onOpenLink = { openLink(context, it) },
        onOpen = { item -> onOpenItem(item) },
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                actions = {
                    IconButton(onClick = { grid = !grid }) {
                        Icon(
                            if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                            stringResource(if (grid) R.string.view_list else R.string.view_grid),
                        )
                    }
                    IconButton(onClick = { searching = !searching; if (!searching) viewModel.query.value = "" }) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.search))
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_history))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (grid) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                LibraryFilters(viewModel, searching, query, filter, platform, platforms)
                val files = items.flatMap { item -> item.files.filter { it.matches(filter) } }
                if (files.isEmpty()) {
                    EmptyState(Icons.Outlined.VideoLibrary, stringResource(R.string.library_empty_title), stringResource(R.string.library_empty_body))
                } else {
                    MediaGrid(files, onOpenFile)
                }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LibraryFilters(viewModel, searching, query, filter, platform, platforms) }
            if (items.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.VideoLibrary, stringResource(R.string.library_empty_title), stringResource(R.string.library_empty_body))
                }
            } else {
                items(items, key = { it.task.id }) { item ->
                    TaskCard(item, actions, Modifier.padding(horizontal = 16.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_body)) },
            confirmButton = { TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { id ->
        var alsoFiles by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_item)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_item_body))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoFiles, onCheckedChange = { alsoFiles = it })
                        Text(stringResource(R.string.delete_files_too))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.delete(id, alsoFiles); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LibraryFilters(
    viewModel: LibraryViewModel,
    searching: Boolean,
    query: String,
    filter: MediaFilter,
    platform: Platform?,
    platforms: List<Platform>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.search_history)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { viewModel.query.value = "" }) { Icon(Icons.Rounded.Close, null) }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(MediaFilter.entries.toList()) { f ->
                FilterChip(selected = filter == f, onClick = { viewModel.filter.value = f }, label = { Text(filterLabel(f)) })
            }
            items(platforms) { p ->
                FilterChip(
                    selected = platform == p,
                    onClick = { viewModel.platform.value = if (platform == p) null else p },
                    label = { Text(p.displayName) },
                )
            }
        }
    }
}

private fun DownloadedFileEntity.matches(filter: MediaFilter) = when (filter) {
    MediaFilter.ALL -> true
    MediaFilter.VIDEO -> mimeType.startsWith("video/")
    MediaFilter.AUDIO -> mimeType.startsWith("audio/")
    MediaFilter.IMAGE -> mimeType.startsWith("image/")
}

@Composable
private fun MediaGrid(files: List<DownloadedFileEntity>, onOpen: (DownloadedFileEntity) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(files, key = { it.id }) { file ->
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onOpen(file) },
                contentAlignment = Alignment.Center,
            ) {
                if (file.mimeType.startsWith("audio/")) {
                    Icon(Icons.Outlined.MusicNote, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    AsyncImage(model = file.uri, contentDescription = file.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (file.mimeType.startsWith("video/")) {
                    Icon(
                        Icons.Filled.PlayCircle, null,
                        Modifier.align(Alignment.BottomStart).padding(6.dp).size(22.dp),
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun filterLabel(f: MediaFilter) = when (f) {
    MediaFilter.ALL -> stringResource(R.string.filter_all)
    MediaFilter.VIDEO -> stringResource(R.string.filter_video)
    MediaFilter.AUDIO -> stringResource(R.string.filter_audio)
    MediaFilter.IMAGE -> stringResource(R.string.filter_image)
}

/** Opens the first file of a finished task in another app. */
fun openExternally(context: android.content.Context, item: TaskWithFiles) {
    item.files.firstOrNull()?.let { openExternally(context, it) }
}

fun openExternally(context: android.content.Context, f: DownloadedFileEntity) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse(f.uri), f.mimeType)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
