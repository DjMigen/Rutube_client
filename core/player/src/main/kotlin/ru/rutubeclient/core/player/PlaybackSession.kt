package ru.rutubeclient.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Port: resolves a fresh signed Stream URL for a RuTube video id.
 * Production wires this to RuTubeApi; tests supply a lambda.
 */
fun interface StreamResolver {
    suspend fun resolve(videoId: String): String
}

/** One-shot notifications the screen must act on. */
sealed interface PlaybackEvent {
    /** Autoplay countdown finished (or "watch now" was pressed); open this video. */
    data class NavigateToNext(val videoId: String) : PlaybackEvent

    /** Playback cannot continue; the stream was already re-resolved once. */
    data class FatalError(val message: String) : PlaybackEvent
}

enum class PlaybackStatus { IDLE, LOADING, READY, ERROR }

/** Everything the UI needs to render playback. Single source of truth. */
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val errorMessage: String? = null,
    /** Freshly resolved signed Stream URL (used by Cast). */
    val streamUrl: String? = null,
    val qualities: List<QualityOption> = emptyList(),
    val textLanguages: List<String> = emptyList(),
    val audioLanguages: List<String> = emptyList(),
    val speed: Float = 1f,
    /** Seconds left until the next episode starts; null = no countdown. */
    val autoplayCountdownSec: Int? = null,
    /** True when the "Вы всё ещё смотрите?" prompt should be shown. */
    val stillWatchingDue: Boolean = false,
    val sleepTimerMinutes: Int? = null,
)

/**
 * A playback session: one video, one screen visit.
 *
 * Owns the player lifecycle and all playback policy that used to live in
 * screen composables: stream attach with internal versioning, resume
 * position, retry-once on playback errors (expired Stream URL signature),
 * periodic position persistence, autoplay-next countdown, "still watching"
 * prompt and the sleep timer. Screens render [state] and send intents;
 * they never program against Media3 policy.
 *
 * Lifecycle: created per screen (see ADR-0001), closed when the screen
 * goes away. Episode switching is navigation — a new screen means a new
 * session and a new player.
 */
@OptIn(UnstableApi::class)
class PlaybackSession(
    val player: Player,
    private val streamResolver: StreamResolver,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private val qualityController = QualityController(player)

    private var videoId: String? = null
    private var resumeFromMs = 0L
    private var positionSink: (suspend (positionMs: Long) -> Unit)? = null
    private var upNext: String? = null
    private var retriedAfterError = false
    private var attachJob: Job? = null
    private var saveLoopJob: Job? = null
    private var countdownJob: Job? = null
    private var stillWatchingJob: Job? = null
    private var sleepJob: Job? = null
    private var closed = false

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) = onPlaybackError()

        override fun onTracksChanged(tracks: Tracks) {
            _state.update {
                it.copy(
                    qualities = qualityController.availableQualities(),
                    textLanguages = qualityController.availableTextLanguages(),
                    audioLanguages = qualityController.availableAudioLanguages(),
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) startAutoplayCountdown()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startStillWatchingTimer() else stopStillWatchingTimer()
        }
    }

    init {
        player.addListener(listener)
    }

    /**
     * Attaches a video: resolves a fresh Stream URL and starts playback.
     * Idempotent for the same video id — repeat calls are no-ops unless the
     * session is in [PlaybackStatus.ERROR].
     *
     * [onSavePosition] is called with the playback position every few
     * seconds and once more on [close]; the caller persists it (metadata
     * stays on the ViewModel side of the seam).
     */
    fun open(
        videoId: String,
        resumeFromMs: Long = 0L,
        onSavePosition: (suspend (positionMs: Long) -> Unit)? = null,
    ) {
        if (closed) return
        if (this.videoId == videoId && _state.value.status != PlaybackStatus.ERROR) return
        this.videoId = videoId
        this.resumeFromMs = resumeFromMs
        this.positionSink = onSavePosition
        retriedAfterError = false
        attach(resumeFromMs)
    }

    /** Re-resolves the stream and re-attaches after an error. */
    fun retry() {
        if (videoId == null || closed) return
        retriedAfterError = false
        attach(currentResumePosition())
    }

    fun setUpNext(nextVideoId: String?) {
        upNext = nextVideoId
        if (nextVideoId == null) cancelAutoplay()
    }

    fun cancelAutoplay() {
        countdownJob?.cancel()
        countdownJob = null
        _state.update { it.copy(autoplayCountdownSec = null) }
    }

    /** "Смотреть сейчас": skip the countdown and navigate immediately. */
    fun playNextNow() {
        val next = upNext ?: return
        cancelAutoplay()
        scope.launch { _events.emit(PlaybackEvent.NavigateToNext(next)) }
    }

    fun setQuality(height: Int) = qualityController.select(height)

    fun setTextLanguage(language: String?) = qualityController.selectTextLanguage(language)

    fun setAudioLanguage(language: String?) = qualityController.selectAudioLanguage(language)

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** "Да, продолжаю": dismisses the prompt and restarts the 3-hour timer. */
    fun confirmStillWatching() {
        _state.update { it.copy(stillWatchingDue = false) }
        stopStillWatchingTimer()
        if (player.isPlaying) startStillWatchingTimer()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(sleepTimerMinutes = minutes) }
        if (minutes != null) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                player.pause()
                _state.update { it.copy(sleepTimerMinutes = null) }
            }
        }
    }

    /** Persists the current position immediately (also runs on a timer). */
    fun savePositionNow() {
        val sink = positionSink ?: return
        val position = player.currentPosition
        if (player.duration > 0 && position > 0) {
            scope.launch { sink(position) }
        }
    }

    /** Saves the final position, releases the player and cancels timers. */
    fun close() {
        if (closed) return
        closed = true
        val sink = positionSink
        val position = player.currentPosition
        player.removeListener(listener)
        player.release()
        if (sink != null && position > 0) {
            // NonCancellable: the final write must survive scope cancellation.
            scope.launch(NonCancellable) { sink(position) }
        }
        scope.cancel()
    }

    // --- internals ---

    private fun currentResumePosition(): Long =
        if (player.currentPosition > 0) player.currentPosition else resumeFromMs

    private fun attach(positionMs: Long) {
        val id = videoId ?: return
        attachJob?.cancel()
        _state.update { it.copy(status = PlaybackStatus.LOADING, errorMessage = null) }
        attachJob = scope.launch {
            runCatching { streamResolver.resolve(id) }
                .onSuccess { url ->
                    _state.update {
                        it.copy(status = PlaybackStatus.READY, streamUrl = url)
                    }
                    player.setMediaItem(PlayerFactory.mediaItem(url, mediaId = id), positionMs)
                    player.prepare()
                    player.play()
                    startSaveLoop()
                }
                .onFailure { e ->
                    val message = e.message ?: "Поток недоступен"
                    _state.update {
                        it.copy(status = PlaybackStatus.ERROR, errorMessage = message)
                    }
                    _events.emit(PlaybackEvent.FatalError(message))
                }
        }
    }

    /** Likely an expired Stream URL signature: re-resolve once, then give up. */
    private fun onPlaybackError() {
        if (!retriedAfterError && videoId != null) {
            retriedAfterError = true
            attach(currentResumePosition())
        } else {
            val message = "Ошибка воспроизведения"
            _state.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = message) }
            scope.launch { _events.emit(PlaybackEvent.FatalError(message)) }
        }
    }

    private fun startSaveLoop() {
        if (saveLoopJob?.isActive == true) return
        saveLoopJob = scope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                savePositionNow()
            }
        }
    }

    private fun startAutoplayCountdown() {
        val next = upNext ?: return
        if (countdownJob?.isActive == true) return
        countdownJob = scope.launch {
            var remaining = AUTOPLAY_COUNTDOWN_SEC
            _state.update { it.copy(autoplayCountdownSec = remaining) }
            while (remaining > 1) {
                delay(1000)
                remaining--
                _state.update { it.copy(autoplayCountdownSec = remaining) }
            }
            delay(1000)
            _state.update { it.copy(autoplayCountdownSec = null) }
            _events.emit(PlaybackEvent.NavigateToNext(next))
        }
    }

    private fun startStillWatchingTimer() {
        if (stillWatchingJob?.isActive == true) return
        stillWatchingJob = scope.launch {
            delay(STILL_WATCHING_MS)
            _state.update { it.copy(stillWatchingDue = true) }
        }
    }

    private fun stopStillWatchingTimer() {
        stillWatchingJob?.cancel()
        stillWatchingJob = null
    }

    companion object {
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val AUTOPLAY_COUNTDOWN_SEC = 5
        private const val STILL_WATCHING_MS = 3 * 60 * 60 * 1000L

        /** Production constructor: builds the ExoPlayer via [PlayerFactory]. */
        fun create(
            context: Context,
            streamResolver: StreamResolver,
            offlineProvider: OfflineContentProvider? = null,
        ): PlaybackSession =
            PlaybackSession(PlayerFactory.create(context, offlineProvider), streamResolver)
    }
}
