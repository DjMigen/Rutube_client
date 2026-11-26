package ru.rutubeclient.mobile.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rutubeclient.mobile.ui.components.ErrorBox
import ru.rutubeclient.mobile.ui.components.LoadingBox
import ru.rutubeclient.mobile.ui.components.PullToRefreshIndicator
import ru.rutubeclient.mobile.ui.components.PullToRefreshState
import ru.rutubeclient.mobile.ui.components.pullToRefresh
import ru.rutubeclient.mobile.ui.components.VideoCard

/** Main feed with category chips and endless scrolling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val refreshState = remember { PullToRefreshState() }

    // Pull-to-refresh is active when the ViewModel is loading but we already have videos
    // (i.e. not the initial empty-state load).
    val isRefreshing = state.isLoading && state.videos.isNotEmpty()

    // Trigger loading of the next page near the end of the list.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // When a pull-to-refresh completes (loading ends), reset the pull offset.
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            refreshState.completeRefresh()
            refreshState.pullOffset.value = 0f
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("RuTube") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        if (state.categories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("Все") },
                    )
                }
                rowItemsIndexed(state.categories, key = { index, category -> "${category.id}_$index" }) { _, category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }

        PullToRefreshIndicator(
            pullOffsetDp = if (isRefreshing || refreshState.pullOffset.value > 0f) {
                refreshState.pullOffset.value
            } else {
                0f
            },
        )

        when {
            state.isLoading && state.videos.isEmpty() ->
                LoadingBox(Modifier.weight(1f))

            state.error != null && state.videos.isEmpty() ->
                ErrorBox(
                    message = state.error ?: "Ошибка",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxSize()
                    .pullToRefresh({ gridState.canScrollBackward }, refreshState) { viewModel.refresh() },
            ) {
                gridItemsIndexed(state.videos, key = { index, video -> "${video.id}_$index" }) { _, video ->
                    VideoCard(video = video, onClick = { onVideoClick(video.id) })
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
