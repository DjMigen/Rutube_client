package ru.rutubeclient.core.player

import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests go through the session's public interface only; the player is a
 * relaxed mock and the ports are lambdas.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {

    private val player: Player = mockk(relaxed = true)
    private val listenerSlot = slot<Player.Listener>()

    private fun TestScope.newSession(resolver: StreamResolver): PlaybackSession {
        every { player.addListener(capture(listenerSlot)) } just Runs
        return PlaybackSession(
            player = player,
            streamResolver = resolver,
            // Unconfined: session coroutines run eagerly, no runCurrent() dance.
            // Parented to backgroundScope: the infinite save loop is cancelled
            // at test end instead of stalling runTest's advance-until-idle.
            scope = CoroutineScope(
                SupervisorJob(backgroundScope.coroutineContext.job) +
                    UnconfinedTestDispatcher(testScheduler),
            ),
        )
    }

    @Test
    fun `open resolves stream and starts playback`() = runTest {
        val session = newSession { "https://cdn/video.m3u8" }

        session.open("v1", resumeFromMs = 5000)

        val state = session.state.value
        assertEquals(PlaybackStatus.READY, state.status)
        assertEquals("https://cdn/video.m3u8", state.streamUrl)
        verify { player.setMediaItem(any(), 5000L) }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `open is idempotent for the same video`() = runTest {
        var resolves = 0
        val session = newSession { resolves++; "url" }

        session.open("v1")
        session.open("v1")

        assertEquals(1, resolves)
    }

    @Test
    fun `failed resolve sets error state and retry recovers`() = runTest {
        var attempts = 0
        val session = newSession {
            attempts++
            if (attempts == 1) throw RuntimeException("network down") else "url"
        }

        session.events.test {
            session.open("v1")

            assertEquals(PlaybackStatus.ERROR, session.state.value.status)
            assertEquals("network down", session.state.value.errorMessage)
            assertEquals(PlaybackEvent.FatalError("network down"), awaitItem())

            session.retry()

            assertEquals(PlaybackStatus.READY, session.state.value.status)
            assertEquals("url", session.state.value.streamUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `player error re-resolves once at current position then fails fatally`() = runTest {
        var resolves = 0
        every { player.currentPosition } returns 7000L
        val session = newSession { resolves++; "url-$resolves" }
        session.open("v1")

        session.events.test {
            // First error: expired signature assumed — re-resolve and re-attach in place.
            listenerSlot.captured.onPlayerError(mockk())
            assertEquals(2, resolves)
            verify { player.setMediaItem(any(), 7000L) }
            assertEquals(PlaybackStatus.READY, session.state.value.status)

            // Second error: give up.
            listenerSlot.captured.onPlayerError(mockk())
            assertEquals(2, resolves)
            assertEquals(PlaybackStatus.ERROR, session.state.value.status)
            assertTrue(awaitItem() is PlaybackEvent.FatalError)
        }
    }

    @Test
    fun `ended starts autoplay countdown and navigates`() = runTest {
        val session = newSession { "url" }
        session.setUpNext("next-1")
        session.open("v1")

        session.events.test {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            assertEquals(5, session.state.value.autoplayCountdownSec)

            advanceTimeBy(2000)
            runCurrent()
            assertEquals(3, session.state.value.autoplayCountdownSec)

            advanceTimeBy(3000)
            runCurrent()
            assertEquals(PlaybackEvent.NavigateToNext("next-1"), awaitItem())
            assertNull(session.state.value.autoplayCountdownSec)
        }
    }

    @Test
    fun `cancelAutoplay stops the countdown`() = runTest {
        val session = newSession { "url" }
        session.setUpNext("next-1")
        session.open("v1")

        session.events.test {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceTimeBy(2000)
            session.cancelAutoplay()
            advanceTimeBy(10_000)
            runCurrent()

            assertNull(session.state.value.autoplayCountdownSec)
            expectNoEvents()
        }
    }

    @Test
    fun `position is saved every five seconds and once more on close`() = runTest {
        every { player.duration } returns 60_000L
        every { player.currentPosition } returns 42_000L
        val saved = mutableListOf<Long>()
        val session = newSession { "url" }

        session.open("v1", onSavePosition = { saved.add(it) })
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(listOf(42_000L), saved)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(listOf(42_000L, 42_000L), saved)

        session.close()
        assertEquals(listOf(42_000L, 42_000L, 42_000L), saved)
        verify { player.release() }
    }

    @Test
    fun `sleep timer pauses playback when it elapses`() = runTest {
        val session = newSession { "url" }
        session.open("v1")

        session.setSleepTimer(15)
        assertEquals(15, session.state.value.sleepTimerMinutes)

        advanceTimeBy(15 * 60_000L)
        runCurrent()
        verify { player.pause() }
        assertNull(session.state.value.sleepTimerMinutes)
    }

    @Test
    fun `still watching prompt appears after three hours and confirm resets it`() = runTest {
        every { player.isPlaying } returns true
        val session = newSession { "url" }
        session.open("v1")

        listenerSlot.captured.onIsPlayingChanged(true)
        advanceTimeBy(3 * 60 * 60 * 1000L)
        runCurrent()
        assertTrue(session.state.value.stillWatchingDue)

        session.confirmStillWatching()
        assertFalse(session.state.value.stillWatchingDue)

        advanceTimeBy(3 * 60 * 60 * 1000L)
        runCurrent()
        assertTrue(session.state.value.stillWatchingDue)
    }
}
