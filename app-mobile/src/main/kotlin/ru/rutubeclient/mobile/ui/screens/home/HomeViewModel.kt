package ru.rutubeclient.mobile.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.mobile.ui.components.ContinueItem

class HomeViewModel(
    private val api: RuTubeApi,
    private val repository: LibraryRepository,
) : ViewModel() {

    enum class RowKind { VIDEOS, TOP10 }

    data class VideoRow(val title: String, val videos: List<Video>, val kind: RowKind = RowKind.VIDEOS)

    data class UiState(
        val hero: Video? = null,
        val heroStreamUrl: String? = null,
        val rows: List<VideoRow> = emptyList(),
        val continueWatching: List<ContinueItem> = emptyList(),
        val heroBookmarked: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        // Continue watching derived from local history.
        viewModelScope.launch {
            repository.history.collectLatest { history ->
                val items = history
                    .filter { it.isInProgress }
                    .map { ContinueItem(it.toVideo(), it.progress) }
                    .take(20)
                _state.update { it.copy(continueWatching = items) }
            }
        }
        // Keep hero bookmarked state in sync with bookmarks.
        viewModelScope.launch {
            repository.bookmarks.collectLatest {
                recomputeHeroBookmarked()
            }
        }
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
                    val categories = categoriesDeferred.await().take(6)

                    val topMoviesDeferred = async {
                        runCatching { api.getTopMovies(1).items }.getOrDefault(emptyList()).take(10)
                    }
                    val topSerialsDeferred = async {
                        runCatching { api.getTopSerials(1).items }.getOrDefault(emptyList()).take(10)
                    }

                    val rows = mutableListOf<VideoRow>()
                    if (feed.items.isNotEmpty()) rows += VideoRow("Рекомендации", feed.items)
                    val trending = feed.items.sortedByDescending { it.hits }.take(20)
                    if (trending.isNotEmpty()) rows += VideoRow("В тренде", trending)
                    topMoviesDeferred.await().let {
                        if (it.isNotEmpty()) rows += VideoRow("Топ-10 фильмов", it, RowKind.TOP10)
                    }
                    topSerialsDeferred.await().let {
                        if (it.isNotEmpty()) rows += VideoRow("Топ-10 сериалов", it, RowKind.TOP10)
                    }
                    val categoryRows = categories.map { category ->
                        async {
                            runCatching { api.getCategoryVideos(category.id, 1) }
                                .getOrNull()
                                ?.let { VideoRow(category.name, it.items) }
                        }
                    }.mapNotNull { it.await() }

                    val becauseRows = repository.history.first()
                        .take(2)
                        .mapNotNull { entry ->
                            val authorId = entry.authorId ?: return@mapNotNull null
                            runCatching { api.getChannelVideos(authorId, 1) }
                                .getOrNull()
                                ?.items
                                ?.filter { it.id != entry.videoId }
                                ?.take(10)
                                ?.let {
                                    val title = entry.title.take(24)
                                    VideoRow("Потому что вы смотрели «$title»", it)
                                }
                        }

                    rows + categoryRows + becauseRows
                }
            }
                .onSuccess { rows ->
                    val hero = rows.firstOrNull()?.videos?.firstOrNull()
                    _state.update {
                        it.copy(hero = hero, rows = rows, isLoading = false, error = null)
                    }
                    recomputeHeroBookmarked()
                    hero?.let { resolveHeroStream(it.id) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Ошибка загрузки",
                        )
                    }
                }
        }
    }

    fun refresh() = load()

    /** Returns a random video id from the loaded rows ("Удиви меня"). */
    fun surpriseMe(): String? =
        _state.value.rows.flatMap { it.videos }.randomOrNull()?.id

    /** Remove a video from the continue-watching row (drops its history entry). */
    fun hideContinue(videoId: String) {
        viewModelScope.launch { repository.removeHistory(videoId) }
    }

    fun toggleHeroBookmark() {
        val hero = _state.value.hero ?: return
        val bookmarked = _state.value.heroBookmarked
        viewModelScope.launch {
            if (bookmarked) {
                repository.removeBookmark(hero.id)
            } else {
                repository.addBookmark(hero)
            }
        }
    }

    private fun recomputeHeroBookmarked() {
        val heroId = _state.value.hero?.id ?: return
        viewModelScope.launch {
            val bookmarked = runCatching {
                repository.isBookmarked(heroId).first()
            }.getOrDefault(false)
            _state.update { it.copy(heroBookmarked = bookmarked) }
        }
    }

    private fun resolveHeroStream(videoId: String) {
        viewModelScope.launch {
            val url = runCatching { api.getStreamInfo(videoId).hlsUrl }.getOrNull()
            _state.update { it.copy(heroStreamUrl = url) }
        }
    }
}
