package ru.rutubeclient.mobile.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rutubeclient.mobile.ui.components.ContinueWatchingRow
import ru.rutubeclient.mobile.ui.components.ErrorBox
import ru.rutubeclient.mobile.ui.components.HeroBanner
import ru.rutubeclient.mobile.ui.components.LoadingBox
import ru.rutubeclient.mobile.ui.components.PullToRefreshIndicator
import ru.rutubeclient.mobile.ui.components.PullToRefreshState
import ru.rutubeclient.mobile.ui.components.Top10Row
import ru.rutubeclient.mobile.ui.components.VideoRow
import ru.rutubeclient.mobile.ui.components.pullToRefresh

/** Netflix-style home: hero banner, continue-watching and horizontal rows. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (String) -> Unit,
    onWatchFromStart: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val refreshState = remember { PullToRefreshState() }

    val isRefreshing = state.isLoading && state.rows.isNotEmpty()

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            refreshState.completeRefresh()
            refreshState.pullOffset.value = 0f
        }
    }

    when {
        state.isLoading && state.rows.isEmpty() ->
            LoadingBox(Modifier.fillMaxSize())

        state.error != null && state.rows.isEmpty() ->
            ErrorBox(
                message = state.error ?: "Ошибка",
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            )

        else -> Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
            PullToRefreshIndicator(
                pullOffsetDp = if (isRefreshing || refreshState.pullOffset.value > 0f) {
                    refreshState.pullOffset.value
                } else {
                    0f
                },
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .pullToRefresh({ listState.canScrollBackward }, refreshState) { viewModel.refresh() },
            ) {
                val hero = state.hero
                if (hero != null) {
                    item(key = "hero") {
                        HeroBanner(
                            video = hero,
                            isBookmarked = state.heroBookmarked,
                            onPlay = { onVideoClick(hero.id) },
                            onToggleList = viewModel::toggleHeroBookmark,
                            previewStreamUrl = state.heroStreamUrl,
                        )
                    }
                }
                if (state.continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        Spacer(Modifier.height(20.dp))
                        ContinueWatchingRow(
                            title = "Продолжить просмотр",
                            items = state.continueWatching,
                            onVideoClick = onVideoClick,
                            onWatchFromStart = onWatchFromStart,
                            onRemove = viewModel::hideContinue,
                        )
                    }
                }
                state.rows.forEach { row ->
                    item(key = "row_${row.title}") {
                        Spacer(Modifier.height(20.dp))
                        if (row.kind == HomeViewModel.RowKind.TOP10) {
                            Top10Row(
                                title = row.title,
                                videos = row.videos,
                                onVideoClick = onVideoClick,
                            )
                        } else {
                            VideoRow(
                                title = row.title,
                                videos = row.videos,
                                onVideoClick = onVideoClick,
                            )
                        }
                    }
                }
            }
            }
            FloatingActionButton(
                onClick = { viewModel.surpriseMe()?.let(onVideoClick) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = "Удиви меня")
            }
        }
    }
}
