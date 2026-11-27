package ru.rutubeclient.tv.ui.screens.catalog

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import ru.rutubeclient.core.model.CatalogEntry
import ru.rutubeclient.core.model.CatalogTab
import ru.rutubeclient.core.model.TvShow
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvLoadingBox
import ru.rutubeclient.tv.ui.TvVideoCard

@Composable
fun TvCatalogScreen(
    viewModel: TvCatalogViewModel,
    onVideoClick: (String) -> Unit,
    onTvShowClick: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("Каталог", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(CatalogTab.entries.toTypedArray(), key = { it.name }) { tab ->
                val selected = state.selectedTab == tab
                androidx.tv.material3.Button(
                    onClick = { viewModel.selectTab(tab) },
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = if (selected) {
                        androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        androidx.tv.material3.ButtonDefaults.colors()
                    },
                ) {
                    Text(tab.label)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            state.isLoading && state.items.isEmpty() -> TvLoadingBox(Modifier.fillMaxSize())
            state.error != null && state.items.isEmpty() -> TvErrorBox(
                message = state.error ?: "Ошибка",
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItemsIndexed(
                    state.items,
                    key = { index, item -> "${item.stableId}_$index" },
                ) { _, item ->
                    when (item) {
                        is CatalogEntry.Show -> TvShowCard(
                            show = item.show,
                            onClick = { onTvShowClick(item.show.id) },
                        )
                        is CatalogEntry.Video -> TvVideoCard(
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
        onClick = onClick,
        modifier = modifier.width(180.dp),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
        ),
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
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    show.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = listOfNotNull(
                    show.yearStart?.take(4)?.takeIf { it.length == 4 },
                    if (show.seasonsCount > 0) "${show.seasonsCount} сез." else null,
                ).joinToString(" • ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val CatalogEntry.stableId: String
    get() = when (this) {
        is CatalogEntry.Show -> "show_${show.id}"
        is CatalogEntry.Video -> "video_${video.id}"
    }
