package ru.rutubeclient.mobile.ui.screens.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rutubeclient.mobile.ui.components.AvatarImage
import ru.rutubeclient.mobile.ui.components.ErrorBox
import ru.rutubeclient.mobile.ui.components.LoadingBox
import ru.rutubeclient.mobile.ui.components.PullToRefreshIndicator
import ru.rutubeclient.mobile.ui.components.PullToRefreshState
import ru.rutubeclient.mobile.ui.components.pullToRefresh
import ru.rutubeclient.mobile.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val refreshState = remember { PullToRefreshState() }

    // Pull-to-refresh is active when the ViewModel is loading but we already have videos
    // (i.e. not the initial empty-state load).
    val isRefreshing = state.isLoading && state.videos.isNotEmpty()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    // When a pull-to-refresh completes (loading ends), reset the pull offset.
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            refreshState.completeRefresh()
            refreshState.pullOffset.value = 0f
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.authorName, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
        )

        PullToRefreshIndicator(
            pullOffsetDp = if (isRefreshing || refreshState.pullOffset.value > 0f) {
                refreshState.pullOffset.value
            } else {
                0f
            },
        )

        when {
            state.isLoading && state.videos.isEmpty() -> LoadingBox(Modifier.fillMaxSize())
            state.error != null && state.videos.isEmpty() -> ErrorBox(
                message = state.error ?: "Ошибка",
                onRetry = viewModel::refresh,
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
                    .pullToRefresh({ gridState.canScrollBackward }, refreshState) { viewModel.refresh() },
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        AvatarImage(
                            url = state.authorAvatar,
                            name = state.authorName,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.authorName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${state.videos.size}+ видео",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = viewModel::toggleSubscription) {
                            Text(if (state.isSubscribed) "Вы подписаны" else "Подписаться")
                        }
                    }
                }
                gridItemsIndexed(state.videos, key = { index, video -> "${video.id}_$index" }) { _, video ->
                    VideoCard(video = video, onClick = { onVideoClick(video.id) })
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
