package ru.rutubeclient.core.network

import ru.rutubeclient.core.model.VideoDetails

/**
 * Port for persisting playback positions. Production uses LibraryRepository;
 * tests can supply an in-memory store.
 */
interface PlaybackPositionStore {
    suspend fun savePosition(details: VideoDetails, positionMs: Long)
}
