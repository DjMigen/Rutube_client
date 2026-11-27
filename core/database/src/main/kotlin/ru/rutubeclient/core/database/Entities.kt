package ru.rutubeclient.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.rutubeclient.core.model.Author
import ru.rutubeclient.core.model.Video

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
) {
    /** Playback progress in 0..1. */
    val progress: Float
        get() = if (durationSec > 0) {
            (positionMs.toFloat() / (durationSec * 1000f)).coerceIn(0f, 1f)
        } else {
            0f
        }

    /** True if the video was started but not finished yet. */
    val isInProgress: Boolean get() = positionMs > 0L && progress < 0.95f

    /** Convert to a [Video] suitable for display in feeds (continue watching). */
    fun toVideo(): Video = Video(
        id = videoId,
        title = title,
        description = null,
        thumbnailUrl = thumbnailUrl,
        durationSec = durationSec,
        hits = 0,
        createdTs = null,
        author = authorId?.let { Author(it, authorName.orEmpty(), null) },
        category = null,
        isLive = false,
        isOnAir = false,
    )
}

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

/** Local thumbs-up / thumbs-down rating for a video. */
@Entity(tableName = "ratings")
data class RatingEntity(
    @PrimaryKey val videoId: String,
    val liked: Boolean,
    val ratedAt: Long,
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
