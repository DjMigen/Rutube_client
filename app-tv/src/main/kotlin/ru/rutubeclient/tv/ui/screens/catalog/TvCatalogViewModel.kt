package ru.rutubeclient.tv.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.model.Page
import ru.rutubeclient.core.model.TvShow
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.network.RuTubeApi

sealed interface TvCatalogItem {
    data class Show(val show: TvShow) : TvCatalogItem
    data class Video(val video: ru.rutubeclient.core.model.Video) : TvCatalogItem
}

enum class TvCatalogTab(val label: String) {
    ALL("Все"),
    SERIES("Сериалы"),
    TV_SHOWS("ТВ-шоу"),
    MOVIES("Фильмы"),
    CARTOONS("Мультики"),
    ANIME("Аниме"),
}

class TvCatalogViewModel(private val api: RuTubeApi) : ViewModel() {

    data class UiState(
        val items: List<TvCatalogItem> = emptyList(),
        val selectedTab: TvCatalogTab = TvCatalogTab.ALL,
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

    fun selectTab(tab: TvCatalogTab) {
        if (_state.value.selectedTab == tab) return
        _state.update { it.copy(selectedTab = tab, items = emptyList()) }
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
            val tab = _state.value.selectedTab
            val result = runCatching {
                when (tab) {
                    TvCatalogTab.ALL -> api.getTvShows(page).toCatalogItems { true }
                    TvCatalogTab.SERIES -> api.getTvShows(page).toCatalogItems { it.type == "series" }
                    TvCatalogTab.TV_SHOWS -> api.getTvShows(page).toCatalogItems { it.type == "tvshow" }
                    TvCatalogTab.MOVIES -> api.getTopMovies(page).toVideoCatalogItems()
                    TvCatalogTab.CARTOONS -> api.getTopCartoons(page).toVideoCatalogItems()
                    TvCatalogTab.ANIME -> api.getTopAnime(page).toVideoCatalogItems()
                }
            }
            result
                .onSuccess { newPage ->
                    _state.update {
                        it.copy(
                            items = if (append) it.items + newPage.items else newPage.items,
                            hasNext = newPage.hasNext,
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

    private fun Page<TvShow>.toCatalogItems(
        filter: (TvShow) -> Boolean,
    ): Page<TvCatalogItem> = Page(
        items = items.filter(filter).map(TvCatalogItem::Show),
        hasNext = hasNext,
        page = page,
    )

    private fun Page<Video>.toVideoCatalogItems(): Page<TvCatalogItem> = Page(
        items = items.map(TvCatalogItem::Video),
        hasNext = hasNext,
        page = page,
    )
}
