package ru.rutubeclient.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Builds ExoPlayer instances configured for RuTube HLS streams.
 *
 * All HTTP traffic (API + CDN) carries the same mobile User-Agent; the CDN
 * signs URLs per session and may reject requests without it.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    private const val CACHE_DIR = "media_cache"
    private const val CACHE_MAX_BYTES = 512L * 1024 * 1024 // 512 MB

    @Volatile
    private var cache: SimpleCache? = null

    /** HTTP client with the mobile User-Agent; also used by the download manager. */
    fun newHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
            .build()

    /** Shared disk cache so replays and quality switches don't refetch segments. */
    @Synchronized
    private fun cache(context: Context): SimpleCache =
        cache ?: SimpleCache(
            File(context.cacheDir, CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }

    /** Streaming path: shared LRU cache over OkHttp. */
    fun streamingDataSourceFactory(context: Context) =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(newHttpClient()))
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Pre-initializes the shared cache + HTTP client off the main thread. The
     * first player (the hero preview) otherwise opens the cache's SQLite DB on
     * the UI thread, causing a startup hitch. Call from Application.onCreate.
     */
    fun warmUp(context: Context) {
        streamingDataSourceFactory(context)
    }

    /**
     * Creates a fully configured player. When an [OfflineContentProvider] is
     * given, downloaded videos play from the downloads cache and everything
     * else streams; without one the player is streaming-only.
     */
    fun create(context: Context, provider: OfflineContentProvider? = null): ExoPlayer {
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = DefaultTrackSelector.Parameters.Builder(context)
                .setExceedVideoConstraintsIfNecessary(true)
                .build()
        }
        val mediaSourceFactory = provider
            ?.let { RoutingMediaSourceFactory(context, it, streamingDataSourceFactory(context)) }
            ?: DefaultMediaSourceFactory(streamingDataSourceFactory(context))
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(context))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                    )
                    .build()
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true) // pause on headphone unplug
            .build()
    }

    /** HLS media item. [mediaId] is the RuTube video id (used for offline routing). */
    fun mediaItem(hlsUrl: String, mediaId: String = hlsUrl): MediaItem =
        MediaItem.Builder()
            .setUri(hlsUrl)
            .setMediaId(mediaId)
            .setMimeType("application/x-mpegURL")
            .build()
}

/** One quality choice shown in the UI. [height] 0 means adaptive ("Авто"). */
data class QualityOption(val label: String, val height: Int)

/**
 * Lists available video qualities and applies the user's choice.
 * Works with any [Player] — including a MediaController connected to the
 * playback service, since track selection parameters are proxied to the
 * session's player.
 */
@OptIn(UnstableApi::class)
class QualityController(private val player: Player) {

    /** "Авто" plus every rendition height found in the current HLS master playlist. */
    fun availableQualities(): List<QualityOption> {
        val heights = sortedSetOf<Int>()
        for (group in player.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val h = group.getTrackFormat(i).height
                    if (h > 0) heights.add(h)
                }
            }
        }
        val auto = QualityOption("Авто", 0)
        return listOf(auto) + heights.toList().sortedDescending().map { QualityOption("${it}p", it) }
    }

    /** Applies a quality choice; height 0 resets to adaptive. */
    fun select(height: Int) {
        val params = player.trackSelectionParameters.buildUpon()
        if (height <= 0) {
            params.clearVideoSizeConstraints()
        } else {
            params.setMaxVideoSize(Int.MAX_VALUE, height)
        }
        player.setTrackSelectionParameters(params.build())
    }

    /** Available text track languages (subtitles/captions). */
    fun availableTextLanguages(): List<String> = player.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .flatMap { group ->
            (0 until group.length).mapNotNull { group.getTrackFormat(it).language }
        }
        .distinct()

    /** Available audio track languages. */
    fun availableAudioLanguages(): List<String> = player.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .flatMap { group ->
            (0 until group.length).mapNotNull { group.getTrackFormat(it).language }
        }
        .distinct()

    fun selectTextLanguage(language: String?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (language == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            params.setPreferredTextLanguage(language)
        }
        player.setTrackSelectionParameters(params.build())
    }

    fun selectAudioLanguage(language: String?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (language != null) {
            params.setPreferredAudioLanguage(language)
        }
        player.setTrackSelectionParameters(params.build())
    }
}
