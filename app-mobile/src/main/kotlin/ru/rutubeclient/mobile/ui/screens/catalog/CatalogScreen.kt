package ru.rutubeclient.mobile.ui.screens.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.rutubeclient.core.model.TvShow
import ru.rutubeclient.mobile.ui.components.ErrorBox
import ru.rutubeclient.mobile.ui.components.LoadingBox
import ru.rutubeclient.mobile.ui.components.PullToRefreshIndicator
import ru.rutubeclient.mobile.ui.components.PullToRefreshState
import ru.rutubeclient.mobile.ui.components.VideoCard
import ru.rutubeclient.mobile.ui.components.pullToRefresh
import ru.rutubeclient.mobile.ui.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onVideoClick: (String) -> Unit,
    onTvShowClick: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val refreshState = remember { PullToRefreshState() }

    val isRefreshing = state.isLoading && state.items.isNotEmpty()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            refreshState.completeRefresh()
            refreshState.pullOffset.value = 0f
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Каталог") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(CatalogTab.entries.toTypedArray(), key = { it.name }) { tab ->
                FilterChip(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    label = { Text(tab.label) },
                )
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
            state.isLoading && state.items.isEmpty() ->
                LoadingBox(Modifier.weight(1f))

            state.error != null && state.items.isEmpty() ->
                ErrorBox(
                    message = state.error ?: "Ошибка",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxSize()
                    .pullToRefresh({ gridState.canScrollBackward }, refreshState) { viewModel.refresh() },
            ) {
                gridItemsIndexed(
                    state.items,
                    key = { index, item -> "${item.stableId}_$index" },
                ) { _, item ->
                    when (item) {
                        is CatalogItem.Show -> TvShowCard(
                            show = item.show,
                            onClick = { onTvShowClick(item.show.id) },
                        )
                        is CatalogItem.Video -> VideoCard(
                            video = item.video,
                            onClick = { onVideoClick(item.video.id) },
                        )
                    }
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvShowCard(
    show: TvShow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = show.posterUrl,
                    contentDescription = show.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    contentScale = ContentScale.Crop,
                )
                val meta = buildTvShowMeta(show)
                if (meta.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Text(
                            meta,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    show.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val subtitle = listOfNotNull(
                    show.yearStart?.take(4)?.takeIf { it.length == 4 },
                    show.genres.firstOrNull(),
                ).joinToString(" • ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun buildTvShowMeta(show: TvShow): String {
    return when {
        show.seasonsCount > 0 -> "${show.seasonsCount} сез."
        else -> ""
    }
}

private val CatalogItem.stableId: String
    get() = when (this) {
        is CatalogItem.Show -> "show_${show.id}"
        is CatalogItem.Video -> "video_${video.id}"
    }
