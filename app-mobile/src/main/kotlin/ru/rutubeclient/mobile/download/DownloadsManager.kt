package ru.rutubeclient.mobile.download

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import ru.rutubeclient.core.database.DownloadEntity
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.model.VideoDetails
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.core.player.PlayerFactory
import ru.rutubeclient.core.player.OfflineContentProvider
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * Owns the Media3 [DownloadManager], mirrors its state into Room for the UI,
 * and tells the player which videos are available offline.
 *
 * Downloads are made from a single HLS variant's media-playlist URL (not the
 * master playlist), so the requested quality is respected and downloads stay
 * small — both of which reduce the chance a short-lived signed URL expires
 * mid-download.
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

    /** Videos already retried once this process run, to avoid retry loops. */
    private val retriedIds = CopyOnWriteArraySet<String>()

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
     * (they expire quickly), then the variant closest to [qualityHeight] is
     * selected and downloaded directly. [qualityHeight] 0 means best available.
     */
    suspend fun enqueue(video: VideoDetails, qualityHeight: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = api.getStreamInfo(video.id)
                val (url, height) = resolveDownloadVariant(stream.hlsUrl, qualityHeight)
                repository.upsertDownload(
                    DownloadEntity(
                        videoId = video.id,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        durationSec = video.durationSec,
                        authorName = video.author?.name,
                        hlsUrl = url,
                        qualityHeight = height,
                        state = DownloadEntity.STATE_QUEUED,
                        bytesDownloaded = 0,
                        bytesTotal = 0,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                DownloadService.sendAddDownload(
                    context,
                    AppDownloadService::class.java,
                    downloadRequest(video.id, url),
                    /* forceSchedule = */ true,
                )
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

    // --- HLS variant resolution ---

    private data class HlsVariant(val height: Int, val url: String)

    /** Resolves the download URL + height for the requested quality, falling
     *  back to the master URL if the playlist can't be fetched/parsed. */
    private suspend fun resolveDownloadVariant(
        masterUrl: String,
        qualityHeight: Int,
    ): Pair<String, Int> = runCatching {
        val variants = fetchAndParseVariants(masterUrl)
        val selected = selectVariant(variants, qualityHeight)
        if (selected != null) selected.url to selected.height else masterUrl to qualityHeight
    }.getOrDefault(masterUrl to qualityHeight)

    private fun fetchAndParseVariants(masterUrl: String): List<HlsVariant> {
        val client = PlayerFactory.newHttpClient()
        val request = Request.Builder().url(masterUrl).build()
        val master = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        return parseVariants(master)
    }

    /** Extracts (height, url) pairs from an HLS master playlist. */
    private fun parseVariants(master: String): List<HlsVariant> {
        val resolution = Regex("""RESOLUTION=(\d+)x(\d+)""")
        val variants = mutableListOf<HlsVariant>()
        var pendingHeight = 0
        for (line in master.lineSequence()) {
            when {
                line.startsWith("#EXT-X-STREAM-INF") ->
                    pendingHeight = resolution.find(line)?.groupValues?.get(2)?.toIntOrNull() ?: 0

                line.startsWith("#") -> Unit

                line.isNotBlank() -> {
                    if (pendingHeight > 0) variants.add(HlsVariant(pendingHeight, line.trim()))
                    pendingHeight = 0
                }
            }
        }
        // Each rendition is listed twice (CDN mirrors); keep one URL per height.
        return variants.distinctBy { it.height }
    }

    private fun selectVariant(variants: List<HlsVariant>, qualityHeight: Int): HlsVariant? {
        if (variants.isEmpty()) return null
        if (qualityHeight <= 0) return variants.maxByOrNull { it.height }
        return variants.firstOrNull { it.height == qualityHeight }
            ?: variants.filter { it.height <= qualityHeight }.maxByOrNull { it.height }
            ?: variants.minByOrNull { it.height }
    }

    private fun downloadRequest(id: String, url: String): DownloadRequest =
        DownloadRequest.Builder(id, Uri.parse(url))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

    // --- State mirroring + retry ---

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
        if (error != null || download.state == Download.STATE_FAILED) {
            if (retriedIds.add(download.request.id)) {
                // A short-lived signed URL likely expired mid-download — re-resolve
                // and re-enqueue once.
                retryDownload(existing)
            } else {
                repository.upsertDownload(existing.copy(state = DownloadEntity.STATE_FAILED))
            }
            return
        }
        val state = when {
            download.state == Download.STATE_COMPLETED -> DownloadEntity.STATE_COMPLETED
            download.state == Download.STATE_DOWNLOADING -> DownloadEntity.STATE_DOWNLOADING
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

    private suspend fun retryDownload(existing: DownloadEntity) {
        val stream = runCatching { api.getStreamInfo(existing.videoId) }.getOrNull()
        if (stream == null) {
            repository.upsertDownload(existing.copy(state = DownloadEntity.STATE_FAILED))
            return
        }
        val (url, height) = resolveDownloadVariant(stream.hlsUrl, existing.qualityHeight)
        DownloadService.sendRemoveDownload(
            context,
            AppDownloadService::class.java,
            existing.videoId,
            /* forceSchedule = */ false,
        )
        repository.upsertDownload(
            existing.copy(
                hlsUrl = url,
                qualityHeight = height,
                state = DownloadEntity.STATE_QUEUED,
                bytesDownloaded = 0,
                bytesTotal = 0,
            )
        )
        DownloadService.sendAddDownload(
            context,
            AppDownloadService::class.java,
            downloadRequest(existing.videoId, url),
            /* forceSchedule = */ true,
        )
    }
}
