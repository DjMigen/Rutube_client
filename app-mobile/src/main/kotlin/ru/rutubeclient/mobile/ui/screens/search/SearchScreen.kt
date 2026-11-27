package ru.rutubeclient.mobile.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as columnItemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rutubeclient.mobile.ui.components.ErrorBox
import ru.rutubeclient.mobile.ui.components.LoadingBox
import ru.rutubeclient.mobile.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recentQueries.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Поиск") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        TextField(
            value = state.query,
            onValueChange = {
                viewModel.onQueryChange(it)
                viewModel.searchDebounced(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            placeholder = { Text("Поиск видео…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Default.Close, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { viewModel.onQuerySubmitted(state.query) }
            ),
        )

        val suggestions = remember(state.query, recent) {
            recent.map { it.query }
                .filter { it.contains(state.query, ignoreCase = true) && it != state.query }
                .take(6)
        }
        if (suggestions.isNotEmpty()) {
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions, key = { it }) { query ->
                    SuggestionChip(
                        onClick = {
                            viewModel.onQueryChange(query)
                            viewModel.onQuerySubmitted(query)
                        },
                        label = { Text(query) },
                    )
                }
            }
        }

        when {
            state.isLoading && state.results.isEmpty() -> LoadingBox(Modifier.weight(1f))

            state.error != null && state.results.isEmpty() -> ErrorBox(
                message = state.error ?: "Ошибка",
                onRetry = { viewModel.onQuerySubmitted(state.query) },
                modifier = Modifier.weight(1f),
            )

            state.results.isNotEmpty() -> SearchResults(
                state = state,
                onVideoClick = onVideoClick,
                onLoadMore = viewModel::loadMore,
                modifier = Modifier.weight(1f),
            )

            state.searched && !state.isLoading -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Ничего не найдено", style = MaterialTheme.typography.bodyLarge)
            }

            else -> RecentQueries(
                queries = recent.map { it.query },
                onSelect = {
                    viewModel.onQueryChange(it)
                    viewModel.onQuerySubmitted(it)
                },
                onClear = viewModel::clearRecentQueries,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchViewModel.UiState,
    onVideoClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 170.dp),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        gridItemsIndexed(state.results, key = { index, video -> "${video.id}_$index" }) { _, video ->
            VideoCard(video = video, onClick = { onVideoClick(video.id) })
        }
        if (state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                androidx.compose.material3.CircularProgressIndicator(
                    Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentQueries(
    queries: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (queries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Введите запрос, чтобы найти видео",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Недавние запросы",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Очистить") }
            }
            LazyColumn {
                columnItemsIndexed(queries, key = { index, query -> "${query}_$index" }) { _, query ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(query) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(query, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}
