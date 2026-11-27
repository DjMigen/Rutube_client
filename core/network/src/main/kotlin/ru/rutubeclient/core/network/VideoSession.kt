package ru.rutubeclient.core.network

import ru.rutubeclient.core.model.VideoDetails

/**
 * Concentrates the per-video loading flow that used to be duplicated between
 * the mobile and TV video screens: fetching details, resolving the signed stream
 * URL and persisting the playback position.
 */
class VideoSession(private val api: RuTubeApi) {

    data class LoadedVideo(
        val details: VideoDetails,
        val streamUrl: String,
    )

    suspend fun load(videoId: String): LoadedVideo {
        val details = api.getVideo(videoId)
        val streamUrl = api.getStreamInfo(videoId).hlsUrl
        return LoadedVideo(details, streamUrl)
    }

    suspend fun resolveStream(videoId: String): String =
        api.getStreamInfo(videoId).hlsUrl

    suspend fun savePosition(store: PlaybackPositionStore?, details: VideoDetails, positionMs: Long) {
        if (positionMs <= 0L) return
        store?.savePosition(details, positionMs)
    }
}
