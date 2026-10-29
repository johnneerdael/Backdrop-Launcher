package nl.neerdael.backdrop_launcher.nativebenchmark

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ResourceStateTest {
    @Test fun unchangedReturnDoesNotNeedAnotherLoadAndChangesDuringLoadStayDirty() {
        val state = HomeRefreshState()
        val first = state.version
        assertTrue(state.dirty)
        assertTrue(state.accept(first))
        assertFalse(state.dirty)
        state.invalidate()
        val pending = state.version
        state.invalidate()
        assertFalse(state.accept(pending))
        assertTrue(state.dirty)
        assertTrue(state.accept(state.version))
        assertFalse(state.dirty)
    }

    @Test fun identicalRequestsShareOneDecodeAndOldCompletionCannotConsumeANewBatch() {
        val requests = ImageRequests<String>()
        val first = requests.join("poster@320", "one")
        val second = requests.join("poster@320", "two")
        assertTrue(first.second)
        assertFalse(second.second)
        assertSame(first.first, second.first)
        assertEquals(listOf("one", "two"), requests.complete(first.first))
        val stale = requests.join("poster@320", "old").first
        requests.clear()
        val fresh = requests.join("poster@320", "new").first
        assertTrue(requests.complete(stale).isEmpty())
        assertEquals(listOf("new"), requests.complete(fresh))
    }

    @Test fun nestedLocalArtworkRevisionChangesWithoutChangingTheScene() {
        val root = Files.createTempDirectory("backdrop-artwork-test").toFile()
        try {
            val image = root.resolve("appicons/custom.png")
            image.parentFile.mkdirs()
            image.writeText("first")
            val paths = listOf("appicons/custom.png")
            val before = localArtworkStamp(root, paths)
            image.writeText("second, changed")
            assertNotEquals(before, localArtworkStamp(root, paths))
            assertThrows(IllegalArgumentException::class.java) { localArtworkStamp(root, listOf("../outside")) }
        } finally { root.deleteRecursively() }
    }
}
