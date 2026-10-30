package nl.neerdael.backdrop_launcher.ui

import org.junit.Assert.*
import org.junit.Test

class WatchNextMotionTest {
    private val keys = listOf("a", "b", "c", "d")
    private fun pose(state: WatchNextMotionState, index: Int, fraction: Float) = WatchNextPose().also {
        state.writePose(index, fraction, 960f, it)
    }
    @Test fun settledPreviewIsOneThirdSizeAtTwentyPercentOpacity() {
        val state = WatchNextMotionState.initial(keys, 0)
        val active = pose(state, 0, 1f); val next = pose(state, 1, 1f)
        assertEquals(32f, active.x, .001f); assertEquals(1f, active.scale, .001f)
        assertEquals(432f, next.x, .001f); assertEquals(1f / 3, next.scale, .001f)
        assertEquals(.2f, next.alpha, .001f)
    }
    @Test fun outgoingRightCardKeepsFullSizeAndOpacityUntilOffscreen() {
        val state = WatchNextMotionState.initial(keys, 0).request(1).begin()
        for (t in listOf(0f, .1f, .25f, .45f, .7f)) {
            val outgoing = pose(state, 0, t)
            assertEquals(1f, outgoing.scale, .001f); assertEquals(1f, outgoing.alpha, .001f)
            assertTrue(outgoing.x <= 32)
        }
        assertTrue(pose(state, 0, 1f).x + WATCH_CARD_WIDTH <= 0)
    }
    @Test fun incomingPreviewMovesLeftAndUpWhileGrowingAndBecomingOpaque() {
        val state = WatchNextMotionState.initial(keys, 0).request(1).begin()
        val start = pose(state, 1, 0f); val middle = pose(state, 1, .5f); val end = pose(state, 1, 1f)
        assertTrue(middle.x < start.x && middle.x > end.x)
        assertTrue(middle.y < start.y && middle.y > end.y)
        assertTrue(middle.scale > start.scale && middle.scale < end.scale)
        assertTrue(middle.alpha > start.alpha && middle.alpha < end.alpha)
        assertEquals(32f, end.x, .001f); assertEquals(0f, end.y, .001f)
        assertEquals(1f, end.scale, .001f); assertEquals(1f, end.alpha, .001f)
    }
    @Test fun leftUsesFullSizeTranslationAndOnlyReintroducesPreviewOffscreen() {
        val state = WatchNextMotionState.initial(keys, 1).request(-1).begin()
        for (step in 0..100) {
            val t = step / 100f
            val previous = pose(state, 0, t)
            assertEquals(1f, previous.scale, .001f)
            val outgoing = pose(state, 1, t)
            assertTrue(outgoing.scale == 1f || kotlin.math.abs(outgoing.scale - 1f / 3) < .001f)
        }
        assertEquals(.2f, pose(state, 1, 1f).alpha, .001f)
    }
    @Test fun rapidRequestsQueueWithoutChangingCurrentStepAndDoNotWrap() {
        var state = WatchNextMotionState.initial(keys, 0).request(-1)
        assertFalse(state.pending)
        state = state.request(1).begin()
        state = state.request(1).request(-1)
        assertEquals(1, state.target); assertEquals(1, state.requested)
        state = state.complete()
        assertFalse(state.pending); assertEquals(1, state.settled)
        state = state.request(20)
        assertEquals(3, state.requested)
        while (state.pending) state = state.begin().complete()
        assertEquals(3, state.settled)
    }
    @Test fun feedChangesPreserveIncomingIdentityAndDropStaleQueue() {
        val moving = WatchNextMotionState.initial(keys, 0).request(1).begin().request(1)
        val updated = moving.reconcile(listOf("x", "b", "a"), 0)
        assertEquals(1, updated.settled); assertFalse(updated.moving); assertFalse(updated.pending)
        assertEquals(0, updated.reconcile(listOf("a"), 0).settled)
    }
    @Test fun pausingCommitsCurrentTargetAndDropsFutureSteps() {
        val moving = WatchNextMotionState.initial(keys, 0).request(1).begin().request(2)
        val paused = moving.pause()
        assertEquals(1, paused.settled); assertEquals(1, paused.requested)
        assertFalse(paused.moving); assertFalse(paused.pending)
    }
    @Test fun renderingIsBoundedToThreeResidentsAndTwoVisibleCards() {
        for (direction in listOf(-1, 1)) {
            val state = WatchNextMotionState.initial(keys, 1).request(direction).begin()
            assertTrue(state.residentIndices().size <= 3)
            for (step in 0..100) {
                val visible = state.residentIndices().count { index ->
                    val p = pose(state, index, step / 100f)
                    p.alpha > .001f && p.x < 960 && p.x + WATCH_CARD_WIDTH * p.scale > 0
                }
                assertTrue("$direction at $step showed $visible cards", visible <= 2)
            }
        }
    }
}
