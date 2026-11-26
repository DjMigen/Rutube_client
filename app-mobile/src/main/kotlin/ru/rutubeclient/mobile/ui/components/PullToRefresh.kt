package ru.rutubeclient.mobile.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Configuration for the pull-to-refresh component. */
data class PullToRefreshConfig(
    /** Distance (in dp) to pull before the refresh is triggered. */
    val triggerDistanceDp: Float = 80f,
    /** Maximum distance (in dp) the content can be pulled down. */
    val maxPullDistanceDp: Float = 160f,
    /** Resistance factor applied while pulling (0 = no resistance, 1 = full resistance). */
    val resistance: Float = 0.5f,
    /** Indicator color. Color.Unspecified means theme primary. */
    val indicatorColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
)

/** State of the pull-to-refresh gesture. */
class PullToRefreshState(
    config: PullToRefreshConfig = PullToRefreshConfig(),
) {
    val config = config
    /** Current pull offset in dp (0 when nothing is being pulled). */
    val pullOffset = mutableFloatStateOf(0f)
    /** Whether a refresh is currently in progress. */
    var isRefreshing by mutableStateOf(false)
        internal set

    /** Call this from the UI when refresh completes (loading ends). */
    fun completeRefresh() {
        isRefreshing = false
        pullOffset.value = 0f
    }

    /** Call this from the UI when refresh starts. */
    fun startRefresh() {
        isRefreshing = true
    }
}

/**
 * Modifier that adds pull-to-refresh gesture detection to a scrollable host.
 *
 * Pulling down is consumed only when the host cannot scroll backward anymore (i.e. it's at
 * the top). The consumed pull amount is exposed via [state.pullOffset].
 *
 * @param canScrollBackward supplier that returns true when the scrollable can scroll toward index 0
 * @param state shared state object holding pull offset and refreshing flag
 * @param onRefresh callback invoked when the user pulls past the trigger distance and releases
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pullToRefresh(
    canScrollBackward: () -> Boolean,
    state: PullToRefreshState,
    onRefresh: () -> Unit,
): Modifier = this
    .nestedScroll(
        connection = object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Pull down (positive y in Compose coords) only when at the top,
                // or we're already in the middle of a pull gesture, and not refreshing.
                if (
                    available.y > 0 &&
                    !state.isRefreshing &&
                    (!canScrollBackward() || state.pullOffset.value > 0f)
                ) {
                    state.pullOffset.value = (state.pullOffset.value + available.y * state.config.resistance)
                        .coerceIn(0f, state.config.maxPullDistanceDp)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

        },
    )
    .pointerInput(Unit) {
        // Detect finger lift for slow releases (no fling).
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val released = event.changes.any { it.pressed == false }
                if (released && !state.isRefreshing) {
                    completePull(state, onRefresh)
                }
            }
        }
    }

private fun completePull(state: PullToRefreshState, onRefresh: () -> Unit) {
    if (state.pullOffset.value >= state.config.triggerDistanceDp) {
        state.isRefreshing = true
        onRefresh()
    }
}

/**
 * Pull-to-refresh indicator composable.
 *
 * Places a centered `CircularProgressIndicator` inside a `Surface` that sizes itself to
 * [pullOffsetDp]. When `pullOffsetDp == 0` it measures 0 height and has no `clickable` /
 * `pointerInput`, so touch events pass through to underlying content (e.g. video cards)
 * without interference.
 */
@Composable
fun PullToRefreshIndicator(
    pullOffsetDp: Float,
    config: PullToRefreshConfig = PullToRefreshConfig(),
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(pullOffsetDp.dp)
            .padding(top = pullOffsetDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (pullOffsetDp > 0f) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 8.dp),
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = if (config.indicatorColor == androidx.compose.ui.graphics.Color.Unspecified) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            config.indicatorColor
                        },
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/** Default pull-to-refresh configuration exposed for easy customization. */
val DefaultPullToRefreshConfig = PullToRefreshConfig()
