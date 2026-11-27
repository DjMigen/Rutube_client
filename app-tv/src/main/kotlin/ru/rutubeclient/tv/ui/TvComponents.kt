package ru.rutubeclient.tv.ui

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import ru.rutubeclient.core.model.Video

/** A video with its playback progress (0..1) for the "Continue watching" row. */
data class ContinueItem(val video: Video, val progress: Float)

/** Focusable video card for TV grids and rows. */
@Composable
fun TvVideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(220.dp),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
            ),
        ),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop,
                )
                if (video.isRecent()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    ) {
                        Text(
                            "НОВИНКА",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (video.isLiveNow) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Text(
                            "LIVE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    video.author?.name.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A titled horizontal row of videos. */
@Composable
fun TvVideoRow(
    title: String,
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        ) {
            rowItemsIndexed(videos, key = { index, video -> "${video.id}_$index" }) { _, video ->
                TvVideoCard(video = video, onClick = { onVideoClick(video.id) })
            }
        }
    }
}

/** Large featured banner with backdrop, title, description and focusable actions. */
@Composable
fun TvHeroBanner(
    video: Video,
    isBookmarked: Boolean,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    onSurprise: () -> Unit,
    previewStreamUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (previewStreamUrl != null) {
            HeroVideoPreview(
                streamUrl = previewStreamUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.92f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp, end = 48.dp)
                .width(560.dp),
        ) {
            Text(
                video.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val desc = video.description?.takeIf { it.isNotBlank() }
            if (desc != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPlay) {
                    Text("▶  Смотреть")
                }
                Button(onClick = onToggleList) {
                    Text(if (isBookmarked) "✓  В списке" else "+  Мой список")
                }
                Button(onClick = onSurprise) {
                    Text("🎲  Удиви меня")
                }
            }
        }
    }
}

/**
 * Muted, looping, auto-playing video preview for the TV hero banner.
 * Fades in once the first frame is rendered and is released when the
 * composable leaves composition.
 */
@Composable
private fun HeroVideoPreview(
    streamUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(streamUrl) {
        ru.rutubeclient.core.player.PlayerFactory.create(context).apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(ru.rutubeclient.core.player.PlayerFactory.mediaItem(streamUrl, streamUrl))
            prepare()
        }
    }
    var alpha by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                alpha = 1f
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        delay(2000)
        player.playWhenReady = true
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        modifier = modifier.alpha(alpha),
    )
}

/** Titled horizontal carousel of Top-10 cards with big rank numbers. */
@Composable
fun TvTop10Row(
    title: String,
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        ) {
            rowItemsIndexed(videos, key = { index, video -> "${video.id}_$index" }) { index, video ->
                TvTop10Card(
                    video = video,
                    rank = index + 1,
                    onClick = { onVideoClick(video.id) },
                )
            }
        }
    }
}

@Composable
fun TvTop10Card(
    video: Video,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(240.dp),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White)),
        ),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Titled horizontal carousel of continue-watching cards with progress. */
@Composable
fun TvContinueWatchingRow(
    title: String,
    items: List<ContinueItem>,
    onVideoClick: (String) -> Unit,
    onWatchFromStart: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        ) {
            rowItemsIndexed(items, key = { index, item -> "${item.video.id}_$index" }) { _, item ->
                TvContinueWatchingCard(
                    item = item,
                    onClick = { onVideoClick(item.video.id) },
                    onWatchFromStart = { onWatchFromStart(item.video.id) },
                    onRemove = { onRemove(item.video.id) },
                )
            }
        }
    }
}

@Composable
fun TvContinueWatchingCard(
    item: ContinueItem,
    onClick: () -> Unit,
    onWatchFromStart: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
    Card(
        onClick = onClick,
        onLongClick = { menuExpanded = true },
        modifier = Modifier.width(220.dp),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White)),
        ),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AsyncImage(
                    model = item.video.thumbnailUrl,
                    contentDescription = item.video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    item.video.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = item.video.author?.name.orEmpty()
                if (author.isNotEmpty()) {
                    Text(
                        author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
    ) {
        DropdownMenuItem(
            text = { Text("Смотреть с начала") },
            onClick = {
                menuExpanded = false
                onWatchFromStart()
            },
        )
        DropdownMenuItem(
            text = { Text("Убрать из ряда") },
            onClick = {
                menuExpanded = false
                onRemove()
            },
        )
    }
    }
}

@Composable
fun TvLoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun TvErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            androidx.tv.material3.Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}
