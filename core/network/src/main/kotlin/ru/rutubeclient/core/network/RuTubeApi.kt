package ru.rutubeclient.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.rutubeclient.core.model.Comment
import ru.rutubeclient.core.model.Page
import ru.rutubeclient.core.model.StreamInfo
import ru.rutubeclient.core.model.TvShow
import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.model.VideoCategory
import ru.rutubeclient.core.model.VideoDetails
import java.util.concurrent.TimeUnit

/** Thrown when the API answers with a non-2xx code or an unexpected body. */
class RuTubeApiException(val code: Int, message: String) : Exception(message)

/** Thrown when a video has no playable stream (paid, deleted, geo-blocked...). */
class StreamUnavailableException(message: String) : Exception(message)

/**
 * Port for RuTube's internal (website) API. Production uses [HttpRuTubeApi];
 * tests can supply an in-memory implementation.
 */
interface RuTubeApi {
    suspend fun getHomeFeed(page: Int = 1): Page<Video>
    suspend fun getCategories(): List<VideoCategory>
    suspend fun getTvShows(page: Int = 1): Page<TvShow>
    suspend fun getTvShow(id: Int): TvShow
    suspend fun getTvShowSeasons(tvId: Int): List<Int>
    suspend fun getTvShowVideos(tvId: Int, page: Int = 1, season: Int? = null): Page<Video>
    suspend fun getTopMovies(page: Int = 1): Page<Video>
    suspend fun getTopSerials(page: Int = 1): Page<Video>
    suspend fun getTopCartoons(page: Int = 1): Page<Video>
    suspend fun getTopAnime(page: Int = 1): Page<Video>
    suspend fun getCategoryVideos(categoryId: Int, page: Int = 1): Page<Video>
    suspend fun searchVideos(query: String, page: Int = 1): Page<Video>
    suspend fun getVideo(id: String): VideoDetails
    suspend fun getStreamInfo(id: String): StreamInfo
    suspend fun getComments(videoId: String, page: Int = 1): Page<Comment>
    suspend fun getChannelVideos(authorId: Long, page: Int = 1): Page<Video>
}

/**
 * HTTP implementation of [RuTubeApi]. All endpoints were verified against the
 * live service. Streams are served as signed, short-lived HLS master-playlist
 * URLs: always call [getStreamInfo] right before playback and again when the
 * CDN starts answering 403 (signature expired).
 */
class HttpRuTubeApi(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL,
) : RuTubeApi {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun getHomeFeed(page: Int): Page<Video> =
        getPage("/api/v2/video/recommendation/main?page=$page") { it: VideoDto -> it.toModel() }

    override suspend fun getCategories(): List<VideoCategory> = withContext(Dispatchers.IO) {
        val body = get("/api/video/category/")
        json.decodeFromString<List<CategoryListItemDto>>(body)
            .map { VideoCategory(it.id, it.name) }
            .filter { it.name.isNotBlank() }
    }

    override suspend fun getTvShows(page: Int): Page<TvShow> =
        getPage("/api/metainfo/tv/?page=$page") { it: TvShowDto -> it.toModel() }

    override suspend fun getTvShow(id: Int): TvShow = withContext(Dispatchers.IO) {
        json.decodeFromString<TvShowDto>(get("/api/metainfo/tv/$id/")).toModel()
    }

    override suspend fun getTvShowSeasons(tvId: Int): List<Int> = withContext(Dispatchers.IO) {
        runCatching {
            json.decodeFromString<List<TvShowSeasonDto>>(get("/api/metainfo/tv/$tvId/season"))
                .map { it.number }
                .distinct()
                .sorted()
        }.getOrDefault(emptyList())
    }

    override suspend fun getTvShowVideos(tvId: Int, page: Int, season: Int?): Page<Video> {
        val path = if (season == null) {
            "/api/metainfo/tv/$tvId/video?page=$page"
        } else {
            "/api/metainfo/tv/$tvId/video?season=$season&page=$page"
        }
        return getPage(path) { it: VideoDto -> it.toModel() }
    }

    override suspend fun getTopMovies(page: Int): Page<Video> =
        getPage("/api/v2/video/recommendation/main/top_movie?limit=20&page=$page") { it: VideoDto -> it.toModel() }

    override suspend fun getTopSerials(page: Int): Page<Video> =
        getPage("/api/v2/video/recommendation/main/top_serial?show_hidden_videos=False&limit=20&page=$page") { it: VideoDto -> it.toModel() }

    override suspend fun getTopCartoons(page: Int): Page<Video> =
        getPage("/api/v2/video/recommendation/main/top_cartoon?limit=20&page=$page") { it: VideoDto -> it.toModel() }

    override suspend fun getTopAnime(page: Int): Page<Video> =
        getPage("/api/v2/video/recommendation/main/top_anime?limit=20&page=$page") { it: VideoDto -> it.toModel() }

    override suspend fun getCategoryVideos(categoryId: Int, page: Int): Page<Video> =
        getPage("/api/video/category/$categoryId/?page=$page") { it: VideoDto -> it.toModel() }

    override suspend fun searchVideos(query: String, page: Int): Page<Video> =
        getPage("/api/search/video/?query=${query.urlEncoded()}&page=$page") { it: VideoDto -> it.toModel() }

    override suspend fun getVideo(id: String): VideoDetails = withContext(Dispatchers.IO) {
        json.decodeFromString<VideoDetailsDto>(get("/api/video/$id/")).toModel()
    }

    override suspend fun getStreamInfo(id: String): StreamInfo = withContext(Dispatchers.IO) {
        val dto = json.decodeFromString<PlayOptionsDto>(get("/api/play/options/$id/?format=json"))
        val url = dto.videoBalancer.m3u8 ?: dto.videoBalancer.default
        if (url.isNullOrBlank()) {
            throw StreamUnavailableException("Поток недоступен для видео $id")
        }
        StreamInfo(hlsUrl = url, isLive = false)
    }

    override suspend fun getComments(videoId: String, page: Int): Page<Comment> =
        getPage("/api/comments/video/$videoId/?page=$page") { it: CommentDto -> it.toModel() }

    override suspend fun getChannelVideos(authorId: Long, page: Int): Page<Video> =
        getPage("/api/video/person/$authorId/?page=$page") { it: VideoDto -> it.toModel() }

    private suspend inline fun <reified T, R> getPage(
        path: String,
        crossinline mapper: (T) -> R,
    ): Page<R> = withContext(Dispatchers.IO) {
        val body = get(path)
        val dto: PageDto<T> = json.decodeFromString(body)
        dto.toModel { mapper(it) }
    }

    private fun get(path: String): String {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuTubeApiException(response.code, "HTTP ${response.code} for $path")
            }
            return response.body?.string()
                ?: throw RuTubeApiException(response.code, "Empty body for $path")
        }
    }

    private fun String.urlEncoded(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    companion object {
        const val BASE_URL = "https://rutube.ru"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
