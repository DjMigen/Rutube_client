package ru.rutubeclient.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.VideoDetails
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.core.player.PlayerFactory
import ru.rutubeclient.core.player.QualityController
import ru.rutubeclient.core.player.QualityOption
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvLoadingBox

class TvVideoViewModel(
    private val videoId: String,
    private val api: RuTubeApi,
    private val repository: LibraryRepository,
) : ViewModel() {

    data class UiState(
        val details: VideoDetails? = null,
        val streamUrl: String? = null,
        val streamVersion: Int = 0,
        val startPositionMs: Long = 0L,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isBookmarked: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** Scope that survives onCleared() for the final position write. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                            startPositionMs = history?.positionMs ?: 0L,
                            isLoading = false,
                        )
                    }
                    resolveStream()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                    }
                }
        }
    }

    private suspend fun resolveStream() {
        runCatching { api.getStreamInfo(videoId) }
            .onSuccess { info ->
                _state.update {
                    it.copy(streamUrl = info.hlsUrl, streamVersion = it.streamVersion + 1)
                }
            }
            .onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Поток недоступен") }
            }
    }

    fun onPlaybackError(positionMs: Long) {
        viewModelScope.launch { resolveStream() }
    }

    fun savePosition(positionMs: Long) {
        val details = _state.value.details ?: return
        if (positionMs <= 0L) return
        ioScope.launch { repository.savePosition(details, positionMs) }
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

    private fun observeBookmarks() {
        viewModelScope.launch {
            repository.isBookmarked(videoId).collect { bookmarked ->
                _state.update { it.copy(isBookmarked = bookmarked) }
            }
        }
    }
}

/** Full-screen TV player with D-pad controls: OK = play/pause, ←/→ = ±10s, ↑ = controls. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvVideoScreen(
    viewModel: TvVideoViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val player = remember { PlayerFactory.create(context) }
    val qualityController = remember { QualityController(player) }
    var qualities by remember { mutableStateOf(listOf<QualityOption>()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var selectedQualityHeight by remember { mutableIntStateOf(0) }

    val boxFocusRequester = remember { FocusRequester() }
    val pauseFocusRequester = remember { FocusRequester() }

    // Attach stream when ready / refreshed.
    var appliedVersion by remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.streamVersion, state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        if (appliedVersion == state.streamVersion) return@LaunchedEffect
        appliedVersion = state.streamVersion
        val resume = if (player.currentPosition > 0) player.currentPosition else state.startPositionMs
        player.setMediaItem(PlayerFactory.mediaItem(url))
        player.prepare()
        if (resume > 0) player.seekTo(resume)
        player.playWhenReady = true
    }

    // Player listener: errors → re-resolve, tracks → qualities, progress → UI.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(player.currentPosition)
            }

            override fun onTracksChanged(tracks: Tracks) {
                qualities = qualityController.availableQualities()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Progress + position persistence.
    LaunchedEffect(player) {
        while (true) {
            delay(1000)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
        }
    }
    LaunchedEffect(player, state.details) {
        while (true) {
            delay(5000)
            if (player.duration > 0 && player.currentPosition > 0) {
                viewModel.savePosition(player.currentPosition)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (player.currentPosition > 0) viewModel.savePosition(player.currentPosition)
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
                    if (state.details?.author != null) {
                        androidx.tv.material3.Text(
                            state.details?.author?.name.orEmpty(),
                            style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                            color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showQualityPicker && qualities.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        qualities.forEach { option ->
                            androidx.tv.material3.Button(onClick = {
                                selectedQualityHeight = option.height
                                qualityController.select(option.height)
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
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
