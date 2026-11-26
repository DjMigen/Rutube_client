package ru.rutubeclient.mobile.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.model.VideoCategory
import ru.rutubeclient.core.network.RuTubeApi

class HomeViewModel(private val api: RuTubeApi) : ViewModel() {

    data class UiState(
        val videos: List<Video> = emptyList(),
        val categories: List<VideoCategory> = emptyList(),
        val selectedCategoryId: Int? = null,
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val hasNext: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var page = 1

    init {
        viewModelScope.launch {
            runCatching { api.getCategories() }
                .onSuccess { cats -> _state.update { it.copy(categories = cats) } }
        }
        refresh()
    }

    fun selectCategory(categoryId: Int?) {
        if (_state.value.selectedCategoryId == categoryId) return
        _state.update { it.copy(selectedCategoryId = categoryId, videos = emptyList()) }
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
            val category = _state.value.selectedCategoryId
            val result = runCatching {
                if (category == null) api.getHomeFeed(page) else api.getCategoryVideos(category, page)
            }
            result
                .onSuccess { newPage ->
                    _state.update {
                        it.copy(
                            videos = if (append) it.videos + newPage.items else newPage.items,
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
}
