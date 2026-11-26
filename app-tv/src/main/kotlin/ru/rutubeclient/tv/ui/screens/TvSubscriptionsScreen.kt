package ru.rutubeclient.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.tv.ui.TvLoadingBox

class TvSubscriptionsViewModel(repository: LibraryRepository) : ViewModel() {
    val subscriptions = repository.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Channel card: round avatar + name, focusable. */
@Composable
private fun ChannelCard(
    name: String,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = Modifier.width(180.dp),
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.06f),
        border = androidx.tv.material3.CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.ui.graphics.Color.White),
            ),
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    modifier = Modifier.size(72.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Spacer(Modifier.size(72.dp))
            }
            Spacer(Modifier.height(10.dp))
            androidx.tv.material3.Text(
                name,
                style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun TvSubscriptionsScreen(
    viewModel: TvSubscriptionsViewModel,
    onChannelClick: (Long, String) -> Unit,
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        androidx.tv.material3.Text(
            "Подписки",
            style = androidx.tv.material3.MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        if (subscriptions.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.tv.material3.Text(
                    "Вы пока ни на кого не подписаны",
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(subscriptions, key = { it.authorId }) { sub ->
                    ChannelCard(
                        name = sub.name,
                        avatarUrl = sub.avatarUrl,
                        onClick = { onChannelClick(sub.authorId, sub.name) },
                    )
                }
            }
        }
    }
}
