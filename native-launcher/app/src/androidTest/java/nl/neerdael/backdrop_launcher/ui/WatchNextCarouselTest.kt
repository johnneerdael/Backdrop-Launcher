package nl.neerdael.backdrop_launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import nl.neerdael.backdrop_launcher.core.ResumeItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchNextCarouselTest {
    @get:Rule val compose = createComposeRule()
    private val initial = (1L..4L).map { ResumeItem("Episode $it", "", "backdrops/fight-club-55e2393686745.jpg", 35, null, "fixture", it) }
    private var items by mutableStateOf(initial)
    private var selected by mutableIntStateOf(0)
    private var running by mutableStateOf(true)
    private val selectedSteps = mutableListOf<Int>()
    private val played = mutableListOf<Long>()

    private fun content() {
        compose.setContent {
            val context = LocalContext.current
            val artwork = remember(context) { ComposeArtwork(context) }
            val focus = remember { FocusRequester() }
            DisposableEffect(artwork) { artwork.setActive(true); onDispose { artwork.close() } }
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    WatchNextCarousel(items, selected, artwork, true, running, 0, focus, Color.White, true, 1.5f, "Continue Watching",
                        {}, { selected = it; selectedSteps.add(it) }, { played.add(it.id) }, {}, {})
                }
            }
            LaunchedEffect(focus) { withFrameNanos { }; focus.requestFocus() }
        }
        compose.onNodeWithContentDescription("Continue watching Episode 1").assertIsFocused()
    }
    private fun anchor() = compose.onNode(hasContentDescription("Continue watching", substring = true))
    private fun card(id: Long) = compose.onNodeWithTag("watch-next-card:$id")
    private fun navigate(key: Key) { anchor().performKeyInput { pressKey(key) } }

    @Test fun rightSlidesAndGrowsTheExistingPreviewAcrossIntermediateFrames() {
        content()
        val before = card(2).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        navigate(Key.DirectionRight)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val during = card(2).fetchSemanticsNode().boundsInRoot
        assertTrue("Incoming card must grow, before=$before during=$during", during.width > before.width + 10)
        assertTrue("Incoming card must remain intermediate", during.width < before.width * 3 - 1)
        assertTrue("Incoming card must move left", during.left < before.left - 10)
        compose.mainClock.advanceTimeBy(400)
        val after = card(2).fetchSemanticsNode().boundsInRoot
        assertEquals(before.width * 3, after.width, 2f)
        assertTrue(after.left < during.left)
        card(1).assertDoesNotExist()
        assertEquals(1, selected)
        anchor().assertIsFocused()
        val cards = compose.onAllNodes(SemanticsMatcher("carousel card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("watch-next-card:") == true
        }).fetchSemanticsNodes()
        assertTrue(cards.size <= 3)
    }
    @Test fun rapidReversalQueuesAndSelectDuringMotionUsesTheIncomingItem() {
        content(); compose.mainClock.autoAdvance = false
        navigate(Key.DirectionRight); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(70)
        navigate(Key.DirectionRight); navigate(Key.DirectionLeft)
        anchor().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(2L), played) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, selected); assertEquals(listOf(1), selectedSteps) }
        anchor().assertIsFocused()
    }
    @Test fun replacementDuringMotionKeepsIncomingIdentityAndRemainsNavigable() {
        content(); compose.mainClock.autoAdvance = false
        navigate(Key.DirectionRight); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(70)
        compose.runOnIdle { items = listOf(initial[1], initial[3], initial[0]) }
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(500)
        anchor().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(2L), played) }
        anchor().assertIsFocused()
    }
    @Test fun hidingCommitsTheCurrentStepAndDiscardsQueuedMotion() {
        content(); compose.mainClock.autoAdvance = false
        navigate(Key.DirectionRight); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(50)
        navigate(Key.DirectionRight)
        compose.runOnIdle { running = false }
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { running = true }
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, selected); assertEquals(listOf(1), selectedSteps) }
        anchor().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(2L), played) }
    }
}
