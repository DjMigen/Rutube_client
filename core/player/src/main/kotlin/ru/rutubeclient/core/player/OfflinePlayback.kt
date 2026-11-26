package ru.rutubeclient.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.File

/**
 * Implemented by the app to tell the player which videos are fully
 * downloaded and where their segments live.
 */
interface OfflineContentProvider {
    /** True when [mediaId] (RuTube video id) is downloaded and playable offline. */
    fun isDownloaded(mediaId: String): Boolean

    /** The cache holding downloaded content (owned by the download manager). */
    fun downloadsCache(context: Context): SimpleCache
}

/**
 * Shared holder configured by the app at startup. [PlaybackService] is created
 * by the system, so it cannot receive constructor parameters — it reads the
 * provider from here instead.
 */
object PlaybackEnvironment {
    @Volatile
    var offlineProvider: OfflineContentProvider? = null
}

/**
 * Routes playback: downloaded videos are served from the downloads cache
 * (works fully offline), everything else streams through the usual path.
 */
@OptIn(UnstableApi::class)
class RoutingMediaSourceFactory(
    private val context: Context,
    private val provider: OfflineContentProvider,
    streamingUpstream: DataSource.Factory,
) : MediaSource.Factory {

    private val streamingFactory = DefaultMediaSourceFactory(streamingUpstream)

    private val offlineFactory by lazy {
        val upstream = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(PlayerFactory.newHttpClient()),
        )
        DefaultMediaSourceFactory(
            CacheDataSource.Factory()
                .setCache(provider.downloadsCache(context))
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        )
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        if (provider.isDownloaded(mediaItem.mediaId)) {
            offlineFactory.createMediaSource(mediaItem)
        } else {
            streamingFactory.createMediaSource(mediaItem)
        }

    override fun getSupportedTypes(): IntArray = streamingFactory.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = this

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = this
}

/** Builds the shared downloads cache. The download manager and the player use it. */
@OptIn(UnstableApi::class)
fun downloadsCache(context: Context, dirName: String = "downloads"): SimpleCache =
    SimpleCache(
        File(context.filesDir, dirName),
        LeastRecentlyUsedCacheEvictor(8L * 1024 * 1024 * 1024), // 8 GB cap
        StandaloneDatabaseProvider(context),
    )
