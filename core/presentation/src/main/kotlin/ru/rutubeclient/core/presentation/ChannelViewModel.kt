package ru.rutubeclient.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.network.RuTubeApi

/**
 * Channel page shared by mobile and TV. TV passes `pagination = false`
 * (D-pad grids load a single page).
 */
class ChannelViewModel(
    private val authorId: Long,
    initialName: String,
    private val api: RuTubeApi,
    private val repository: LibraryRepository,
    private val pagination: Boolean = true,
) : ViewModel() {

    data class UiState(
        val authorName: String = "",
        val authorAvatar: String? = null,
        val videos: List<Video> = emptyList(),
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val hasNext: Boolean = true,
        val error: String? = null,
        val isSubscribed: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(authorName = initialName))
    val state = _state.asStateFlow()

    private var page = 1

    init {
        refresh()
        viewModelScope.launch {
            repository.isSubscribed(authorId).collect { subscribed ->
                _state.update { it.copy(isSubscribed = subscribed) }
            }
        }
    }

    fun refresh() {
        page = 1
        _state.update { it.copy(isLoading = true, error = null, hasNext = true) }
        load(append = false)
    }

    fun loadMore() {
        if (!pagination) return
        val s = _state.value
        if (!s.hasNext || s.isLoading || s.isLoadingMore) return
        page++
        load(append = true)
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

    private fun load(append: Boolean) {
        viewModelScope.launch {
            runCatching { api.getChannelVideos(authorId, page) }
                .onSuccess { newPage ->
                    _state.update { s ->
                        // Enrich the header with the channel avatar from the first video.
                        val avatar = s.authorAvatar
                            ?: newPage.items.firstOrNull()?.author?.avatarUrl
                        s.copy(
                            videos = if (append) s.videos + newPage.items else newPage.items,
                            authorAvatar = avatar,
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
