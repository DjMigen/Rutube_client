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
    val season: Int? = null,
    val tvShowId: Int? = null,
) {
    val isLiveNow: Boolean get() = isLive && isOnAir

    /** True if the video was created within the last [withinDays] days. */
    fun isRecent(withinDays: Long = 14): Boolean {
        val ts = createdTs ?: return false
        return try {
            val created = java.time.LocalDateTime.parse(ts)
            created.isAfter(java.time.LocalDateTime.now().minusDays(withinDays))
        } catch (_: Exception) {
            false
        }
    }
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
    val season: Int? = null,
    val tvShowId: Int? = null,
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
 * A TV show / series entry from the structured metainfo catalog.
 *
 * @param type Backend type name: "series" for serials, "tvshow" for shows.
 * @param posterUrl Best available poster (poster_url, vertical_poster_url or picture).
 */
data class TvShow(
    val id: Int,
    val name: String,
    val type: String,
    val seasonsCount: Int,
    val subscribersCount: Long,
    val posterUrl: String?,
    val description: String?,
    val genres: List<String>,
    val yearStart: String?,
    val yearEnd: String?,
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

/** A catalog grid entry: either a structured TV show or a plain video. */
sealed interface CatalogEntry {
    data class Show(val show: TvShow) : CatalogEntry
    data class Video(val video: ru.rutubeclient.core.model.Video) : CatalogEntry
}

/** Catalog tab shared by mobile and TV. */
enum class CatalogTab(val label: String) {
    ALL("Все"),
    SERIES("Сериалы"),
    TV_SHOWS("ТВ-шоу"),
    MOVIES("Фильмы"),
    CARTOONS("Мультики"),
    ANIME("Аниме"),
}
