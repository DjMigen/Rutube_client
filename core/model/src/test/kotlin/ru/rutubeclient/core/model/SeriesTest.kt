package ru.rutubeclient.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesTest {

    private fun video(title: String, season: Int? = null, id: String = title) = Video(
        id = id,
        title = title,
        description = null,
        thumbnailUrl = null,
        durationSec = 0,
        hits = 0,
        createdTs = null,
        author = null,
        category = null,
        isLive = false,
        isOnAir = false,
        season = season,
    )

    // --- parseSeriesInfo: table of title → expected SeriesInfo ---

    @Test
    fun `season and episode patterns`() {
        val cases = listOf(
            "Интерны, 1 сезон, 3 серия" to SeriesInfo("Интерны", 1, 3),
            "Шторм 2 сезон 5 выпуск" to SeriesInfo("Шторм", 2, 5),
            "Триггер, 3 сезон, 7 эпизод" to SeriesInfo("Триггер", 3, 7),
            "Severance S02E03" to SeriesInfo("Severance", 2, 3),
            "Dark 1x02" to SeriesInfo("Dark", 1, 2),
            "Универ 2 сезон 4 часть" to SeriesInfo("Универ", 2, 4),
        )
        for ((title, expected) in cases) {
            assertEquals(title, expected, parseSeriesInfo(title))
        }
    }

    @Test
    fun `episode-only patterns`() {
        val cases = listOf(
            "Нулевой день 1 серия" to SeriesInfo("Нулевой день", null, 1),
            "Кухня 5 выпуск" to SeriesInfo("Кухня", null, 5),
            "Финал 3 из 10" to SeriesInfo("Финал", null, 3),
        )
        for ((title, expected) in cases) {
            assertEquals(title, expected, parseSeriesInfo(title))
        }
    }

    @Test
    fun `non-series titles return null`() {
        val cases = listOf(
            "Топ 10 фильмов недели",
            "Утро ТНТ, 27 ноября 2026",
            "Обзор iPhone 17",
            "",
            "AB 1 серия", // base title too short
        )
        for (title in cases) {
            assertNull(title, parseSeriesInfo(title))
        }
    }

    @Test
    fun `normalizeSeriesTitle strips punctuation and case`() {
        assertEquals(
            "интерны лобанов и кастрюля",
            normalizeSeriesTitle("Интерны: Лобанов и кастрюля!"),
        )
        assertEquals("sherlock bbc", normalizeSeriesTitle("  Sherlock   (BBC) "))
    }

    // --- seasonOf / episodeOf ---

    @Test
    fun `seasonOf prefers title over metadata and defaults to 1`() {
        assertEquals(3, seasonOf(video("Шторм, 3 сезон, 1 серия", season = 2)))
        assertEquals(2, seasonOf(video("Просто видео", season = 2)))
        assertEquals(1, seasonOf(video("Просто видео")))
    }

    @Test
    fun `episodeOf returns null for non-series titles`() {
        assertEquals(7, episodeOf(video("Кухня 7 серия")))
        assertNull(episodeOf(video("Обзор iPhone 17")))
    }

    // --- episodesForSeason ---

    @Test
    fun `filters by season and sorts by episode`() {
        val videos = listOf(
            video("Шоу, 2 сезон, 1 серия"),
            video("Шоу, 1 сезон, 2 серия"),
            video("Шоу, 1 сезон, 1 серия"),
        )
        val result = episodesForSeason(videos, season = 1)
        assertEquals(listOf("Шоу, 1 сезон, 1 серия", "Шоу, 1 сезон, 2 серия"), result.map { it.title })
    }

    @Test
    fun `unknown episodes go last`() {
        val videos = listOf(
            video("Шоу финал"),
            video("Шоу 2 серия"),
            video("Шоу 1 серия"),
        )
        val result = episodesForSeason(videos, season = 1)
        assertEquals(listOf("Шоу 1 серия", "Шоу 2 серия", "Шоу финал"), result.map { it.title })
    }

    @Test
    fun `falls back to all videos when the season filter is empty`() {
        val videos = listOf(
            video("Шоу, 1 сезон, 1 серия"),
            video("Шоу, 1 сезон, 2 серия"),
        )
        val result = episodesForSeason(videos, season = 5)
        assertEquals(2, result.size)
    }

    @Test
    fun `null season returns all videos sorted`() {
        val videos = listOf(
            video("Шоу 3 серия"),
            video("Шоу 1 серия"),
        )
        val result = episodesForSeason(videos, season = null)
        assertEquals(listOf("Шоу 1 серия", "Шоу 3 серия"), result.map { it.title })
    }
}
