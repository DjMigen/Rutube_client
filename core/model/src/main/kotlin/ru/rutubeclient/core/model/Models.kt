package ru.rutubeclient.core.model

/** Author of a video (channel owner). */
data class Author(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
)

/** A video category (rubric). */
data class VideoCategory(
    val id: Int,
    val name: String,
)

/** A video as it appears in lists (feed, search, channel). */
data class Video(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val durationSec: Int,
    val hits: Long,
    val createdTs: String?,
    val author: Author?,
    val category: VideoCategory?,
    val isLive: Boolean,
    val isOnAir: Boolean,
) {
    val isLiveNow: Boolean get() = isLive && isOnAir
}

/** Full video details for the watch screen. */
data class VideoDetails(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val durationSec: Int,
    val hits: Long,
    val createdTs: String?,
    val author: Author?,
    val category: VideoCategory?,
    val isLive: Boolean,
    val isOnAir: Boolean,
    val hashtags: List<String>,
    val commentsEnabled: Boolean,
    val pgRatingAge: Int?,
)

/** A comment under a video. */
data class Comment(
    val id: String,
    val text: String,
    val createdTs: String?,
    val authorName: String?,
    val authorAvatarUrl: String?,
    val likesCount: Int,
    val repliesCount: Int,
)

/** A page of results with pagination info. */
data class Page<T>(
    val items: List<T>,
    val hasNext: Boolean,
    val page: Int,
)

/**
 * Resolved playback info. [hlsUrl] is a signed master-playlist URL that is
 * short-lived: it must be requested right before playback and re-requested
 * when it expires (HTTP 403 from the CDN).
 */
data class StreamInfo(
    val hlsUrl: String,
    val isLive: Boolean,
)
