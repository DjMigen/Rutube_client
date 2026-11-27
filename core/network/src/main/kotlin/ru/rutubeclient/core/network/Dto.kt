package ru.rutubeclient.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.rutubeclient.core.model.Author
import ru.rutubeclient.core.model.Comment
import ru.rutubeclient.core.model.Page
import ru.rutubeclient.core.model.TvShow
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.model.VideoCategory
import ru.rutubeclient.core.model.VideoDetails

@Serializable
internal data class AuthorDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("name") val name: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
internal data class CategoryDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
)

@Serializable
internal data class VideoDto(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("duration") val duration: Int = 0,
    @SerialName("hits") val hits: Long = 0,
    @SerialName("created_ts") val createdTs: String? = null,
    @SerialName("author") val author: AuthorDto? = null,
    @SerialName("category") val category: CategoryDto? = null,
    @SerialName("is_livestream") val isLivestream: Boolean = false,
    @SerialName("is_on_air") val isOnAir: Boolean = false,
)

@Serializable
internal data class VideoDetailsDto(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("duration") val duration: Int = 0,
    @SerialName("hits") val hits: Long = 0,
    @SerialName("created_ts") val createdTs: String? = null,
    @SerialName("author") val author: AuthorDto? = null,
    @SerialName("category") val category: CategoryDto? = null,
    @SerialName("is_livestream") val isLivestream: Boolean = false,
    @SerialName("is_on_air") val isOnAir: Boolean = false,
    @SerialName("hashtags") val hashtags: List<String> = emptyList(),
    @SerialName("hide_comments") val hideComments: Boolean = false,
    @SerialName("pg_rating") val pgRating: PgRatingDto? = null,
)

@Serializable
internal data class PgRatingDto(
    @SerialName("age") val age: Int = 0,
)

@Serializable
internal data class CommentAuthorDto(
    @SerialName("name") val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
internal data class CommentDto(
    @SerialName("id") val id: String = "",
    @SerialName("text") val text: String = "",
    @SerialName("created_ts") val createdTs: String? = null,
    @SerialName("user") val user: CommentAuthorDto? = null,
    @SerialName("likes_number") val likesCount: Int = 0,
    @SerialName("replies_number") val repliesCount: Int = 0,
)

/** List responses use either "page" or "current_page" depending on the endpoint. */
@Serializable
internal data class PageDto<T>(
    @SerialName("results") val results: List<T> = emptyList(),
    @SerialName("has_next") val hasNext: Boolean = false,
    @SerialName("page") val page: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
) {
    val effectivePage: Int get() = page ?: currentPage ?: 1
}

@Serializable
internal data class VideoBalancerDto(
    @SerialName("m3u8") val m3u8: String? = null,
    @SerialName("default") val default: String? = null,
)

@Serializable
internal data class PlayOptionsDto(
    @SerialName("video_balancer") val videoBalancer: VideoBalancerDto = VideoBalancerDto(),
)

@Serializable
internal data class CategoryListItemDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
)

@Serializable
internal data class TvShowTypeDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("title") val title: String = "",
)

@Serializable
internal data class TvShowGenreDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("link") val link: String? = null,
)

@Serializable
internal data class TvShowSeasonDto(
    @SerialName("number") val number: Int = 0,
    @SerialName("url") val url: String = "",
    @SerialName("lightweight_url") val lightweightUrl: String? = null,
)

@Serializable
internal data class TvShowDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("type") val type: TvShowTypeDto? = null,
    @SerialName("seasons_count") val seasonsCount: Int = 0,
    @SerialName("subscribers_count") val subscribersCount: Long = 0,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("vertical_poster_url") val verticalPosterUrl: String? = null,
    @SerialName("picture") val picture: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("genres") val genres: List<TvShowGenreDto> = emptyList(),
    @SerialName("year_start") val yearStart: String? = null,
    @SerialName("year_end") val yearEnd: String? = null,
)

internal fun AuthorDto.toModel() = Author(id = id, name = name, avatarUrl = avatarUrl)

internal fun CategoryDto.toModel() = VideoCategory(id = id, name = name)

internal fun VideoDto.toModel() = Video(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    durationSec = duration,
    hits = hits,
    createdTs = createdTs,
    author = author?.toModel(),
    category = category?.toModel(),
    isLive = isLivestream,
    isOnAir = isOnAir,
)

internal fun VideoDetailsDto.toModel() = VideoDetails(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    durationSec = duration,
    hits = hits,
    createdTs = createdTs,
    author = author?.toModel(),
    category = category?.toModel(),
    isLive = isLivestream,
    isOnAir = isOnAir,
    hashtags = hashtags,
    commentsEnabled = !hideComments,
    pgRatingAge = pgRating?.age,
)

internal fun CommentDto.toModel() = Comment(
    id = id,
    text = text,
    createdTs = createdTs,
    authorName = user?.name,
    authorAvatarUrl = user?.avatarUrl,
    likesCount = likesCount,
    repliesCount = repliesCount,
)

internal fun TvShowDto.toModel() = TvShow(
    id = id,
    name = name,
    type = type?.name ?: "",
    seasonsCount = seasonsCount,
    subscribersCount = subscribersCount,
    posterUrl = posterUrl ?: verticalPosterUrl ?: picture,
    description = description,
    genres = genres.map { it.name },
    yearStart = yearStart,
    yearEnd = yearEnd,
)

internal fun <T, R> PageDto<T>.toModel(mapper: (T) -> R) = Page(
    items = results.map(mapper),
    hasNext = hasNext,
    page = effectivePage,
)
