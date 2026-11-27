package ru.rutubeclient.tv.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.rutubeclient.core.model.CatalogEntry
import ru.rutubeclient.core.model.CatalogTab
import ru.rutubeclient.core.model.Page
import ru.rutubeclient.core.network.CatalogLoader
import ru.rutubeclient.core.network.RuTubeApi

class TvCatalogViewModel(private val api: RuTubeApi) : ViewModel() {
    private val loader = CatalogLoader(api)

    data class UiState(
        val items: List<CatalogEntry> = emptyList(),
        val selectedTab: CatalogTab = CatalogTab.ALL,
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val hasNext: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var page = 1

    init {
        refresh()
    }

    fun selectTab(tab: CatalogTab) {
        if (_state.value.selectedTab == tab) return
        _state.update { it.copy(selectedTab = tab, items = emptyList()) }
        refresh()
    }

    fun refresh() {
        page = 1
        _state.update { it.copy(isLoading = true, error = null, hasNext = true) }
        load(append = false)
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasNext || s.isLoading || s.isLoadingMore || s.error != null) return
        page++
        load(append = true)
    }

    private fun load(append: Boolean) {
        viewModelScope.launch {
            val tab = _state.value.selectedTab
            val result = runCatching { loader.load(tab, page) }
            result
                .onSuccess { newPage ->
                    _state.update {
                        it.copy(
                            items = if (append) it.items + newPage.items else newPage.items,
                            hasNext = newPage.hasNext,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = e.message ?: "Ошибка загрузки",
                        )
                    }
                }
        }
    }

}
