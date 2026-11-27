package ru.rutubeclient.tv.ui.screens.catalog

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
        val seasons: List<Int> = emptyList(),
        val selectedSeason: Int? = null,
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
        _state.update { it.copy(isLoading = true, error = null, hasNext = true, episodes = emptyList()) }
        load(append = false, seasonOverride = null)
    }

    fun selectSeason(season: Int) {
        val current = _state.value.selectedSeason
        if (current == season) return
        page = 1
        _state.update {
            it.copy(selectedSeason = season, episodes = emptyList(), isLoading = true, error = null, hasNext = true)
        }
        load(append = false, seasonOverride = season)
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasNext || s.isLoading || s.isLoadingMore || s.error != null) return
        page++
        load(append = true, seasonOverride = s.selectedSeason)
    }

    private fun load(append: Boolean, seasonOverride: Int?) {
        viewModelScope.launch {
            val result = runCatching {
                val show = if (!append || _state.value.show == null) {
                    api.getTvShow(tvId)
                } else {
                    _state.value.show!!
                }
                val seasons = if (!append || _state.value.seasons.isEmpty()) {
                    api.getTvShowSeasons(tvId)
                } else {
                    _state.value.seasons
                }
                val season = seasonOverride ?: _state.value.selectedSeason ?: seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull()
                val videos = api.getTvShowVideos(tvId, page, season)
                show to (seasons to (season to videos))
            }
            result
                .onSuccess { (show, seasonsAndVideos) ->
                    val (seasons, seasonAndVideos) = seasonsAndVideos
                    val (season, videos) = seasonAndVideos
                    _state.update {
                        it.copy(
                            show = show,
                            seasons = seasons,
                            selectedSeason = season,
                            episodes = if (append) it.episodes + videos.items else videos.items,
                            hasNext = videos.hasNext,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    runCatching {
                        val show = _state.value.show ?: api.getTvShow(tvId)
                        val videos = api.getTvShowVideos(tvId, page, null)
                        show to videos
                    }.onSuccess { (show, videos) ->
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
                    }.onFailure { e2 ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                error = e2.message ?: e.message ?: "Ошибка загрузки",
                            )
                        }
                    }
                }
        }
    }
}