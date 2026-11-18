package com.groovio.app.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Lightweight, reusable Groovio press feedback. Uses the click target's real
 * InteractionSource, so a drag/scroll cancellation also cancels the pressed state.
 * Only the rendering layer scales: layout, semantics and click bounds stay put.
 */
fun Modifier.groovioClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "Groovio press",
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactions,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.groovioCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "Groovio long press",
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interactions,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}
