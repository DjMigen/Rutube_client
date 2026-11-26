package ru.rutubeclient.mobile.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.model.TvShow
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.network.RuTubeApi

class TvShowViewModel(
    private val tvId: Int,
    private val api: RuTubeApi,
) : ViewModel() {

    data class UiState(
        val show: TvShow? = null,
        val episodes: List<Video> = emptyList(),
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val hasNext: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var page = 1

    init {
        refresh()
    }

    fun refresh() {
        page = 1
        _state.update { it.copy(isLoading = true, error = null, hasNext = true) }
        load(append = false)
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasNext || s.isLoading || s.isLoadingMore || s.error != null) return
        page++
        load(append = true)
    }

    private fun load(append: Boolean) {
        viewModelScope.launch {
            val result = runCatching {
                val show = if (!append || _state.value.show == null) {
                    api.getTvShow(tvId)
                } else {
                    _state.value.show!!
                }
                val videos = api.getTvShowVideos(tvId, page)
                show to videos
            }
            result
                .onSuccess { (show, videos) ->
                    _state.update {
                        it.copy(
                            show = show,
                            episodes = if (append) it.episodes + videos.items else videos.items,
                            hasNext = videos.hasNext,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = e.message ?: "Ошибка загрузки",
                        )
                    }
                }
        }
    }
}
