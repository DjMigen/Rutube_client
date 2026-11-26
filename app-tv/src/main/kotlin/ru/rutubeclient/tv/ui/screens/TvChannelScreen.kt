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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvLoadingBox
import ru.rutubeclient.tv.ui.TvVideoCard

class TvChannelViewModel(
    private val authorId: Long,
    initialName: String,
    private val api: RuTubeApi,
    private val repository: LibraryRepository,
) : ViewModel() {

    data class UiState(
        val authorName: String = "",
        val authorAvatar: String? = null,
        val videos: List<Video> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val isSubscribed: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(authorName = initialName))
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            repository.isSubscribed(authorId).collect { subscribed ->
                _state.update { it.copy(isSubscribed = subscribed) }
            }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { api.getChannelVideos(authorId, 1) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            videos = page.items,
                            authorAvatar = it.authorAvatar
                                ?: page.items.firstOrNull()?.author?.avatarUrl,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                    }
                }
        }
    }

    fun toggleSubscription() {
        viewModelScope.launch {
            if (_state.value.isSubscribed) {
                repository.unsubscribe(authorId)
            } else {
                repository.subscribe(authorId, _state.value.authorName, _state.value.authorAvatar)
            }
        }
    }
}

@Composable
fun TvChannelScreen(
    viewModel: TvChannelViewModel,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> TvLoadingBox()
        state.error != null -> TvErrorBox(
            message = state.error ?: "Ошибка",
            onRetry = viewModel::load,
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
