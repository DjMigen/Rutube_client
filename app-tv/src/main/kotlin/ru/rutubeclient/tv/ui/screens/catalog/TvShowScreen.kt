package ru.rutubeclient.tv.ui.screens.catalog

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ru.rutubeclient.core.model.TvShow
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvLoadingBox
import ru.rutubeclient.tv.ui.TvVideoCard

@Composable
fun TvShowScreen(
    viewModel: TvShowViewModel,
    onVideoClick: (videoId: String, nextVideoId: String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val show = state.show
    val seasonsToShow = remember(state.seasons) {
        state.seasons.filter { it != 0 }.ifEmpty { state.seasons }
    }
    val seasonsAvailable = seasonsToShow.size > 1
    val selectedSeason = state.selectedSeason
    val visibleEpisodes = state.episodes

    LaunchedEffect(Unit) {
        // TV show screen loads everything on init; episodes are paginated via loadMore.
    }

    when {
        state.isLoading && state.show == null -> TvLoadingBox(Modifier.fillMaxSize())
        state.error != null && state.show == null -> TvErrorBox(
            message = state.error ?: "Ошибка",
            onRetry = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        )

        else -> Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            TvShowHeader(state.show)
            Spacer(Modifier.height(24.dp))
            Text("Эпизоды", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (seasonsAvailable) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seasonsToShow) { season ->
                        Button(onClick = { viewModel.selectSeason(season) }) {
                            Text(
                                if (selectedSeason == season) "• ${if (season == 0) "Все" else "$season сезон"}"
                                else if (season == 0) "Все" else "$season сезон"
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                itemsIndexed(
                    visibleEpisodes,
                    key = { index, video -> "${video.id}_$index" },
                ) { index, video ->
                    val nextId = visibleEpisodes.getOrNull(index + 1)?.id
                    TvVideoCard(
                        video = video,
                        onClick = { onVideoClick(video.id, nextId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvShowHeader(show: TvShow?) {
    if (show == null) return

    Row(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = show.posterUrl,
            contentDescription = show.name,
            modifier = Modifier
                .width(180.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(show.name, style = MaterialTheme.typography.headlineSmall)
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
                    maxLines = 8,
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

private fun buildTvShowMetaLine(show: TvShow): String {
    return listOfNotNull(
        show.yearStart?.take(4)?.takeIf { it.length == 4 },
        show.genres.firstOrNull(),
        if (show.seasonsCount > 0) "${show.seasonsCount} сез." else null,
    ).joinToString(" • ")
}
