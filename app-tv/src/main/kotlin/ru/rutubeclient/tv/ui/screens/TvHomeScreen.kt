package ru.rutubeclient.tv.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvLoadingBox
import ru.rutubeclient.tv.ui.TvVideoRow

class TvHomeViewModel(private val api: RuTubeApi) : ViewModel() {

    data class VideoRow(val title: String, val videos: List<Video>)

    data class UiState(
        val rows: List<VideoRow> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val feedDeferred = async { api.getHomeFeed(1) }
                    val categoriesDeferred = async {
                        runCatching { api.getCategories() }.getOrDefault(emptyList())
                    }
                    val feed = feedDeferred.await()
                    val categories = categoriesDeferred.await().take(5)

                    val rows = mutableListOf(VideoRow("Рекомендации", feed.items))
                    // Load a row per category in parallel; skip empty ones.
                    val categoryRows = categories.map { category ->
                        async {
                            runCatching { api.getCategoryVideos(category.id, 1) }
                                .getOrNull()
                                ?.let { VideoRow(category.name, it.items) }
                        }
                    }.mapNotNull { it.await() }
                    rows + categoryRows
                }
            }
                .onSuccess { rows ->
                    _state.update { it.copy(rows = rows, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                    }
                }
        }
    }
}

/** TV home: vertical list of horizontal video rows. */
@Composable
fun TvHomeScreen(
    viewModel: TvHomeViewModel,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> TvLoadingBox()
        state.error != null -> TvErrorBox(
            message = state.error ?: "Ошибка",
            onRetry = viewModel::load,
        )

        else -> LazyColumn(Modifier.fillMaxSize()) {
            item { Spacer(Modifier.height(24.dp)) }
            state.rows.forEach { row ->
                item(key = row.title) {
                    TvVideoRow(
                        title = row.title,
                        videos = row.videos,
                        onVideoClick = onVideoClick,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
