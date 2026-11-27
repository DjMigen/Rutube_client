package ru.rutubeclient.mobile.ui.screens.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.DownloadEntity
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.Comment
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.model.VideoDetails
import ru.rutubeclient.core.model.parseSeriesInfo
import ru.rutubeclient.core.model.normalizeSeriesTitle
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.core.network.SeriesDiscovery
import ru.rutubeclient.core.network.VideoSession
import ru.rutubeclient.mobile.download.DownloadsManager

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewModel(
    val videoId: String,
    private val api: RuTubeApi,
    private val repository: LibraryRepository,
    private val downloadsManager: DownloadsManager,
    private val startFromBeginning: Boolean = false,
    private val nextVideoId: String? = null,
) : ViewModel() {

    data class UiState(
        val details: VideoDetails? = null,
        /** Freshly resolved signed HLS URL; null until loaded. */
        val streamUrl: String? = null,
        /** Bumped on every stream re-resolve so the player re-sets the item. */
        val streamVersion: Int = 0,
        val startPositionMs: Long = 0L,
        val isLoading: Boolean = true,
        val error: String? = null,
        /** Set when playback failed; the UI should re-attach with the new URL. */
        val playbackFailed: Boolean = false,
        val comments: List<Comment> = emptyList(),
        val commentsHasNext: Boolean = false,
        val commentsLoading: Boolean = false,
        val isBookmarked: Boolean = false,
        val isSubscribed: Boolean = false,
        /** Download state from Room (null = not in downloads). */
        val download: DownloadEntity? = null,
        val channelVideos: List<Video> = emptyList(),
        val similarVideos: List<Video> = emptyList(),
        val nextVideoId: String? = null,
        val seriesEpisodes: List<Video> = emptyList(),
        val seriesSeasons: List<Int> = emptyList(),
        val selectedSeriesSeason: Int? = null,
        val rating: ru.rutubeclient.core.database.RatingEntity? = null,
        val actionMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** Scope that survives onCleared() for the final position write. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var commentsPage = 1
    private var lastSavedPositionMs = 0L

    init {
        loadVideo()
        observeLocalState()
    }

    fun loadVideo() {
        _state.update { it.copy(isLoading = true, error = null, playbackFailed = false) }
        viewModelScope.launch {
            runCatching { VideoSession(api).load(videoId) }
                .onSuccess { loaded ->
                    val details = loaded.details
                    val history = repository.getHistoryEntry(videoId)
                    val rating = repository.getRating(videoId)
                    _state.update {
                        it.copy(
                            details = details,
                            streamUrl = loaded.streamUrl,
                            streamVersion = it.streamVersion + 1,
                            startPositionMs = if (startFromBeginning) 0L else history?.positionMs ?: 0L,
                            nextVideoId = this@VideoViewModel.nextVideoId,
                            rating = rating,
                            isLoading = false,
                            playbackFailed = false,
                        )
                    }
                    loadComments(reset = true)
                    loadChannelVideos(details)
                    loadSimilarVideos(details)
                    viewModelScope.launch {
                        SeriesDiscovery(api).discover(details, videoId, nextVideoId)
                            ?.let { result ->
                                _state.update {
                                    it.copy(
                                        seriesEpisodes = result.seriesEpisodes,
                                        seriesSeasons = result.seriesSeasons,
                                        selectedSeriesSeason = result.selectedSeriesSeason,
                                        nextVideoId = result.nextVideoId ?: it.nextVideoId,
                                    )
                                }
                            }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                    }
                }
        }
    }

    /** Resolves a fresh signed stream URL. Called on load and after playback errors (expiry). */
    private suspend fun resolveStream() {
        runCatching { VideoSession(api).resolveStream(videoId) }
            .onSuccess { url ->
                _state.update {
                    it.copy(
                        streamUrl = url,
                        streamVersion = it.streamVersion + 1,
                        playbackFailed = false,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(error = e.message ?: "Поток недоступен", playbackFailed = true)
                }
            }
    }

    /** Called by the player error listener — likely an expired signature; re-resolve once. */
    fun onPlaybackError(currentPositionMs: Long) {
        lastSavedPositionMs = currentPositionMs
        viewModelScope.launch { resolveStream() }
    }

    fun savePosition(positionMs: Long) {
        val details = _state.value.details ?: return
        if (positionMs <= 0L) return
        lastSavedPositionMs = positionMs
        viewModelScope.launch { VideoSession(api).savePosition(repository, details, positionMs) }
    }

    /** Fire-and-forget save that survives ViewModel teardown. */
    fun savePositionFinal(positionMs: Long) {
        val details = _state.value.details ?: return
        if (positionMs <= 0L) return
        ioScope.launch { VideoSession(api).savePosition(repository, details, positionMs) }
    }

    fun toggleBookmark() {
        val details = _state.value.details ?: return
        viewModelScope.launch {
            if (_state.value.isBookmarked) {
                repository.removeBookmark(videoId)
            } else {
                repository.addBookmark(details)
            }
        }
    }

    fun toggleSubscription() {
        val author = _state.value.details?.author ?: return
        viewModelScope.launch {
            if (_state.value.isSubscribed) {
                repository.unsubscribe(author.id)
            } else {
                repository.subscribe(author.id, author.name, author.avatarUrl)
            }
        }
    }

    fun download(qualityHeight: Int) {
        val details = _state.value.details ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionMessage = "Добавление в загрузки…") }
            downloadsManager.enqueue(details, qualityHeight)
                .onSuccess {
                    _state.update { it.copy(actionMessage = "Загрузка началась") }
                    // Smart download: also queue the next episode if available.
                    state.value.nextVideoId?.let { nextId ->
                        runCatching { api.getVideo(nextId) }
                            .getOrNull()
                            ?.let { nextDetails -> downloadsManager.enqueue(nextDetails, qualityHeight) }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(actionMessage = "Не удалось скачать: ${e.message}")
                    }
                }
        }
    }

    fun consumeActionMessage() {
        _state.update { it.copy(actionMessage = null) }
    }

    fun loadMoreComments() {
        val s = _state.value
        if (!s.commentsHasNext || s.commentsLoading) return
        commentsPage++
        loadComments(reset = false)
    }

    private fun loadComments(reset: Boolean) {
        if (reset) commentsPage = 1
        _state.update { it.copy(commentsLoading = true) }
        viewModelScope.launch {
            runCatching { api.getComments(videoId, commentsPage) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            comments = if (reset) page.items else it.comments + page.items,
                            commentsHasNext = page.hasNext,
                            commentsLoading = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(commentsLoading = false) }
                }
        }
    }

    private fun loadSimilarVideos(details: VideoDetails) {
        val categoryId = details.category?.id ?: return
        viewModelScope.launch {
            runCatching { api.getCategoryVideos(categoryId, 1) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(similarVideos = page.items.filter { v -> v.id != videoId }.take(10))
                    }
                }
        }
    }

    private fun loadChannelVideos(details: VideoDetails) {
        val authorId = details.author?.id ?: return
        viewModelScope.launch {
            runCatching { api.getChannelVideos(authorId, 1) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(channelVideos = page.items.filter { v -> v.id != videoId }.take(10))
                    }
                }
        }
    }

    private fun observeLocalState() {
        viewModelScope.launch {
            repository.isBookmarked(videoId).collect { bookmarked ->
                _state.update { it.copy(isBookmarked = bookmarked) }
            }
        }
        viewModelScope.launch {
            // Subscription state depends on the author — observe once details arrive.
            _state.map { it.details?.author?.id }
                .distinctUntilChanged()
                .flatMapLatest { authorId ->
                    if (authorId != null) repository.isSubscribed(authorId) else flowOf(false)
                }
                .collect { subscribed ->
                    _state.update { it.copy(isSubscribed = subscribed) }
                }
        }
        viewModelScope.launch {
            repository.downloads.map { list -> list.firstOrNull { it.videoId == videoId } }
                .collect { download ->
                    _state.update { it.copy(download = download) }
                }
        }
    }

    fun rate(liked: Boolean) {
        viewModelScope.launch {
            repository.rateVideo(videoId, liked)
            _state.update { it.copy(rating = ru.rutubeclient.core.database.RatingEntity(videoId, liked, System.currentTimeMillis())) }
        }
    }

    fun removeRating() {
        viewModelScope.launch {
            repository.removeRating(videoId)
            _state.update { it.copy(rating = null) }
        }
    }

    fun selectSeriesSeason(season: Int) {
        _state.update { it.copy(selectedSeriesSeason = season) }
    }

}
