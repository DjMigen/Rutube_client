package ru.rutubeclient.core.model

/**
 * Series heuristics: recognizing episodes from video titles and ordering
 * them for playback. Pure functions — the single source of truth for
 * "which videos belong to a series season and in what order", used by the
 * video screens (episode rows) and SeriesDiscovery.
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
    val t = title.trim()
    if (t.isEmpty()) return null

    // Season + episode patterns: "Сериал, 1 сезон, 2 серия", "S01E02", "1x02", "1 сезон 2 часть"
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

    // Episode only: "Нулевой день 1 серия", "Сериал 5 выпуск".
    // NB: no `\b` after the keyword — on JVM 19+ `\b` is ASCII-only and never
    // matches after Cyrillic; the lookahead is the portable equivalent.
    val episodeOnlyPatterns = listOf(
        Regex("""(?i)(.+?)[\s,]+(\d+)\s*(?:серия|выпуск|эпизод|часть)(?![а-яёa-z])"""),
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

/**
 * Season of a video: parsed from the title first, then the metadata
 * [Video.season], defaulting to 1.
 */
fun seasonOf(video: Video): Int =
    parseSeriesInfo(video.title)?.season ?: video.season ?: 1

/** Episode number parsed from the title; null when unknown. */
fun episodeOf(video: Video): Int? =
    parseSeriesInfo(video.title)?.episode

/**
 * Episodes of [season] in playback order: filtered by [seasonOf], sorted by
 * [episodeOf] (unknown episode numbers go last). When the filter yields
 * nothing (or [season] is null), all [videos] are returned sorted — an empty
 * "Серии" row is worse than episodes from other seasons.
 */
fun episodesForSeason(videos: List<Video>, season: Int?): List<Video> {
    val filtered = if (season == null) emptyList() else videos.filter { seasonOf(it) == season }
    return filtered.ifEmpty { videos }.sortedBy { episodeOf(it) ?: Int.MAX_VALUE }
}
