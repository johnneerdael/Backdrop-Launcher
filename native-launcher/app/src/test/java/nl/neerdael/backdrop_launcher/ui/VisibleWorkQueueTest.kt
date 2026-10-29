package nl.neerdael.backdrop_launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleWorkQueueTest {
    @Test fun fullExecutorRetriesVisibleImagesAndDiscardsOffscreenWaitingWork() {
        var free = 1
        val started = mutableListOf<String>()
        val queue = VisibleWorkQueue<String> { if (free == 0) false else { free--; started.add(it); true } }
        queue.offer("first")
        queue.offer("offscreen")
        queue.offer("still visible")
        queue.cancel("offscreen")
        free++
        queue.drain()
        assertEquals(listOf("first", "still visible"), started)
    }
    @Test fun pauseClearsWaitingImagesBeforeAnotherWorkerCompletes() {
        var ready = false
        val started = mutableListOf<String>()
        val queue = VisibleWorkQueue<String> { if (!ready) false else { started.add(it); true } }
        queue.offer("old")
        queue.clear()
        ready = true
        queue.drain()
        assertEquals(emptyList<String>(), started)
    }
}
