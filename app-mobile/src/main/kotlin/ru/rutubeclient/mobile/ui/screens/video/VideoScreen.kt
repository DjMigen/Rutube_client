package ru.rutubeclient.mobile.ui.screens.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import ru.rutubeclient.core.model.Comment
import ru.rutubeclient.core.player.PlayerFactory
import ru.rutubeclient.core.player.QualityController
import ru.rutubeclient.core.player.QualityOption
import ru.rutubeclient.mobile.player.CastManager
import ru.rutubeclient.mobile.ui.components.AvatarImage
import ru.rutubeclient.mobile.ui.components.ErrorBox
import ru.rutubeclient.mobile.ui.components.LoadingBox
import ru.rutubeclient.mobile.ui.components.VideoCard
import ru.rutubeclient.core.model.parseSeriesInfo
import ru.rutubeclient.mobile.ui.formatDate
import ru.rutubeclient.mobile.ui.formatViews

/** Three hours in milliseconds; triggers the "Are you still watching?" dialog. */
private const val STILL_WATCHING_MS = 3 * 60 * 60 * 1000L

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    viewModel: VideoViewModel,
    castManager: CastManager,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: (Long, String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isCasting by castManager.isCasting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Local ExoPlayer for this screen. Using a screen-local player avoids
    // surface lifecycle issues when switching episodes through navigation.
    val player = remember { PlayerFactory.create(context) }

    // Attach the freshly resolved stream to the player.
    var appliedStreamVersion by remember { mutableIntStateOf(-1) }
    LaunchedEffect(player, state.streamVersion, state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        if (appliedStreamVersion == state.streamVersion) return@LaunchedEffect
        appliedStreamVersion = state.streamVersion
        val position = if (player.currentPosition > 0) player.currentPosition else state.startPositionMs
        player.setMediaItem(PlayerFactory.mediaItem(url, mediaId = viewModel.videoId), position)
        player.prepare()
        player.play()
    }

    // Player events: retry on error (expired signature), collect qualities.
    val qualityController = remember(player) { QualityController(player) }
    var qualities by remember { mutableStateOf(listOf<QualityOption>()) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableStateOf<Int?>(null) }
    var nextEpisodeCountdown by remember { mutableStateOf<Int?>(null) }
    var textLanguages by remember { mutableStateOf(listOf<String>()) }
    var audioLanguages by remember { mutableStateOf(listOf<String>()) }
    var selectedTextLanguage by remember { mutableStateOf<String?>(null) }
    var selectedAudioLanguage by remember { mutableStateOf<String?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isManualFullscreen by remember { mutableStateOf(false) }
    var controllerVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var showStillWatching by remember { mutableStateOf(false) }
    var stillWatchingAck by remember { mutableStateOf(0) }
    val configuration = LocalConfiguration.current

    // Sleep timer: pause playback when it elapses.
    LaunchedEffect(sleepTimerMinutes) {
        val minutes = sleepTimerMinutes ?: return@LaunchedEffect
        kotlinx.coroutines.delay(minutes * 60_000L)
        player.pause()
        sleepTimerMinutes = null
    }

    // Autoplay next episode countdown.
    LaunchedEffect(nextEpisodeCountdown) {
        var remaining = nextEpisodeCountdown ?: return@LaunchedEffect
        while (remaining > 0) {
            kotlinx.coroutines.delay(1000)
            remaining--
            nextEpisodeCountdown = remaining
        }
        nextEpisodeCountdown = null
        state.nextVideoId?.let { onVideoClick(it) }
    }

    // "Are you still watching?" after 3 hours of continuous playback.
    LaunchedEffect(isPlaying, stillWatchingAck) {
        if (!isPlaying) return@LaunchedEffect
        delay(STILL_WATCHING_MS)
        showStillWatching = true
    }

    // Auto-enter fullscreen on landscape rotation; auto-exit when returning to portrait
    // unless the user explicitly enabled fullscreen manually.
    LaunchedEffect(configuration.orientation) {
        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> if (!isFullscreen) {
                isFullscreen = true
                isManualFullscreen = false
            }
            Configuration.ORIENTATION_PORTRAIT -> if (isFullscreen && !isManualFullscreen) {
                isFullscreen = false
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(player.currentPosition)
            }

            override fun onTracksChanged(tracks: Tracks) {
                qualities = qualityController.availableQualities()
                textLanguages = qualityController.availableTextLanguages()
                audioLanguages = qualityController.availableAudioLanguages()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && state.nextVideoId != null) {
                    nextEpisodeCountdown = 5
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Periodically persist the playback position into history.
    LaunchedEffect(player, state.details) {
        while (true) {
            delay(5000)
            if (player.duration > 0 && player.currentPosition > 0) {
                viewModel.savePosition(player.currentPosition)
            }
        }
    }

    // On leaving the screen: save position and release the local player.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.savePositionFinal(player.currentPosition)
            player.release()
        }
    }

    // Fullscreen: hide system bars and (for manual fullscreen) lock landscape.
    // Auto-fullscreen follows device rotation and does not lock orientation.
    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }
    DisposableEffect(isFullscreen, isManualFullscreen) {
        val activity = context.findActivity()
        if (isFullscreen) {
            if (isManualFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            insetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Back always returns to the home screen, not to the previous video.
    BackHandler(enabled = true) {
        onBack()
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        isManualFullscreen = false
    }

    // Snackbar for action messages.
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            state.details?.title ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.error != null && state.details == null -> ErrorBox(
                message = state.error ?: "Ошибка",
                onRetry = viewModel::loadVideo,
                modifier = Modifier.padding(padding),
            )

            else -> {
                val columnModifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                }
                Column(columnModifier) {
                // --- Player ---
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black,
                    modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                    setControllerVisibilityListener(
                                        PlayerView.ControllerVisibilityListener { visibility ->
                                            controllerVisible = visibility == View.VISIBLE
                                        }
                                    )
                                }
                            },
                            update = { view ->
                                view.player = player
                                view.resizeMode = if (isFullscreen) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                                } else {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            modifier = if (isFullscreen) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            },
                        )
                        if (controllerVisible) {
                            IconButton(
                                onClick = {
                                    isFullscreen = !isFullscreen
                                    isManualFullscreen = isFullscreen
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                            ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Выйти из полноэкранного режима" else "Во весь экран",
                                tint = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                        }
                    }
                }

                if (isFullscreen) return@Column

                val details = state.details ?: return@Column

                Column(Modifier.padding(12.dp)) {
                    Text(details.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatViews(details.hits)} • ${formatDate(details.createdTs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    // --- Author row ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = details.author != null) {
                                details.author?.let { onChannelClick(it.id, it.name) }
                            },
                    ) {
                        AvatarImage(
                            url = details.author?.avatarUrl,
                            name = details.author?.name.orEmpty(),
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            details.author?.name.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Button(onClick = viewModel::toggleSubscription) {
                            Text(if (state.isSubscribed) "Вы подписаны" else "Подписаться")
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // --- Actions ---
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = viewModel::toggleBookmark) {
                            Icon(
                                if (state.isBookmarked) Icons.Default.Bookmark
                                else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isBookmarked) "В закладках" else "Смотреть позже")
                        }
                        TextButton(onClick = {
                            when (state.rating?.liked) {
                                true -> viewModel.removeRating()
                                else -> viewModel.rate(true)
                            }
                        }) {
                            Icon(
                                if (state.rating?.liked == true) Icons.Default.ThumbUp
                                else Icons.Default.ThumbUpOffAlt,
                                contentDescription = null,
                            )
                        }
                        TextButton(onClick = {
                            when (state.rating?.liked) {
                                false -> viewModel.removeRating()
                                else -> viewModel.rate(false)
                            }
                        }) {
                            Icon(
                                if (state.rating?.liked == false) Icons.Default.ThumbDown
                                else Icons.Default.ThumbDownOffAlt,
                                contentDescription = null,
                            )
                        }
                        TextButton(onClick = { showDownloadDialog = true }) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                when (state.download?.state) {
                                    "completed" -> "Скачано"
                                    "downloading", "queued" -> "Качается…"
                                    else -> "Скачать"
                                }
                            )
                        }
                        TextButton(onClick = {
                            val url = state.streamUrl
                            if (url != null && isCasting) {
                                castManager.loadVideo(details, url, player.currentPosition)
                                player.pause()
                            }
                        }) {
                            Icon(Icons.Default.Cast, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isCasting) "На ТВ" else "Cast")
                        }
                        TextButton(onClick = { showQualityDialog = true }) {
                            Icon(Icons.Default.Hd, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Качество")
                        }
                        TextButton(onClick = { showSpeedDialog = true }) {
                            Icon(Icons.Default.Speed, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Скорость")
                        }
                        TextButton(onClick = { showSleepDialog = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                sleepTimerMinutes?.let { "Сон: ${it}м" } ?: "Таймер сна"
                            )
                        }
                        if (state.nextVideoId != null) {
                            TextButton(onClick = { state.nextVideoId?.let(onVideoClick) }) {
                                Icon(Icons.Default.SkipNext, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Следующая серия")
                            }
                        }
                        if (audioLanguages.isNotEmpty()) {
                            TextButton(onClick = { showAudioDialog = true }) {
                                Text("Аудио")
                            }
                        }
                        if (textLanguages.isNotEmpty()) {
                            TextButton(onClick = { showTextDialog = true }) {
                                Text("Субтитры")
                            }
                        }
                    }

                    // --- Description ---
                    if (!details.description.isNullOrBlank()) {
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            details.description.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(vertical = 8.dp),
                        )
                    }

                    // --- Series episodes (catalog or discovered) ---
                    if (state.seriesEpisodes.isNotEmpty()) {
                        val seriesSeasons = state.seriesSeasons.ifEmpty { listOf(1) }
                        val selectedSeason = state.selectedSeriesSeason ?: seriesSeasons.firstOrNull()
                        val seriesEpisodesForSeason = state.seriesEpisodes.filter { v ->
                            val info = parseSeriesInfo(v.title)
                            val season = info?.season ?: v.season ?: 1
                            season == selectedSeason
                        }.ifEmpty { state.seriesEpisodes }

                        Spacer(Modifier.height(16.dp))
                        Text("Серии", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        if (seriesSeasons.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                seriesSeasons.forEach { season ->
                                    item {
                                        FilterChip(
                                            selected = season == selectedSeason,
                                            onClick = { viewModel.selectSeriesSeason(season) },
                                            label = { Text(if (season == 0) "Все" else "$season сезон") },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItemsIndexed(seriesEpisodesForSeason, key = { index, video -> "${video.id}_$index" }) { _, video ->
                                VideoCard(
                                    video = video,
                                    onClick = { onVideoClick(video.id) },
                                    modifier = Modifier.width(240.dp),
                                )
                            }
                        }
                    }

                    // --- More from this channel ---
                    if (state.channelVideos.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Ещё с канала", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItemsIndexed(state.channelVideos, key = { index, video -> "${video.id}_$index" }) { _, video ->
                                VideoCard(
                                    video = video,
                                    onClick = { onVideoClick(video.id) },
                                    modifier = Modifier.width(240.dp),
                                )
                            }
                        }
                    }

                    // --- Similar ---
                    if (state.similarVideos.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Похожие", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItemsIndexed(state.similarVideos, key = { index, video -> "${video.id}_$index" }) { _, video ->
                                VideoCard(
                                    video = video,
                                    onClick = { onVideoClick(video.id) },
                                    modifier = Modifier.width(240.dp),
                                )
                            }
                        }
                    }

                    // --- Comments ---
                    if (details.commentsEnabled) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Комментарии (${state.comments.size})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        state.comments.forEach { comment ->
                            CommentItem(comment)
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                        if (state.commentsHasNext) {
                            TextButton(
                                onClick = viewModel::loadMoreComments,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (state.commentsLoading) "Загрузка…"
                                    else "Показать ещё"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    // --- Dialogs ---
    if (showQualityDialog) {
        QualityDialog(
            qualities = qualities,
            onSelect = { height ->
                qualityController?.select(height)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false },
        )
    }
    if (showSpeedDialog) {
        SpeedDialog(
            current = playbackSpeed,
            onSelect = { speed ->
                playbackSpeed = speed
                player.setPlaybackSpeed(speed)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false },
        )
    }
    val countdown = nextEpisodeCountdown
    if (countdown != null && state.nextVideoId != null) {
        NextEpisodeOverlay(
            countdown = countdown,
            onCancel = { nextEpisodeCountdown = null },
            onPlayNow = {
                nextEpisodeCountdown = null
                onVideoClick(state.nextVideoId!!)
            },
        )
    }

    if (showAudioDialog) {
        TrackSelectionDialog(
            title = "Аудиодорожка",
            options = audioLanguages,
            selected = selectedAudioLanguage,
            noneLabel = "По умолчанию",
            onSelect = { lang ->
                selectedAudioLanguage = lang
                qualityController?.selectAudioLanguage(lang)
                showAudioDialog = false
            },
            onDismiss = { showAudioDialog = false },
        )
    }
    if (showTextDialog) {
        TrackSelectionDialog(
            title = "Субтитры",
            options = textLanguages,
            selected = selectedTextLanguage,
            noneLabel = "Выключить",
            onSelect = { lang ->
                selectedTextLanguage = lang
                qualityController?.selectTextLanguage(lang)
                showTextDialog = false
            },
            onDismiss = { showTextDialog = false },
        )
    }
    if (showStillWatching) {
        AlertDialog(
            onDismissRequest = { showStillWatching = false },
            confirmButton = {
                TextButton(onClick = {
                    showStillWatching = false
                    stillWatchingAck++
                }) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = {
                    player.pause()
                    showStillWatching = false
                }) { Text("Стоп") }
            },
            title = { Text("Вы всё ещё смотрите?") },
            text = { Text("Нажмите «Да», чтобы продолжить просмотр.") },
        )
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            current = sleepTimerMinutes,
            onSelect = { minutes ->
                sleepTimerMinutes = minutes
                showSleepDialog = false
            },
            onDismiss = { showSleepDialog = false },
        )
    }
    if (showDownloadDialog) {
        DownloadDialog(
            qualities = qualities,
            onSelect = { height ->
                viewModel.download(height)
                showDownloadDialog = false
                // Notifications for the download progress need runtime permission on API 33+.
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    !NotificationManagerCompat.from(context).areNotificationsEnabled()
                ) {
                    // The system settings screen is the least intrusive fallback.
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        ).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            },
            onDismiss = { showDownloadDialog = false },
        )
    }
}

@Composable
private fun CommentItem(comment: Comment) {
    Row(Modifier.padding(vertical = 8.dp)) {
        AvatarImage(
            url = comment.authorAvatarUrl,
            name = comment.authorName.orEmpty(),
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDate(comment.createdTs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            if (comment.likesCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "👍 ${comment.likesCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QualityDialog(
    qualities: List<QualityOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = qualities.ifEmpty { listOf(QualityOption("Авто", 0)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Качество видео") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.height) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = false, onClick = { onSelect(option.height) })
                        Text(option.label)
                    }
                }
            }
        },
    )
}

@Composable
private fun SpeedDialog(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Скорость воспроизведения") },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = speed == current, onClick = { onSelect(speed) })
                        Text(if (speed == 1f) "Обычная" else "${speed}x")
                    }
                }
            }
        },
    )
}

@Composable
private fun NextEpisodeOverlay(
    countdown: Int,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Следующая серия через $countdown",
                style = MaterialTheme.typography.headlineSmall,
                color = androidx.compose.ui.graphics.Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPlayNow) {
                    Text("Смотреть сейчас")
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                ) {
                    Text("Отмена")
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionDialog(
    title: String,
    options: List<String>,
    selected: String?,
    noneLabel: String,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text(title) },
        text = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == null, onClick = { onSelect(null) })
                    Text(noneLabel)
                }
                options.forEach { lang ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(lang) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang == selected, onClick = { onSelect(lang) })
                        Text(lang.uppercase())
                    }
                }
            }
        },
    )
}

@Composable
private fun SleepTimerDialog(
    current: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Таймер сна") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = minutes == current, onClick = { onSelect(minutes) })
                        Text("$minutes минут")
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current == null, onClick = { onSelect(null) })
                    Text("Выключить")
                }
            }
        },
    )
}

@Composable
private fun DownloadDialog(
    qualities: List<QualityOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(QualityOption("Лучшее доступное", 0)) +
        qualities.filter { it.height > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Скачать видео") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.height) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = false, onClick = { onSelect(option.height) })
                        Text(option.label)
                    }
                }
            }
        },
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
