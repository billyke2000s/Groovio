package com.groovio.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.groovio.app.playback.PlayerUiState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** One deliberate gesture changes one media item. A small drag always springs back. */
object SwipeNavigation {
    const val COMMIT_FRACTION = 0.22f
    const val FLING_PX_PER_SECOND = 1250f

    /** -1 next, 1 previous, 0 snap back. Velocity still requires some travel. */
    fun direction(dragPx: Float, velocityPx: Float, widthPx: Float, hasPrevious: Boolean, hasNext: Boolean): Int {
        if (widthPx <= 0f) return 0
        val travelled = abs(dragPx) >= widthPx * COMMIT_FRACTION ||
            (abs(dragPx) >= widthPx * 0.07f && abs(velocityPx) >= FLING_PX_PER_SECOND &&
                dragPx * velocityPx > 0f)
        return when {
            !travelled -> 0
            dragPx < 0f && hasNext -> -1
            dragPx > 0f && hasPrevious -> 1
            else -> 0
        }
    }
}

/** An edge-to-edge, finger-following three-cover carousel for the full-screen player. */
@Composable
fun SwipeArtwork(
    state: PlayerUiState,
    bitmap: ImageBitmap?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Change the artwork key on an actual Media3 transition, never on title/position updates.
    var drag by remember(state.currentMediaId) { mutableFloatStateOf(0f) }
    var transitioning by remember(state.currentMediaId) { mutableStateOf(false) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(10.dp))) {
        val width = constraints.maxWidth.toFloat()
        val enabled = state.hasItem && width > 0f && !transitioning
        // Keep horizontal gestures only on the artwork; the player can still swipe down
        // and the progress slider retains its own gestures.
        val dragState = rememberDraggableState { delta ->
            if (!transitioning) {
                val min = if (state.hasNext) -width else -width * 0.14f
                val max = if (state.hasPrevious) width else width * 0.14f
                drag = (drag + delta).coerceIn(min, max)
            }
        }
        Box(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Album artwork. Swipe left for next track, right for previous track." }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = { velocity ->
                        val decision = SwipeNavigation.direction(
                            drag, velocity, width, state.hasPrevious, state.hasNext)
                        val target = when (decision) {
                            -1 -> -width
                            1 -> width
                            else -> 0f
                        }
                        val animation = Animatable(drag)
                        if (decision != 0) transitioning = true
                        animation.animateTo(
                            target, tween(durationMillis = if (decision == 0) 220 else 170,
                                easing = FastOutSlowInEasing),
                        ) { drag = value }
                        if (decision < 0) onNext()
                        if (decision > 0) onPrevious()
                        if (decision == 0) drag = 0f
                    },
                ),
        ) {
            // Adjacent covers are already laid out before the drag begins.
            // The player metadata decides which track to show after Media3 reports the transition.
            if (state.hasPrevious) {
                Box(Modifier.fillMaxSize().offset { IntOffset((drag - width).roundToInt(), 0) }) {
                    ArtworkImage(null, state.previousArtworkUri, 120.dp, Modifier.fillMaxSize())
                }
            }
            if (state.hasNext) {
                Box(Modifier.fillMaxSize().offset { IntOffset((drag + width).roundToInt(), 0) }) {
                    ArtworkImage(null, state.nextArtworkUri, 120.dp, Modifier.fillMaxSize())
                }
            }
            Box(Modifier.fillMaxSize().offset { IntOffset(drag.roundToInt(), 0) }) {
                ArtworkImage(bitmap, state.artworkUri, 120.dp, Modifier.fillMaxSize())
            }
        }
    }
    // If Media3 cannot switch (a disconnected controller, for example), don't stick
    // on the incoming artwork forever. A real new media ID clears this state immediately.
    LaunchedEffect(state.currentMediaId, transitioning) {
        if (transitioning) {
            delay(1100)
            if (transitioning) {
                val settle = Animatable(drag)
                settle.animateTo(0f, tween(200)) { drag = value }
                transitioning = false
            }
        }
    }
}
