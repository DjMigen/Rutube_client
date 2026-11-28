package ru.rutubeclient.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.model.VideoDetails
import ru.rutubeclient.core.model.episodeOf
import ru.rutubeclient.core.model.episodesForSeason
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.core.network.SeriesDiscovery
import ru.rutubeclient.core.player.PlaybackEvent
import ru.rutubeclient.core.player.PlaybackSession
import ru.rutubeclient.core.player.PlaybackStatus
import ru.rutubeclient.core.player.QualityOption
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvLoadingBox

class TvVideoViewModel(
    val videoId: String,
    private val api: RuTubeApi,
    private val repository: LibraryRepository,
    private val startFromBeginning: Boolean = false,
    private val nextVideoId: String? = null,
) : ViewModel() {

    data class UiState(
        val details: VideoDetails? = null,
        /** Saved playback position to resume from; 0 = from the beginning. */
        val startPositionMs: Long = 0L,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isBookmarked: Boolean = false,
        val nextVideoId: String? = null,
        val rating: ru.rutubeclient.core.database.RatingEntity? = null,
        val seriesEpisodes: List<Video> = emptyList(),
        val seriesSeasons: List<Int> = emptyList(),
        val selectedSeriesSeason: Int? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
        observeBookmarks()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { api.getVideo(videoId) }
                .onSuccess { details ->
                    val history = repository.getHistoryEntry(videoId)
                    _state.update {
                        it.copy(
                            details = details,
                            startPositionMs = if (startFromBeginning) 0L else history?.positionMs ?: 0L,
                            nextVideoId = this@TvVideoViewModel.nextVideoId,
                            rating = repository.getRating(videoId),
                            isLoading = false,
                        )
                    }
                    // Discover the series episodes row (and next episode) on every
                    // load, like mobile — a navigation-provided next still wins via
                    // the `?: it.nextVideoId` fallback.
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

    /**
     * Position sink for the playback session. Suspend so the session can
     * carry the final write through its own NonCancellable scope.
     */
    suspend fun persistPosition(positionMs: Long) {
        val details = _state.value.details ?: return
        if (positionMs <= 0L) return
        repository.savePosition(details, positionMs)
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

    fun rate(liked: Boolean) {
        viewModelScope.launch {
            repository.rateVideo(videoId, liked)
            _state.update {
                it.copy(
                    rating = ru.rutubeclient.core.database.RatingEntity(
                        videoId,
                        liked,
                        System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun removeRating() {
        viewModelScope.launch {
            repository.removeRating(videoId)
            _state.update { it.copy(rating = null) }
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            repository.isBookmarked(videoId).collect { bookmarked ->
                _state.update { it.copy(isBookmarked = bookmarked) }
            }
        }
    }

    fun selectSeriesSeason(season: Int) {
        _state.update { it.copy(selectedSeriesSeason = season) }
    }
}

/** Full-screen TV player with D-pad controls: OK = play/pause, ←/→ = ±10s, ↑ = controls. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvVideoScreen(
    viewModel: TvVideoViewModel,
    session: PlaybackSession,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pb by session.state.collectAsStateWithLifecycle()
    val player = session.player

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var selectedQualityHeight by remember { mutableIntStateOf(0) }
    var showTextPicker by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var selectedTextLanguage by remember { mutableStateOf<String?>(null) }
    var selectedAudioLanguage by remember { mutableStateOf<String?>(null) }

    // Attach the video once details (and the resume position) are loaded.
    // open() is idempotent for the same video id.
    LaunchedEffect(state.details) {
        if (state.details != null) {
            session.open(
                videoId = viewModel.videoId,
                resumeFromMs = state.startPositionMs,
                onSavePosition = { pos -> viewModel.persistPosition(pos) },
            )
        }
    }

    // The ViewModel owns "what is the next episode"; the session owns timing.
    LaunchedEffect(state.nextVideoId) { session.setUpNext(state.nextVideoId) }

    // One-shot playback events.
    LaunchedEffect(session) {
        session.events.collect { event ->
            when (event) {
                is PlaybackEvent.NavigateToNext -> onVideoClick(event.videoId)
                is PlaybackEvent.FatalError -> Unit // rendered from state below
            }
        }
    }

    val boxFocusRequester = remember { FocusRequester() }
    val pauseFocusRequester = remember { FocusRequester() }

    // Transport observation for the custom controls (progress bar, play/pause label).
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Progress polling for the controls overlay.
    LaunchedEffect(player) {
        while (true) {
            delay(1000)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
        }
    }

    // Auto-hide controls.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(4000)
            if (isPlaying && !showQualityPicker) controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            pauseFocusRequester.requestFocus()
        } else {
            boxFocusRequester.requestFocus()
        }
    }
    androidx.activity.compose.BackHandler { onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(boxFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Dedicated media keys on the remote (play/pause, rewind, fast-forward, next/prev).
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (isPlaying) player.pause() else player.play()
                        controlsVisible = true
                        return@onKeyEvent true
                    }
                    Key.MediaStop -> {
                        player.pause()
                        controlsVisible = true
                        return@onKeyEvent true
                    }
                    Key.MediaRewind -> {
                        player.seekBack()
                        positionMs = player.currentPosition
                        controlsVisible = true
                        return@onKeyEvent true
                    }
                    Key.MediaFastForward -> {
                        player.seekForward()
                        positionMs = player.currentPosition
                        controlsVisible = true
                        return@onKeyEvent true
                    }
                    Key.MediaNext, Key.MediaSkipForward -> {
                        state.nextVideoId?.let { onVideoClick(it) }
                        return@onKeyEvent true
                    }
                    Key.MediaPrevious, Key.MediaSkipBackward -> {
                        // No previous-episode tracking yet; keep the key consumed.
                        return@onKeyEvent true
                    }
                    else -> { }
                }
                // When controls are visible, let focus move between the overlay buttons.
                // Down hides them so the user can seek again.
                if (controlsVisible) {
                    if (event.key == Key.DirectionDown) {
                        controlsVisible = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (isPlaying) player.pause() else player.play()
                        controlsVisible = true
                        true
                    }

                    Key.DirectionLeft -> {
                        player.seekBack()
                        positionMs = player.currentPosition
                        controlsVisible = true
                        true
                    }

                    Key.DirectionRight -> {
                        player.seekForward()
                        positionMs = player.currentPosition
                        controlsVisible = true
                        true
                    }

                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
                        false
                    }

                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(24.dp),
            ) {
                androidx.tv.material3.Text(
                    state.details?.title.orEmpty(),
                    style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.tv.material3.Text(
                        formatMs(positionMs),
                        style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = {
                            if (durationMs > 0) {
                                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            } else 0f
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    androidx.tv.material3.Text(
                        formatMs(durationMs),
                        style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Transport + navigation (most-used actions, first row).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.tv.material3.Button(
                        onClick = {
                            if (isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier.focusRequester(pauseFocusRequester),
                    ) {
                        androidx.tv.material3.Text(if (isPlaying) "Пауза" else "Смотреть")
                    }
                    androidx.tv.material3.Button(onClick = { player.seekBack() }) {
                        androidx.tv.material3.Text("-10 сек")
                    }
                    androidx.tv.material3.Button(onClick = { player.seekForward() }) {
                        androidx.tv.material3.Text("+10 сек")
                    }
                    androidx.tv.material3.Button(onClick = {
                        showQualityPicker = !showQualityPicker
                    }) {
                        androidx.tv.material3.Text("Качество")
                    }
                    if (state.nextVideoId != null) {
                        androidx.tv.material3.Button(onClick = {
                            state.nextVideoId?.let { onVideoClick(it) }
                        }) {
                            androidx.tv.material3.Text("Следующая серия")
                        }
                    }
                }
                // Secondary actions (second row, so nothing overflows the screen width).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    if (pb.audioLanguages.isNotEmpty()) {
                        androidx.tv.material3.Button(onClick = { showAudioPicker = !showAudioPicker }) {
                            androidx.tv.material3.Text("Аудио")
                        }
                    }
                    if (pb.textLanguages.isNotEmpty()) {
                        androidx.tv.material3.Button(onClick = { showTextPicker = !showTextPicker }) {
                            androidx.tv.material3.Text("Субтитры")
                        }
                    }
                    androidx.tv.material3.Button(onClick = {
                        session.setSleepTimer(
                            when (pb.sleepTimerMinutes) {
                                null -> 15
                                15 -> 30
                                30 -> 45
                                45 -> 60
                                else -> null
                            }
                        )
                    }) {
                        androidx.tv.material3.Text(
                            pb.sleepTimerMinutes?.let { "Сон: ${it}м" } ?: "Таймер сна"
                        )
                    }
                    androidx.tv.material3.Button(onClick = viewModel::toggleBookmark) {
                        Icon(
                            imageVector = if (state.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = androidx.tv.material3.MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.tv.material3.Text(
                            if (state.isBookmarked) "В избранном" else "В избранное"
                        )
                    }
                    androidx.tv.material3.Button(onClick = {
                        when (state.rating?.liked) {
                            true -> viewModel.removeRating()
                            else -> viewModel.rate(true)
                        }
                    }) {
                        Icon(
                            imageVector = if (state.rating?.liked == true) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                            contentDescription = null,
                            tint = androidx.tv.material3.MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    androidx.tv.material3.Button(onClick = {
                        when (state.rating?.liked) {
                            false -> viewModel.removeRating()
                            else -> viewModel.rate(false)
                        }
                    }) {
                        Icon(
                            imageVector = if (state.rating?.liked == false) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                            contentDescription = null,
                            tint = androidx.tv.material3.MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                // --- Series episodes ---
                val seriesSeasons = state.seriesSeasons
                val selectedSeason = state.selectedSeriesSeason ?: seriesSeasons.firstOrNull()
                val seriesEpisodesForSeason = remember(state.seriesEpisodes, selectedSeason) {
                    episodesForSeason(state.seriesEpisodes, selectedSeason)
                }
                if (seriesEpisodesForSeason.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    if (seriesSeasons.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            seriesSeasons.forEach { season ->
                                androidx.tv.material3.Button(onClick = { viewModel.selectSeriesSeason(season) }) {
                                    androidx.tv.material3.Text(
                                        if (season == selectedSeason) "• $season сезон"
                                        else "$season сезон"
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    androidx.tv.material3.Text(
                        "Серии",
                        style = androidx.tv.material3.MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(seriesEpisodesForSeason, key = { it.id }) { video ->
                            val episode = episodeOf(video)
                            androidx.tv.material3.Button(onClick = { onVideoClick(video.id) }) {
                                Column {
                                    androidx.tv.material3.Text(
                                        "${episode ?: video.title} серия",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    androidx.tv.material3.Text(
                                        formatMs(video.durationSec * 1000L),
                                        style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                if (showQualityPicker && pb.qualities.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pb.qualities.forEach { option ->
                            androidx.tv.material3.Button(onClick = {
                                selectedQualityHeight = option.height
                                session.setQuality(option.height)
                                showQualityPicker = false
                            }) {
                                androidx.tv.material3.Text(
                                    if (option.height == selectedQualityHeight) "• ${option.label}"
                                    else option.label
                                )
                            }
                        }
                    }
                }
                if (showAudioPicker && pb.audioLanguages.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pb.audioLanguages.forEach { lang ->
                            androidx.tv.material3.Button(onClick = {
                                selectedAudioLanguage = lang
                                session.setAudioLanguage(lang)
                                showAudioPicker = false
                            }) {
                                androidx.tv.material3.Text(
                                    if (lang == selectedAudioLanguage) "• ${lang.uppercase()}"
                                    else lang.uppercase()
                                )
                            }
                        }
                    }
                }
                if (showTextPicker && pb.textLanguages.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.tv.material3.Button(onClick = {
                            selectedTextLanguage = null
                            session.setTextLanguage(null)
                            showTextPicker = false
                        }) {
                            androidx.tv.material3.Text(
                                if (selectedTextLanguage == null) "• ВЫКЛ" else "ВЫКЛ"
                            )
                        }
                        pb.textLanguages.forEach { lang ->
                            androidx.tv.material3.Button(onClick = {
                                selectedTextLanguage = lang
                                session.setTextLanguage(lang)
                                showTextPicker = false
                            }) {
                                androidx.tv.material3.Text(
                                    if (lang == selectedTextLanguage) "• ${lang.uppercase()}"
                                    else lang.uppercase()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val countdown = pb.autoplayCountdownSec
    if (countdown != null && state.nextVideoId != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.tv.material3.Text(
                    "Следующая серия через $countdown",
                    style = androidx.tv.material3.MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.tv.material3.Button(onClick = session::playNextNow) {
                        androidx.tv.material3.Text("Смотреть сейчас")
                    }
                    androidx.tv.material3.Button(onClick = session::cancelAutoplay) {
                        androidx.tv.material3.Text("Отмена")
                    }
                }
            }
        }
    }

    if (pb.stillWatchingDue) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.tv.material3.Text(
                    "Вы всё ещё смотрите?",
                    style = androidx.tv.material3.MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.tv.material3.Button(onClick = session::confirmStillWatching) {
                        androidx.tv.material3.Text("Да")
                    }
                    androidx.tv.material3.Button(onClick = {
                        player.pause()
                        session.confirmStillWatching()
                    }) {
                        androidx.tv.material3.Text("Стоп")
                    }
                }
            }
        }
    }

    if (state.isLoading) {
        TvLoadingBox()
    }
    state.error?.let { error ->
        if (state.details == null) {
            TvErrorBox(message = error, onRetry = viewModel::load)
        }
    }
    if (pb.status == PlaybackStatus.ERROR && state.details != null) {
        TvErrorBox(message = pb.errorMessage ?: "Ошибка воспроизведения", onRetry = session::retry)
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
