package ru.rutubeclient.mobile.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.rutubeclient.core.player.PlaybackService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Connects a [MediaController] to the shared [PlaybackService].
 * The returned controller proxies the session's player: playback keeps
 * running in background / PiP and shows the system media notification.
 */
suspend fun Context.connectMediaController(): MediaController =
    suspendCancellableCoroutine { cont ->
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { cont.resume(it) }
                    .onFailure { cont.resumeWithException(it) }
            },
            MoreExecutors.directExecutor(),
        )
        cont.invokeOnCancellation { future.cancel(true) }
    }
