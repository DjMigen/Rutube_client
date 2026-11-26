package ru.rutubeclient.mobile.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.network.RuTubeApi

class SearchViewModel(
    private val api: RuTubeApi,
    private val repository: LibraryRepository,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<Video> = emptyList(),
        val isLoading: Boolean = false,
        val hasNext: Boolean = false,
        val searched: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** Recent local search queries shown before typing. */
    val recentQueries = repository.recentQueries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var page = 1
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Debounced search-as-you-type. */
    fun onQuerySubmitted(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            repository.addSearchQuery(trimmed)
            page = 1
            _state.update { it.copy(isLoading = true, error = null, searched = true) }
            doSearch(trimmed, append = false)
        }
    }

    fun searchDebounced(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 3) return
        searchJob = viewModelScope.launch {
            delay(500)
            page = 1
            _state.update { it.copy(isLoading = true, error = null, searched = true) }
            doSearch(query.trim(), append = false)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasNext || s.isLoading || s.error != null) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            page++
            doSearch(s.query.trim(), append = true)
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _state.update { UiState() }
    }

    fun clearRecentQueries() {
        viewModelScope.launch { repository.clearSearchQueries() }
    }

    private suspend fun doSearch(query: String, append: Boolean) {
        runCatching { api.searchVideos(query, page) }
            .onSuccess { newPage ->
                _state.update {
                    it.copy(
                        results = if (append) it.results + newPage.items else newPage.items,
                        hasNext = newPage.hasNext,
                        isLoading = false,
                        error = null,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка поиска")
                }
            }
    }
}
