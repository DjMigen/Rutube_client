package ru.rutubeclient.core.network

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ru.rutubeclient.core.model.Video

/**
 * Concentrates the home-feed assembly that used to be duplicated between the
 * mobile and TV home screens: recommendations, trending, top movies/serials,
 * category rows and "because you watched".
 */
class HomeFeedLoader(
    private val api: RuTubeApi,
    private val categoryLimit: Int = 6,
    private val alwaysRecommendations: Boolean = false,
) {

    /** Minimal history entry needed for "because you watched" rows. */
    data class RecentHistoryEntry(
        val videoId: String,
        val title: String,
        val authorId: Long?,
    )

    enum class RowKind { VIDEOS, TOP10 }

    data class Row(val title: String, val videos: List<Video>, val kind: RowKind = RowKind.VIDEOS)

    data class HomeFeed(val hero: Video?, val rows: List<Row>)

    suspend fun load(recentHistory: List<RecentHistoryEntry> = emptyList()): HomeFeed = coroutineScope {
        val feedDeferred = async { api.getHomeFeed(1) }
        val categoriesDeferred = async {
            runCatching { api.getCategories() }.getOrDefault(emptyList())
        }
        val feed = feedDeferred.await()
        val categories = categoriesDeferred.await().take(categoryLimit)

        val topMoviesDeferred = async {
            runCatching { api.getTopMovies(1).items }.getOrDefault(emptyList()).take(10)
        }
        val topSerialsDeferred = async {
            runCatching { api.getTopSerials(1).items }.getOrDefault(emptyList()).take(10)
        }

        val rows = mutableListOf<Row>()
        if (alwaysRecommendations || feed.items.isNotEmpty()) {
            rows += Row("Рекомендации", feed.items)
        }
        val trending = feed.items.sortedByDescending { it.hits }.take(20)
        if (trending.isNotEmpty()) rows += Row("В тренде", trending)
        topMoviesDeferred.await().let {
            if (it.isNotEmpty()) rows += Row("Топ-10 фильмов", it, RowKind.TOP10)
        }
        topSerialsDeferred.await().let {
            if (it.isNotEmpty()) rows += Row("Топ-10 сериалов", it, RowKind.TOP10)
        }
        val categoryRows = categories.map { category ->
            async {
                runCatching { api.getCategoryVideos(category.id, 1) }
                    .getOrNull()
                    ?.let { Row(category.name, it.items) }
            }
        }.mapNotNull { it.await() }

        val becauseRows = recentHistory
            .take(2)
            .mapNotNull { entry ->
                val authorId = entry.authorId ?: return@mapNotNull null
                runCatching { api.getChannelVideos(authorId, 1) }
                    .getOrNull()
                    ?.items
                    ?.filter { it.id != entry.videoId }
                    ?.take(10)
                    ?.let {
                        val title = entry.title.take(24)
                        Row("Потому что вы смотрели «$title»", it)
                    }
            }

        val allRows: List<Row> = rows + categoryRows + becauseRows
        HomeFeed(hero = allRows.firstOrNull()?.videos?.firstOrNull(), rows = allRows)
    }
}
