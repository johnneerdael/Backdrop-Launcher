package nl.neerdael.backdrop_launcher.composebenchmark

import nl.neerdael.backdrop_launcher.nativebenchmark.*
import org.junit.Assert.*
import org.junit.Test

class HomeSelectionTest {
    private fun scene(rows: List<AppRow>, watch: List<ResumeItem> = emptyList()) =
        Scene("test", "wall.jpg", rows, watch, false, false, -1, .2f, false, false)

    @Test fun removedFocusedAppFallsBackToFirstAvailableApp() {
        val state = HomeSelection().apply { app = AppFocus(0, 1, "gone") }
        state.reconcile(scene(listOf(AppRow("empty", emptyList()), AppRow("apps", listOf(AppTile("kept", "Kept", null))))))
        assertEquals(AppFocus(1, 0, "kept"), state.app)
    }
    @Test fun reorderedAppsPreserveIdentityAndResumeSelectionClamps() {
        val state = HomeSelection().apply { app = AppFocus(0, 0, "b"); resumeIndex = 8 }
        state.reconcile(scene(listOf(AppRow("apps", listOf(AppTile("a", "A", null), AppTile("b", "B", null)))),
            listOf(ResumeItem("one", "", null, 0, null, null))))
        assertEquals(AppFocus(0, 1, "b"), state.app)
        assertEquals(0, state.resumeIndex)
    }
    @Test fun carouselWrapsBothDirectionsAndHandlesEmptyProvider() {
        val state = HomeSelection()
        state.cycle(3, -1)
        assertEquals(2, state.resumeIndex)
        state.cycle(3, 1)
        assertEquals(0, state.resumeIndex)
        state.cycle(0, -1)
        assertEquals(0, state.resumeIndex)
    }
    @Test fun removedWatchNextFallsBackToAppsOrSettingsWithoutLosingAppIdentity() {
        val state = HomeSelection().apply { area = HomeSelection.Area.WATCH_NEXT }
        state.reconcile(scene(listOf(AppRow("apps", listOf(AppTile("a", "A", null))))))
        assertEquals(HomeSelection.Area.APPS, state.area)
        state.reconcile(scene(emptyList()))
        assertEquals(HomeSelection.Area.SETTINGS, state.area)
        assertNull(state.app)
    }
}
