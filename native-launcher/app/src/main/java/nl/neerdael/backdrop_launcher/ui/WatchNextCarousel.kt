package nl.neerdael.backdrop_launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import nl.neerdael.backdrop_launcher.core.ResumeItem

private class CarouselMotion {
    var state by mutableStateOf(WatchNextMotionState.initial(emptyList(), 0))
    val progress = Animatable(1f)
}
private val carouselEasing = CubicBezierEasing(.215f, .61f, .355f, 1f)

/** A stationary input anchor owns focus while at most three poster layers move around it. */
@Composable
internal fun WatchNextCarousel(items: List<ResumeItem>, selectedIndex: Int, artwork: ComposeArtwork,
    active: Boolean, motionActive: Boolean, revision: Int, focus: FocusRequester,
    accent: Color, showBorder: Boolean, borderWidth: Float, label: String,
    onFocus: (Boolean) -> Unit, onStep: (Int) -> Unit, onPlay: (ResumeItem) -> Unit,
    onUp: () -> Unit, onDown: () -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    val viewportWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val motion = remember { CarouselMotion() }
    val ids = remember(items) {
        val seen = mutableMapOf<String, Int>()
        items.map { item ->
            val id = if (item.id != 0L) "id:${item.id}" else "${item.packageName}:${item.contentId ?: item.title}"
            val occurrence = seen[id] ?: 0
            seen[id] = occurrence + 1
            // Preserve unique legacy identities across reorders; disambiguate malformed duplicates.
            if (occurrence == 0) id else "$id:duplicate:$occurrence"
        }
    }
    val current = motion.state.reconcile(ids, selectedIndex)
    SideEffect { if (motion.state !== current) motion.state = current }
    val currentOnStep by rememberUpdatedState(onStep)
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentItems by rememberUpdatedState(items)
    var focused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val cardShape = remember { RoundedCornerShape(8.dp) }
    val posterSize = with(density) { IntSize(296.dp.roundToPx(), 164.dp.roundToPx()) }

    LaunchedEffect(motion, ids, motionActive) {
        if (!motionActive) {
            motion.progress.snapTo(1f)
            motion.state = motion.state.pause()
            return@LaunchedEffect
        }
        while (isActive) {
            // Await input; this is an idle coroutine, not a polling loop or UI derivation.
            snapshotFlow { motion.state.pending }.first { it }
            while (motion.state.pending) {
                val next = motion.state.begin()
                // Reset before exposing the moving step, so its first frame cannot show t=1.
                motion.progress.snapTo(0f)
                motion.state = next
                currentOnStep(next.target)
                motion.progress.animateTo(1f, tween(WATCH_MOTION_MILLIS, easing = carouselEasing))
                motion.state = motion.state.complete()
            }
        }
    }

    val activeIndex = current.activeIndex.coerceIn(items.indices)
    Box(modifier.fillMaxWidth().height(WATCH_CARD_HEIGHT.dp).clipToBounds()) {
        val residents = current.residentIndices()
        for (slot in residents.indices) {
            val index = residents[slot]
            val item = items[index]
            key(ids[index]) {
                val pose = remember { WatchNextPose() }
                Column(Modifier.size(WATCH_CARD_WIDTH.dp, WATCH_CARD_HEIGHT.dp)
                    .graphicsLayer {
                        // Only Draw observes progress: no per-frame composition or poster remeasure.
                        current.writePose(index, motion.progress.value, viewportWidth, pose)
                        translationX = pose.x.dp.toPx(); translationY = pose.y.dp.toPx()
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = pose.scale; scaleY = pose.scale; alpha = pose.alpha
                        shape = cardShape; clip = true
                    }.testTag("watch-next-card:${item.id}").background(Color(0x55383838))) {
                    Box(Modifier.size(300.dp, 168.dp).drawWithCache {
                        val stroke = Stroke(borderWidth.dp.toPx())
                        val radius = CornerRadius(8.dp.toPx())
                        onDrawWithContent {
                            drawContent()
                            if (focused && index == activeIndex && showBorder)
                                drawRoundRect(accent, cornerRadius = radius, style = stroke)
                        }
                    }) {
                        Artwork(item.image, artwork, active, revision, Modifier.fillMaxSize().padding(2.dp),
                            ContentScale.Crop, decodeSize = posterSize)
                    }
                    Text(item.title, Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 4.dp, vertical = 4.dp),
                        fontSize = 15.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("▶  $label", Modifier.fillMaxWidth().height(23.dp).padding(horizontal = 4.dp, vertical = 3.dp),
                        fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xff333333))) {
                        if (item.progress > 0) Box(Modifier.fillMaxWidth(item.progress.coerceIn(0, 100) / 100f)
                            .fillMaxHeight().background(accent))
                    }
                }
            }
        }
        // One focus target, independent of cards entering/leaving the viewport.
        Box(Modifier.offset(x = 32.dp).size(WATCH_CARD_WIDTH.dp, WATCH_CARD_HEIGHT.dp)
            .focusRequester(focus).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (motionActive) motion.state = motion.state.request(if (it.key == Key.DirectionRight) 1 else -1)
                        true
                    }
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> { onDown(); true }
                    else -> false
                }
            }.combinedClickable(interactionSource = interactions, indication = null, onClick = {
                val index = motion.state.activeIndex.coerceIn(currentItems.indices)
                currentOnPlay(currentItems[index])
            }).semantics { contentDescription = "Continue watching ${items[activeIndex].title}" })
    }
}
