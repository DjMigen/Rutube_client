package ru.rutubeclient.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.tv.ui.TvErrorBox
import ru.rutubeclient.tv.ui.TvLoadingBox
import ru.rutubeclient.tv.ui.TvVideoCard

class TvSearchViewModel(private val api: RuTubeApi) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<Video> = emptyList(),
        val isLoading: Boolean = false,
        val searched: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 3) return
        searchJob = viewModelScope.launch {
            delay(500)
            doSearch(query.trim())
        }
    }

    fun submit() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { doSearch(q) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(300)
            doSearch(q)
        }
    }

    private suspend fun doSearch(query: String) {
        _state.update { it.copy(isLoading = true, searched = true, error = null) }
        runCatching { api.searchVideos(query, 1) }
            .onSuccess { page ->
                _state.update { it.copy(results = page.items, isLoading = false) }
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
    }
}

@Composable
fun TvSearchScreen(
    viewModel: TvSearchViewModel,
    onVideoClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchFieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }

    LaunchedEffect(state.results) {
        if (state.results.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            runCatching { firstResultFocus.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    state.results.isNotEmpty()
                ) {
                    runCatching { firstResultFocus.requestFocus() }
                    true
                } else {
                    false
                }
            }
    ) {
        androidx.tv.material3.Text(
            "Поиск",
            style = androidx.tv.material3.MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFieldFocus),
            placeholder = { androidx.compose.material3.Text("Поиск видео…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
        )
        Spacer(Modifier.height(16.dp))

        when {
            state.isLoading -> TvLoadingBox()
            state.error != null -> TvErrorBox(
                message = state.error ?: "Ошибка",
                onRetry = viewModel::submit,
            )

            state.results.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            runCatching { searchFieldFocus.requestFocus() }
                            true
                        } else {
                            false
                        }
                    },
            ) {
                gridItemsIndexed(state.results, key = { index, video -> "${video.id}_$index" }) { index, video ->
                    TvVideoCard(
                        video = video,
                        onClick = { onVideoClick(video.id) },
                        modifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier,
                    )
                }
            }

            state.searched -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.tv.material3.Text(
                    "Ничего не найдено",
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
