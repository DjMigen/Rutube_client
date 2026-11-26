package ru.rutubeclient.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.DownloadEntity
import ru.rutubeclient.core.database.LibraryRepository

class TvLibraryViewModel(repository: LibraryRepository) : ViewModel() {

    data class UiState(
        val selectedTab: Int = 0,
        val history: List<ru.rutubeclient.core.database.HistoryEntity> = emptyList(),
        val bookmarks: List<ru.rutubeclient.core.database.BookmarkEntity> = emptyList(),
        val downloads: List<DownloadEntity> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.history,
                repository.bookmarks,
                repository.downloads,
            ) { history, bookmarks, downloads ->
                Triple(history, bookmarks, downloads)
            }.collect { (history, bookmarks, downloads) ->
                _state.value = _state.value.copy(
                    history = history,
                    bookmarks = bookmarks,
                    downloads = downloads,
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTab = index)
    }
}

private val tabs = listOf("История", "Избранное", "Загрузки")

@Composable
fun TvLibraryScreen(
    viewModel: TvLibraryViewModel,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Библиотека", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            rowItemsIndexed(tabs) { index, title ->
                Button(
                    onClick = { viewModel.selectTab(index) },
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        if (state.selectedTab == index) "▸ $title" else title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val items = when (state.selectedTab) {
            0 -> state.history
            1 -> state.bookmarks
            2 -> state.downloads
            else -> emptyList()
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Здесь пока ничего нет",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.selectedTab) {
                    0 -> gridItemsIndexed(state.history, key = { index, item -> "h_${item.videoId}_$index" }) { _, item ->
                        HistoryCard(item) { onVideoClick(item.videoId) }
                    }
                    1 -> gridItemsIndexed(state.bookmarks, key = { index, item -> "b_${item.videoId}_$index" }) { _, item ->
                        BookmarkCard(item) { onVideoClick(item.videoId) }
                    }
                    2 -> gridItemsIndexed(state.downloads, key = { index, item -> "d_${item.videoId}_$index" }) { _, item ->
                        DownloadCard(item) { onVideoClick(item.videoId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItemCard(
    imageUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
        ),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null && progress > 0f) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: ru.rutubeclient.core.database.HistoryEntity,
    onClick: () -> Unit,
) {
    val subtitle = buildString {
        append(entry.authorName.orEmpty())
        if (entry.positionMs > 0) {
            if (isNotEmpty()) append(" • ")
            append("остановлено на ${formatMs(entry.positionMs)}")
        }
    }
    val progress = if (entry.durationSec > 0) {
        (entry.positionMs / 1000f / entry.durationSec).coerceIn(0f, 1f)
    } else null
    LibraryItemCard(
        imageUrl = entry.thumbnailUrl,
        title = entry.title,
        subtitle = subtitle,
        onClick = onClick,
        progress = progress,
    )
}

@Composable
private fun BookmarkCard(
    entry: ru.rutubeclient.core.database.BookmarkEntity,
    onClick: () -> Unit,
) {
    val subtitle = buildString {
        append(entry.authorName.orEmpty())
        val duration = formatDuration(entry.durationSec)
        if (duration.isNotEmpty()) {
            if (isNotEmpty()) append(" • ")
            append(duration)
        }
    }
    LibraryItemCard(
        imageUrl = entry.thumbnailUrl,
        title = entry.title,
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
private fun DownloadCard(
    entry: DownloadEntity,
    onClick: () -> Unit,
) {
    val subtitle = when (entry.state) {
        DownloadEntity.STATE_COMPLETED ->
            "Скачано • ${entry.qualityHeight}p • ${formatBytes(entry.bytesDownloaded)}"
        DownloadEntity.STATE_DOWNLOADING, DownloadEntity.STATE_QUEUED -> {
            val pct = if (entry.bytesTotal > 0) {
                (entry.bytesDownloaded * 100 / entry.bytesTotal).toInt()
            } else 0
            "Загрузка • $pct%"
        }
        else -> "Ошибка загрузки"
    }
    val progress = if (entry.bytesTotal > 0) {
        (entry.bytesDownloaded.toFloat() / entry.bytesTotal).coerceIn(0f, 1f)
    } else null
    LibraryItemCard(
        imageUrl = entry.thumbnailUrl,
        title = entry.title,
        subtitle = subtitle,
        onClick = onClick,
        progress = progress,
    )
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb КБ"
    val mb = kb / 1024
    if (mb < 1024) return "$mb МБ"
    val gb = mb / 1024
    return String.format("%.2f ГБ", gb.toFloat())
}
