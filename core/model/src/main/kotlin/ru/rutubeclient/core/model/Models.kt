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
/** Parsed series information from a video title. */
data class SeriesInfo(
    val baseTitle: String,
    val season: Int?,
    val episode: Int?,
)

/**
 * Best-effort extraction of a series base title and season/episode numbers
 * from a RuTube video title.
 */
fun parseSeriesInfo(title: String): SeriesInfo? {
    var t = title.trim()
    if (t.isEmpty()) return null

    // Season + episode patterns: "Сериал, 1 сезон, 2 серия", "S01E02", "1x02", "1 сезон 2 выпуск"
    val seasonEpisodePatterns = listOf(
        Regex("""(?i)(.+?)[,\s]+(\d+)\s*сезон[,\s]+(\d+)\s*(?:серия|выпуск|эпизод)"""),
        Regex("""(?i)(.+?)[,\s]+s(\d{1,2})e(\d{1,3})"""),
        Regex("""(?i)(.+?)[,\s]+(\d{1,2})x(\d{1,3})"""),
        Regex("""(?i)(.+?)[,\s]+(\d+)\s*сезон[,\s]+(\d+)\s*часть"""),
    )
    for (p in seasonEpisodePatterns) {
        val m = p.find(t) ?: continue
        val base = m.groupValues[1].trim().trimEnd(',', ' ', '-')
        val season = m.groupValues[2].toIntOrNull()
        val episode = m.groupValues[3].toIntOrNull()
        if (base.length >= 3) return SeriesInfo(base, season, episode)
    }

    // Episode only: "Нулевой день 1 серия", "Сериал 5 выпуск"
    val episodeOnlyPatterns = listOf(
        Regex("""(?i)(.+?)[\s,]+(\d+)\s*(?:серия|выпуск|эпизод|часть)\b"""),
        Regex("""(?i)(.+?)[\s,/]+(\d+)\s*из\s*\d+"""),
    )
    for (p in episodeOnlyPatterns) {
        val m = p.find(t) ?: continue
        val base = m.groupValues[1].trim().trimEnd(',', ' ', '-')
        val episode = m.groupValues[2].toIntOrNull()
        if (base.length >= 3 && episode != null) return SeriesInfo(base, null, episode)
    }

    return null
}

/** Normalized title for fuzzy matching of series. */
fun normalizeSeriesTitle(title: String): String =
    title.lowercase()
        .replace(Regex("""[^a-zа-я0-9]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

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
