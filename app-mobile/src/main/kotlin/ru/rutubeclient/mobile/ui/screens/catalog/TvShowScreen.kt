package ru.rutubeclient.mobile.ui.screens.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.rutubeclient.core.presentation.TvShowViewModel
import ru.rutubeclient.mobile.ui.components.ErrorBox
import ru.rutubeclient.mobile.ui.components.LoadingBox
import ru.rutubeclient.mobile.ui.components.VideoListItem
import ru.rutubeclient.mobile.ui.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvShowScreen(
    viewModel: TvShowViewModel,
    onBack: () -> Unit,
    onVideoClick: (videoId: String, nextVideoId: String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val show = state.show
    val seasonsToShow = remember(state.seasons) {
        state.seasons.filter { it != 0 }.ifEmpty { state.seasons }
    }
    val seasonsAvailable = seasonsToShow.size > 1
    val selectedSeason = state.selectedSeason
    val visibleEpisodes = state.episodes

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.show?.name ?: "Сериал", maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
        )

        when {
            state.isLoading && state.show == null -> LoadingBox(Modifier.fillMaxSize())
            state.error != null && state.show == null -> ErrorBox(
                message = state.error ?: "Ошибка",
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    TvShowHeader(state.show)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Эпизоды",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (seasonsAvailable) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(seasonsToShow) { season ->
                                FilterChip(
                                    selected = selectedSeason == season,
                                    onClick = { viewModel.selectSeason(season) },
                                    label = { Text(if (season == 0) "Все" else "$season сезон") },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                itemsIndexed(
                    visibleEpisodes,
                    key = { index, video -> "${video.id}_$index" },
                ) { index, video ->
                    val nextId = visibleEpisodes.getOrNull(index + 1)?.id
                    VideoListItem(
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        subtitle = listOfNotNull(
                            video.author?.name,
                            formatDuration(video.durationSec),
                        ).joinToString(" • "),
                        onClick = { onVideoClick(video.id, nextId) },
                    )
                }

                if (state.isLoadingMore) {
                    item {
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

@Composable
private fun TvShowHeader(show: ru.rutubeclient.core.model.TvShow?) {
    if (show == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        AsyncImage(
            model = show.posterUrl,
            contentDescription = show.name,
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                show.name,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            val meta = buildTvShowMetaLine(show)
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            val description = show.description
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Best-effort season number extraction from an episode title. */
private fun extractSeason(title: String): Int? {
    val patterns = listOf(
        Regex("""(\d+)\s*сезон""", RegexOption.IGNORE_CASE),
        Regex("""сезон\s*(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""S(\d{1,2})E\d+""", RegexOption.IGNORE_CASE),
        Regex("""(\d{1,2})x\d+"""),
    )
    for (pattern in patterns) {
        val match = pattern.find(title) ?: continue
        return match.groupValues[1].toIntOrNull()
    }
    return null
}

private fun buildTvShowMetaLine(show: ru.rutubeclient.core.model.TvShow): String {
    return listOfNotNull(
        show.yearStart?.take(4)?.takeIf { it.length == 4 },
        show.genres.firstOrNull(),
        if (show.seasonsCount > 0) "${show.seasonsCount} сез." else null,
    ).joinToString(" • ")
}
