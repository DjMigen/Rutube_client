package ru.rutubeclient.mobile.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.mobile.download.DownloadsManager

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val downloadsManager: DownloadsManager,
) : ViewModel() {

    val history = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks = repository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeHistory(videoId: String) {
        viewModelScope.launch { repository.removeHistory(videoId) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun removeBookmark(videoId: String) {
        viewModelScope.launch { repository.removeBookmark(videoId) }
    }

    fun removeDownload(videoId: String) {
        downloadsManager.remove(videoId)
    }
}
