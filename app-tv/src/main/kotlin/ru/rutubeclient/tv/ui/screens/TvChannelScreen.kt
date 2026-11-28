package ru.rutubeclient.tv.ui.screens

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.rutubeclient.core.presentation.ChannelViewModel
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvLoadingBox
import ru.rutubeclient.tv.ui.TvVideoCard

@Composable
fun TvChannelScreen(
    viewModel: ChannelViewModel,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> TvLoadingBox()
        state.error != null -> TvErrorBox(
            message = state.error ?: "Ошибка",
            onRetry = viewModel::refresh,
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.authorAvatar != null) {
                        AsyncImage(
                            model = state.authorAvatar,
                            contentDescription = state.authorName,
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        androidx.tv.material3.Text(
                            state.authorName,
                            style = androidx.tv.material3.MaterialTheme.typography.headlineSmall,
                        )
                    }
                    androidx.tv.material3.Button(onClick = viewModel::toggleSubscription) {
                        androidx.tv.material3.Text(
                            if (state.isSubscribed) "Вы подписаны" else "Подписаться"
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            gridItemsIndexed(state.videos, key = { index, video -> "${video.id}_$index" }) { _, video ->
                TvVideoCard(video = video, onClick = { onVideoClick(video.id) })
            }
        }
    }
}
