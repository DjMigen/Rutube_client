package ru.rutubeclient.mobile.ui.screens.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Rational
import android.view.View
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import ru.rutubeclient.core.model.Comment
import ru.rutubeclient.core.player.PlayerFactory
import ru.rutubeclient.core.player.QualityController
import ru.rutubeclient.core.player.QualityOption
import ru.rutubeclient.mobile.player.CastManager
import ru.rutubeclient.mobile.player.connectMediaController
import ru.rutubeclient.mobile.ui.components.AvatarImage
import ru.rutubeclient.mobile.ui.components.ErrorBox
import ru.rutubeclient.mobile.ui.components.LoadingBox
import ru.rutubeclient.mobile.ui.components.VideoCard
import ru.rutubeclient.mobile.ui.formatDate
import ru.rutubeclient.mobile.ui.formatViews

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

    // Connect to the shared playback service.
    var controller by remember { mutableStateOf<MediaController?>(null) }
    LaunchedEffect(Unit) {
        controller = context.connectMediaController()
    }

    // Attach the freshly resolved stream to the player.
    var appliedStreamVersion by remember { mutableIntStateOf(-1) }
    LaunchedEffect(controller, state.streamVersion, state.streamUrl) {
        val c = controller ?: return@LaunchedEffect
        val url = state.streamUrl ?: return@LaunchedEffect
        if (appliedStreamVersion == state.streamVersion) return@LaunchedEffect
        appliedStreamVersion = state.streamVersion
        val position = if (c.currentPosition > 0) c.currentPosition else state.startPositionMs
        c.setMediaItem(PlayerFactory.mediaItem(url, mediaId = viewModel.videoId))
        c.prepare()
        if (position > 0) c.seekTo(position)
        c.playWhenReady = true
    }

    // Player events: retry on error (expired signature), collect qualities.
    val qualityController = remember(controller) { controller?.let { QualityController(it) } }
    var qualities by remember { mutableStateOf(listOf<QualityOption>()) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var controllerVisible by remember { mutableStateOf(true) }

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(c.currentPosition)
            }

            override fun onTracksChanged(tracks: Tracks) {
                qualities = qualityController?.availableQualities().orEmpty()
            }
        }
        c.addListener(listener)
        onDispose {
            c.removeListener(listener)
        }
    }

    // Periodically persist the playback position into history.
    LaunchedEffect(controller, state.details) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            delay(5000)
            if (c.duration > 0 && c.currentPosition > 0) {
                viewModel.savePosition(c.currentPosition)
            }
        }
    }

    // On leaving the screen: save position, stop and release the controller.
    DisposableEffect(Unit) {
        onDispose {
            controller?.let { c ->
                viewModel.savePositionFinal(c.currentPosition)
                c.stop()
                c.clearMediaItems()
                c.release()
            }
        }
    }

    // Fullscreen: hide system bars and lock landscape; restore on exit / dispose.
    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }
    DisposableEffect(isFullscreen) {
        val activity = context.findActivity()
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
    BackHandler(enabled = isFullscreen) { isFullscreen = false }

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
                                view.player = controller
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
                                onClick = { isFullscreen = !isFullscreen },
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
                                castManager.loadVideo(details, url, controller?.currentPosition ?: 0L)
                                controller?.pause()
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
                        TextButton(onClick = {
                            val activity = context.findActivity()
                            activity?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                            )
                        }) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("PiP")
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
                controller?.setPlaybackSpeed(speed)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false },
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
