package ru.rutubeclient.core.network

import ru.rutubeclient.core.model.CatalogEntry
import ru.rutubeclient.core.model.CatalogTab
import ru.rutubeclient.core.model.Page
import ru.rutubeclient.core.model.TvShow
import ru.rutubeclient.core.model.Video

/**
 * Concentrates catalog page loading + tab dispatch that used to be duplicated
 * between the mobile and TV catalog screens.
 */
class CatalogLoader(private val api: RuTubeApi) {

    suspend fun load(tab: CatalogTab, page: Int): Page<CatalogEntry> = when (tab) {
        CatalogTab.ALL -> api.getTvShows(page).toCatalogEntries { true }
        CatalogTab.SERIES -> api.getTvShows(page).toCatalogEntries { it.type == "series" }
        CatalogTab.TV_SHOWS -> api.getTvShows(page).toCatalogEntries { it.type == "tvshow" }
        CatalogTab.MOVIES -> api.getTopMovies(page).toVideoCatalogEntries()
        CatalogTab.CARTOONS -> api.getTopCartoons(page).toVideoCatalogEntries()
        CatalogTab.ANIME -> api.getTopAnime(page).toVideoCatalogEntries()
    }

    private fun Page<TvShow>.toCatalogEntries(filter: (TvShow) -> Boolean): Page<CatalogEntry> =
        Page(
            items = items.filter(filter).map(CatalogEntry::Show),
            hasNext = hasNext,
            page = page,
        )

    private fun Page<Video>.toVideoCatalogEntries(): Page<CatalogEntry> =
        Page(
            items = items.map(CatalogEntry::Video),
            hasNext = hasNext,
            page = page,
        )
}
