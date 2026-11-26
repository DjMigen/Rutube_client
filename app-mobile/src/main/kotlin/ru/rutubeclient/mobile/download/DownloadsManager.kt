package ru.rutubeclient.mobile.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.rutubeclient.core.database.DownloadEntity
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.VideoDetails
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.core.player.PlayerFactory
import ru.rutubeclient.core.player.OfflineContentProvider
import ru.rutubeclient.core.player.downloadsCache
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the Media3 [DownloadManager], mirrors its state into Room for the UI,
 * and tells the player which videos are available offline.
 */
@OptIn(UnstableApi::class)
class DownloadsManager(
    private val context: Context,
    private val api: RuTubeApi,
    private val repository: LibraryRepository,
) : OfflineContentProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val cache: SimpleCache by lazy { ru.rutubeclient.core.player.downloadsCache(context) }

    val downloadManager: DownloadManager by lazy {
        val upstream = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(PlayerFactory.newHttpClient()),
        )
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        DownloadManager(
            context,
            StandaloneDatabaseProvider(context),
            cache,
            cacheFactory,
            Executors.newSingleThreadExecutor(),
        ).apply {
            setMaxParallelDownloads(2)
            addListener(SyncListener())
        }
    }

    private val completedIds = CopyOnWriteArraySet<String>()

    init {
        // Keep the offline set in sync with Room.
        scope.launch {
            repository.downloads.collect { list ->
                completedIds.clear()
                list.filter { it.state == DownloadEntity.STATE_COMPLETED }
                    .forEach { completedIds.add(it.videoId) }
            }
        }
        // Build the manager off the main thread.
        scope.launch { downloadManager }
    }

    override fun isDownloaded(mediaId: String): Boolean = completedIds.contains(mediaId)

    override fun downloadsCache(context: Context): SimpleCache = cache

    /**
     * Enqueues a video for download. A fresh signed HLS URL is resolved first
     * (they expire quickly), then exactly one rendition is selected so we
     * don't download every quality.
     */
    suspend fun enqueue(video: VideoDetails, qualityHeight: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = api.getStreamInfo(video.id)
                val helper = DownloadHelper.forMediaItem(
                    context,
                    PlayerFactory.mediaItem(stream.hlsUrl, video.id),
                    DefaultDataSource.Factory(
                        context,
                        OkHttpDataSource.Factory(PlayerFactory.newHttpClient()),
                    ),
                )
                try {
                    suspendCancellableCoroutine { cont ->
                        helper.prepare(object : DownloadHelper.Callback {
                            override fun onPrepared(h: DownloadHelper, contentLengthKnown: Boolean) =
                                cont.resume(Unit)

                            override fun onPrepareError(h: DownloadHelper, e: IOException) =
                                cont.resumeWithException(e)
                        })
                    }
                    val targetHeight =
                        if (qualityHeight > 0) qualityHeight else maxVideoHeight(helper)
                    val params = TrackSelectionParameters.Builder()
                        .setMinVideoSize(0, targetHeight)
                        .setMaxVideoSize(Int.MAX_VALUE, targetHeight)
                        .build()
                    for (p in 0 until helper.periodCount) {
                        helper.addTrackSelection(p, params)
                    }
                    val request = helper.getDownloadRequest(video.id, null)
                    repository.upsertDownload(
                        DownloadEntity(
                            videoId = video.id,
                            title = video.title,
                            thumbnailUrl = video.thumbnailUrl,
                            durationSec = video.durationSec,
                            authorName = video.author?.name,
                            hlsUrl = stream.hlsUrl,
                            qualityHeight = targetHeight,
                            state = DownloadEntity.STATE_QUEUED,
                            bytesDownloaded = 0,
                            bytesTotal = 0,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    DownloadService.sendAddDownload(
                        context,
                        AppDownloadService::class.java,
                        request,
                        /* forceSchedule = */ true,
                    )
                } finally {
                    helper.release()
                }
            }
        }

    fun remove(videoId: String) {
        DownloadService.sendRemoveDownload(
            context,
            AppDownloadService::class.java,
            videoId,
            /* forceSchedule = */ true,
        )
        scope.launch { repository.removeDownload(videoId) }
    }

    fun removeAll() {
        DownloadService.sendRemoveAllDownloads(
            context,
            AppDownloadService::class.java,
            /* forceSchedule = */ true,
        )
        scope.launch {
            repository.downloads.first().forEach { repository.removeDownload(it.videoId) }
        }
    }

    private fun maxVideoHeight(helper: DownloadHelper): Int {
        var max = 0
        for (p in 0 until helper.periodCount) {
            val groups = helper.getTrackGroups(p)
            for (i in 0 until groups.length) {
                val group = groups[i]
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (j in 0 until group.length) {
                        val h = group.getFormat(j).height
                        if (h > max) max = h
                    }
                }
            }
        }
        return if (max > 0) max else 1080
    }

    private inner class SyncListener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            scope.launch { syncToRoom(download, finalException) }
        }
    }

    private suspend fun syncToRoom(download: Download, error: Exception?) {
        val existing = repository.getDownload(download.request.id) ?: return
        val state = when {
            error != null || download.state == Download.STATE_FAILED ->
                DownloadEntity.STATE_FAILED

            download.state == Download.STATE_COMPLETED ->
                DownloadEntity.STATE_COMPLETED

            download.state == Download.STATE_DOWNLOADING ->
                DownloadEntity.STATE_DOWNLOADING

            else -> DownloadEntity.STATE_QUEUED
        }
        repository.upsertDownload(
            existing.copy(
                state = state,
                bytesDownloaded = download.bytesDownloaded,
                bytesTotal = download.contentLength,
            )
        )
    }
}
