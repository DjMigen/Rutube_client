package ru.rutubeclient.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Watch history entry with the last playback position. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSec: Int,
    val authorId: Long?,
    val authorName: String?,
    val positionMs: Long,
    val watchedAt: Long,
)

/** Local subscription to a channel (no account needed). */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val authorId: Long,
    val name: String,
    val avatarUrl: String?,
    val addedAt: Long,
)

/** "Watch later" bookmark. */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSec: Int,
    val authorId: Long?,
    val authorName: String?,
    val addedAt: Long,
)

/** Search query history for suggestions on the search screen. */
@Entity(tableName = "search_queries")
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val usedAt: Long,
)

/** Download queue / downloaded videos metadata (mirrors Media3 DownloadManager). */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSec: Int,
    val authorName: String?,
    /** Signed HLS URL used at download time; playback of a completed download
     *  must reuse it so cache keys match (no network needed). */
    val hlsUrl: String,
    /** Requested video height (e.g. 1080, 720); 0 = best available. */
    val qualityHeight: Int,
    /** queued | downloading | completed | failed */
    val state: String,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val createdAt: Long,
) {
    companion object {
        const val STATE_QUEUED = "queued"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_COMPLETED = "completed"
        const val STATE_FAILED = "failed"
    }
}
