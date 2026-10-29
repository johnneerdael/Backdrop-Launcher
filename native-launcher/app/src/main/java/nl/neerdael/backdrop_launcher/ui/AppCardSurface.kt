package nl.neerdael.backdrop_launcher.ui

import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

/** Enabled app cards need a translucent fill and focus stroke, without a clipped content layer.
 * Artwork is inset 8dp horizontally/6dp vertically, wholly inside the 12dp rounded corners.
 * Keep this card-specific: arbitrary edge-to-edge child content would need its own clipping.
 */
@Composable
internal fun AppCardSurface(
    onClick: () -> Unit, modifier: Modifier = Modifier, color: Color,
    onLongClick: (() -> Unit)? = null, borderColor: Color = Color.White,
    borderWidth: Dp = 1.5.dp, showBorder: Boolean = true, animateBorder: Boolean = false,
    active: Boolean = true, content: @Composable BoxScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val focusEasing = remember {
        val interpolator = AccelerateDecelerateInterpolator()
        Easing { interpolator.getInterpolation(it) }
    }
    val scale = animateFloatAsState(
        targetValue = if (focused && !pressed) 1.06f else 1f,
        // Match the Views control focus transition instead of TV Material's 300/500ms scale.
        animationSpec = tween(120, easing = focusEasing),
        label = "app-card-scale",
    )
    val borderAlpha = if (active && animateBorder && focused && !pressed) {
        rememberInfiniteTransition(label = "focus-border").animateFloat(.65f, 1f,
            infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "focus-border-alpha")
    } else null
    Box(
        modifier = modifier
            // A transform-only RenderNode does not request an offscreen composition buffer.
            // Keep alpha at 1 and clipping off; apply tile opacity once to the fill below.
            .graphicsLayer {
                val currentScale = scale.value
                scaleX = currentScale
                scaleY = currentScale
            }
            .zIndex(if (focused) .5f else 0f)
            .drawWithCache {
                val radius = CornerRadius(12.dp.toPx())
                val stroke = Stroke(width = borderWidth.toPx(), cap = StrokeCap.Round)
                onDrawWithContent {
                    drawContent()
                    if (showBorder && (focused || pressed)) drawRoundRect(borderColor, cornerRadius = radius, style = stroke, alpha = borderAlpha?.value ?: 1f)
                }
            }
            .background(color, RoundedCornerShape(12.dp))
            // Foundation supplies D-pad/Enter release activation, focus and merged click semantics.
            // No ripple, extra clip or group alpha is needed for these always-enabled cards.
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick, onLongClick = onLongClick),
        propagateMinConstraints = true,
        content = content,
    )
}
