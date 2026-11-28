package ru.rutubeclient.core.presentation

/**
 * Navigation routes shared by both apps (they were byte-identical copies).
 * The video route carries `fromStart` (ignore the saved position) and `next`
 * (autoplay target) as query args.
 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SUBSCRIPTIONS = "subscriptions"
    const val LIBRARY = "library"
    const val CATALOG = "catalog"
    const val TV_SHOW = "tvshow/{tvId}"
    const val VIDEO = "video/{videoId}?fromStart={fromStart}&next={next}"
    const val CHANNEL = "channel/{authorId}?name={name}"

    fun video(id: String, fromStart: Boolean = false, next: String? = null): String {
        val base = "video/$id?fromStart=$fromStart"
        return if (next.isNullOrEmpty()) base else "$base&next=$next"
    }

    fun tvShow(id: Int) = "tvshow/$id"

    fun channel(authorId: Long, name: String) =
        "channel/$authorId?name=${android.net.Uri.encode(name)}"

    /**
     * ViewModel key for the video destination. Without a distinct key per
     * argument combination the nav back stack would silently reuse a stale
     * ViewModel — always construct the key through this function.
     */
    fun videoKey(id: String, fromStart: Boolean, next: String?): String =
        "video-$id-$fromStart-$next"
}
