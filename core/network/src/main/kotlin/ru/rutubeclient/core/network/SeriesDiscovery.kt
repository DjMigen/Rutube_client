package ru.rutubeclient.core.network

import ru.rutubeclient.core.model.Video
import ru.rutubeclient.core.model.VideoDetails
import ru.rutubeclient.core.model.episodeOf
import ru.rutubeclient.core.model.normalizeSeriesTitle
import ru.rutubeclient.core.model.parseSeriesInfo
import ru.rutubeclient.core.model.seasonOf

/**
 * Concentrates the "which episodes belong to this series and what is the next one"
 * logic that used to be duplicated between the mobile and TV video screens.
 *
 * Takes a [VideoDetails] plus the optional id passed in via navigation and returns
 * the discovered episodes, seasons, selected season and resolved next id.
 * Returns null when nothing could be discovered.
 */
class SeriesDiscovery(private val api: RuTubeApi) {

    data class Result(
        val seriesEpisodes: List<Video>,
        val seriesSeasons: List<Int>,
        val selectedSeriesSeason: Int?,
        val nextVideoId: String?,
    )

    suspend fun discover(
        details: VideoDetails,
        videoId: String,
        passedNextVideoId: String? = null,
        discoverWhenNextProvided: Boolean = true,
    ): Result? {
        if (passedNextVideoId != null && !discoverWhenNextProvided) return null

        val tvShowId = details.tvShowId
        if (tvShowId != null) {
            val season = details.season ?: 1
            // All seasons of the show, so the season selector is complete.
            val allSeasons = runCatching { api.getTvShowSeasons(tvShowId) }
                .getOrNull()
                .orEmpty()
                .ifEmpty { listOf(season) }
            return runCatching { api.getTvShowVideos(tvShowId, 1, season) }
                .getOrNull()
                ?.let { page ->
                    val episodes = page.items.filter { it.id != videoId }
                    if (episodes.isEmpty()) null
                    else Result(
                        seriesEpisodes = episodes,
                        seriesSeasons = allSeasons,
                        selectedSeriesSeason = season,
                        nextVideoId = passedNextVideoId,
                    )
                }
        }

        val currentInfo = parseSeriesInfo(details.title) ?: return null
        val baseNorm = normalizeSeriesTitle(currentInfo.baseTitle)
        if (baseNorm.length < 3) return null

        val authorId = details.author?.id ?: return null
        val candidatePool = mutableListOf<Video>()

        for (page in 1..3) {
            runCatching { api.getChannelVideos(authorId, page) }
                .onSuccess { p -> candidatePool += p.items }
        }
        runCatching { api.searchVideos(currentInfo.baseTitle, 1) }
            .onSuccess { p ->
                candidatePool += p.items.filter { it.author?.id == authorId }
            }

        val candidates = candidatePool
            .distinctBy { it.id }
            .filter { v ->
                v.id != videoId && parseSeriesInfo(v.title)?.let { info ->
                    normalizeSeriesTitle(info.baseTitle) == baseNorm
                } == true
            }

        if (candidates.isEmpty()) return null

        val seasons = candidates.mapNotNull { parseSeriesInfo(it.title)?.season ?: it.season }
            .distinct().sorted()
            .ifEmpty { listOf(1) }

        val currentSeason = currentInfo.season
            ?: candidates.firstOrNull()?.let { parseSeriesInfo(it.title)?.season } ?: 1
        val currentEpisode = currentInfo.episode ?: 0

        val seasonCandidates = candidates.filter { seasonOf(it) == currentSeason }
        val sortedEpisodes = seasonCandidates
            .sortedBy { episodeOf(it) ?: Int.MAX_VALUE }
        val nextId = sortedEpisodes
            .firstOrNull { (episodeOf(it) ?: Int.MAX_VALUE) > currentEpisode }
            ?.id

        return Result(
            seriesEpisodes = candidates,
            seriesSeasons = seasons,
            selectedSeriesSeason = currentSeason,
            nextVideoId = nextId ?: passedNextVideoId,
        )
    }
}
