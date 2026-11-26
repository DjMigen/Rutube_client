package ru.rutubeclient.mobile.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rutubeclient.core.database.DownloadEntity
import ru.rutubeclient.mobile.ui.components.VideoListItem
import ru.rutubeclient.mobile.ui.formatBytes
import ru.rutubeclient.mobile.ui.formatDuration
import ru.rutubeclient.mobile.ui.formatMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onVideoClick: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val history by viewModel.history.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Библиотека") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("История") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Закладки") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Загрузки") })
        }

        when (tab) {
            0 -> HistoryTab(
                items = history,
                onVideoClick = onVideoClick,
                onRemove = viewModel::removeHistory,
                onClear = viewModel::clearHistory,
            )

            1 -> BookmarksTab(
                items = bookmarks,
                onVideoClick = onVideoClick,
                onRemove = viewModel::removeBookmark,
            )

            2 -> DownloadsTab(
                items = downloads,
                onVideoClick = onVideoClick,
                onRemove = viewModel::removeDownload,
            )
        }
    }
}

@Composable
private fun HistoryTab(
    items: List<ru.rutubeclient.core.database.HistoryEntity>,
    onVideoClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyTab("История просмотров пуста")
        return
    }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Очистить всё") }
        }
        LazyColumn {
            items(items, key = { it.videoId }) { entry ->
                val progress = if (entry.durationSec > 0) {
                    (entry.positionMs / 1000f / entry.durationSec).coerceIn(0f, 1f)
                } else null
                VideoListItem(
                    title = entry.title,
                    thumbnailUrl = entry.thumbnailUrl,
                    subtitle = buildString {
                        append(entry.authorName.orEmpty())
                        if (entry.positionMs > 0) {
                            append(" • остановлено на ${formatMs(entry.positionMs)}")
                        }
                    },
                    onClick = { onVideoClick(entry.videoId) },
                    trailing = {
                        IconButton(onClick = { onRemove(entry.videoId) }) {
                            Icon(Icons.Default.Close, contentDescription = "Удалить")
                        }
                    },
                )
                if (progress != null && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun BookmarksTab(
    items: List<ru.rutubeclient.core.database.BookmarkEntity>,
    onVideoClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyTab("Нет видео «Смотреть позже»")
        return
    }
    LazyColumn {
        items(items, key = { it.videoId }) { entry ->
            VideoListItem(
                title = entry.title,
                thumbnailUrl = entry.thumbnailUrl,
                subtitle = buildString {
                    append(entry.authorName.orEmpty())
                    val d = formatDuration(entry.durationSec)
                    if (d.isNotEmpty()) append(" • $d")
                },
                onClick = { onVideoClick(entry.videoId) },
                trailing = {
                    IconButton(onClick = { onRemove(entry.videoId) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить")
                    }
                },
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun DownloadsTab(
    items: List<DownloadEntity>,
    onVideoClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyTab("Нет скачанных видео")
        return
    }
    LazyColumn {
        items(items, key = { it.videoId }) { entry ->
            val subtitle = when (entry.state) {
                DownloadEntity.STATE_COMPLETED ->
                    "Скачано • ${entry.qualityHeight}p • ${formatBytes(entry.bytesDownloaded)}"

                DownloadEntity.STATE_DOWNLOADING, DownloadEntity.STATE_QUEUED -> {
                    if (entry.bytesTotal > 0) {
                        val pct = (entry.bytesDownloaded * 100 / entry.bytesTotal).toInt()
                        "Загрузка… $pct% (${formatBytes(entry.bytesDownloaded)})"
                    } else "В очереди…"
                }

                else -> "Ошибка загрузки"
            }
            Column {
                VideoListItem(
                    title = entry.title,
                    thumbnailUrl = entry.thumbnailUrl,
                    subtitle = subtitle,
                    onClick = {
                        // Completed downloads play offline from the cache.
                        if (entry.state == DownloadEntity.STATE_COMPLETED) {
                            onVideoClick(entry.videoId)
                        }
                    },
                    trailing = {
                        IconButton(onClick = { onRemove(entry.videoId) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить")
                        }
                    },
                )
                if (entry.state == DownloadEntity.STATE_DOWNLOADING && entry.bytesTotal > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (entry.bytesDownloaded.toFloat() / entry.bytesTotal).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun EmptyTab(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
    }
}
