package ru.rutubeclient.core.database

import kotlinx.coroutines.flow.Flow
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.model.VideoDetails

/**
 * Single entry point for all locally stored user data:
 * history, subscriptions, bookmarks, search history and downloads.
 */
class LibraryRepository(db: AppDatabase) {

    private val historyDao = db.historyDao()
    private val subscriptionDao = db.subscriptionDao()
    private val bookmarkDao = db.bookmarkDao()
    private val searchQueryDao = db.searchQueryDao()
    private val downloadDao = db.downloadDao()
    private val ratingDao = db.ratingDao()

    // --- History ---

    val history: Flow<List<HistoryEntity>> = historyDao.observeAll()

    suspend fun getHistoryEntry(videoId: String): HistoryEntity? = historyDao.get(videoId)

    suspend fun savePosition(details: VideoDetails, positionMs: Long) {
        historyDao.upsert(
            HistoryEntity(
                videoId = details.id,
                title = details.title,
                thumbnailUrl = details.thumbnailUrl,
                durationSec = details.durationSec,
                authorId = details.author?.id,
                authorName = details.author?.name,
                positionMs = positionMs,
                watchedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeHistory(videoId: String) = historyDao.remove(videoId)

    /** Reset the saved playback position to 0 ("watch from the beginning"). */
    suspend fun resetPosition(videoId: String) = historyDao.resetPosition(videoId)

    suspend fun clearHistory() = historyDao.clear()

    // --- Subscriptions ---

    val subscriptions: Flow<List<SubscriptionEntity>> = subscriptionDao.observeAll()

    fun isSubscribed(authorId: Long): Flow<Boolean> = subscriptionDao.observeIsSubscribed(authorId)

    suspend fun subscribe(authorId: Long, name: String, avatarUrl: String?) =
        subscriptionDao.upsert(
            SubscriptionEntity(authorId, name, avatarUrl, System.currentTimeMillis())
        )

    suspend fun unsubscribe(authorId: Long) = subscriptionDao.remove(authorId)

    // --- Bookmarks ---

    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()

    fun isBookmarked(videoId: String): Flow<Boolean> = bookmarkDao.observeExists(videoId)

    suspend fun addBookmark(details: VideoDetails) = bookmarkDao.upsert(
        BookmarkEntity(
            videoId = details.id,
            title = details.title,
            thumbnailUrl = details.thumbnailUrl,
            durationSec = details.durationSec,
            authorId = details.author?.id,
            authorName = details.author?.name,
            addedAt = System.currentTimeMillis(),
        )
    )

    suspend fun addBookmark(video: Video) = bookmarkDao.upsert(
        BookmarkEntity(
            videoId = video.id,
            title = video.title,
            thumbnailUrl = video.thumbnailUrl,
            durationSec = video.durationSec,
            authorId = video.author?.id,
            authorName = video.author?.name,
            addedAt = System.currentTimeMillis(),
        )
    )

    suspend fun removeBookmark(videoId: String) = bookmarkDao.remove(videoId)

    // --- Search history ---

    val recentQueries: Flow<List<SearchQueryEntity>> = searchQueryDao.observeRecent()

    suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            searchQueryDao.upsert(SearchQueryEntity(trimmed, System.currentTimeMillis()))
        }
    }

    suspend fun clearSearchQueries() = searchQueryDao.clear()

    // --- Downloads ---

    val downloads: Flow<List<DownloadEntity>> = downloadDao.observeAll()

    suspend fun getDownload(videoId: String): DownloadEntity? = downloadDao.get(videoId)

    suspend fun upsertDownload(entity: DownloadEntity) = downloadDao.upsert(entity)

    suspend fun removeDownload(videoId: String) = downloadDao.remove(videoId)

    // --- Ratings ---

    suspend fun getRating(videoId: String): RatingEntity? = ratingDao.get(videoId)

    suspend fun rateVideo(videoId: String, liked: Boolean) = ratingDao.upsert(
        RatingEntity(videoId, liked, System.currentTimeMillis())
    )

    suspend fun removeRating(videoId: String) = ratingDao.remove(videoId)
}
