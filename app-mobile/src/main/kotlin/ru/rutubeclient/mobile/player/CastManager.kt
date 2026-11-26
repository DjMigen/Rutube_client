package ru.rutubeclient.mobile.player

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.rutubeclient.core.model.VideoDetails

/**
 * Thin wrapper over the Cast SDK. Everything is guarded: on devices without
 * Google Play services (or on TV) the manager simply reports unavailable.
 */
class CastManager(private val context: Context) {

    private var castContext: CastContext? = null

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _isCasting.value = true
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _isCasting.value = true
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            _isCasting.value = false
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    /** Call once from the activity; no-op when Play services are missing. */
    fun initialize() {
        if (castContext != null) return
        runCatching {
            val ctx = CastContext.getSharedInstance(context)
            castContext = ctx
            ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        }
    }

    val isAvailable: Boolean
        get() = castContext != null

    /** Sends the video to the current cast session. Returns false when not connected. */
    fun loadVideo(details: VideoDetails, hlsUrl: String, positionMs: Long): Boolean {
        val session = runCatching {
            castContext?.sessionManager?.currentCastSession
        }.getOrNull() ?: return false
        val client = session.remoteMediaClient ?: return false

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, details.title)
            putString(MediaMetadata.KEY_SUBTITLE, details.author?.name.orEmpty())
            details.thumbnailUrl?.let { url ->
                runCatching { addImage(WebImage(Uri.parse(url))) }
            }
        }
        val mediaInfo = MediaInfo.Builder(hlsUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("application/x-mpegURL")
            .setMetadata(metadata)
            .build()
        return runCatching {
            client.load(
                MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setCurrentTime(positionMs)
                    .setAutoplay(true)
                    .build()
            )
            true
        }.getOrDefault(false)
    }
}
