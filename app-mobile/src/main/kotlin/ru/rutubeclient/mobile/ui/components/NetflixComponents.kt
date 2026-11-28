package ru.rutubeclient.mobile.ui.components

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.player.PlayerFactory
import ru.rutubeclient.core.presentation.ContinueItem
import ru.rutubeclient.mobile.ui.formatDuration

/**
 * Large featured banner with backdrop, title, description and action buttons.
 */
@Composable
fun HeroBanner(
    video: Video,
    isBookmarked: Boolean,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    previewStreamUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
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
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.92f),
                        ),
                        startY = 0.3f,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                video.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(
                video.author?.name?.takeIf { it.isNotEmpty() },
                formatDuration(video.durationSec).takeIf { it.isNotEmpty() },
            ).joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            val desc = video.description?.takeIf { it.isNotBlank() }
            if (desc != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Смотреть")
                }
                OutlinedButton(
                    onClick = onToggleList,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(
                        if (isBookmarked) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isBookmarked) "В списке" else "Мой список")
                }
            }
        }
    }
}

/**
 * Muted, looping, auto-playing video preview for the hero banner.
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
        PlayerFactory.create(context).apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(PlayerFactory.mediaItem(streamUrl, streamUrl))
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

/** Titled horizontal carousel of Netflix-style video cards. */
@Composable
fun VideoRow(
    title: String,
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(videos, key = { index, video -> "${video.id}_$index" }) { _, video ->
                VideoCard(
                    video = video,
                    onClick = { onVideoClick(video.id) },
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}

/** Titled horizontal carousel of Top-10 cards with big rank numbers. */
@Composable
fun Top10Row(
    title: String,
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(videos, key = { index, video -> "${video.id}_$index" }) { index, video ->
                Top10Card(
                    video = video,
                    rank = index + 1,
                    onClick = { onVideoClick(video.id) },
                )
            }
        }
    }
}

@Composable
fun Top10Card(
    video: Video,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(200.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            rank.toString(),
            fontSize = 72.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 4.dp),
            style = MaterialTheme.typography.displayMedium,
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

/** Titled horizontal carousel of continue-watching cards with progress. */
@Composable
fun ContinueWatchingRow(
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
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "${item.video.id}_$index" }) { _, item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onVideoClick(item.video.id) },
                    onWatchFromStart = { onWatchFromStart(item.video.id) },
                    onRemove = { onRemove(item.video.id) },
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    item: ContinueItem,
    onClick: () -> Unit,
    onWatchFromStart: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                ),
        ) {
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
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    item.video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = item.video.author?.name.orEmpty()
                if (author.isNotEmpty()) {
                    Text(
                        author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
