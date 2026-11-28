package ru.rutubeclient.tv.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rutubeclient.core.presentation.HomeViewModel
import ru.rutubeclient.tv.ui.TvContinueWatchingRow
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvHeroBanner
import ru.rutubeclient.tv.ui.TvLoadingBox
import ru.rutubeclient.tv.ui.TvTop10Row
import ru.rutubeclient.tv.ui.TvVideoRow

/** TV home: hero banner, continue-watching and vertical list of horizontal rows. */
@Composable
fun TvHomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (String) -> Unit,
    onWatchFromStart: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> TvLoadingBox()
        state.error != null -> TvErrorBox(
            message = state.error ?: "Ошибка",
            onRetry = viewModel::load,
        )

        else -> LazyColumn(Modifier.fillMaxSize()) {
            val hero = state.hero
            if (hero != null) {
                item(key = "hero") {
                    TvHeroBanner(
                        video = hero,
                        isBookmarked = state.heroBookmarked,
                        onPlay = { onVideoClick(hero.id) },
                        onToggleList = viewModel::toggleHeroBookmark,
                        onSurprise = { viewModel.surpriseMe()?.let(onVideoClick) },
                        previewStreamUrl = state.heroStreamUrl,
                    )
                }
            }
            if (state.continueWatching.isNotEmpty()) {
                item(key = "continue") {
                    Spacer(Modifier.height(16.dp))
                    TvContinueWatchingRow(
                        title = "Продолжить просмотр",
                        items = state.continueWatching,
                        onVideoClick = onVideoClick,
                        onWatchFromStart = onWatchFromStart,
                        onRemove = viewModel::hideContinue,
                    )
                }
            }
            state.rows.forEach { row ->
                item(key = row.title) {
                    Spacer(Modifier.height(24.dp))
                    if (row.kind == HomeViewModel.RowKind.TOP10) {
                        TvTop10Row(
                            title = row.title,
                            videos = row.videos,
                            onVideoClick = onVideoClick,
                        )
                    } else {
                        TvVideoRow(
                            title = row.title,
                            videos = row.videos,
                            onVideoClick = onVideoClick,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
