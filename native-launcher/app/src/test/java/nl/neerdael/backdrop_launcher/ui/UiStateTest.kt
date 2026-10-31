package nl.neerdael.backdrop_launcher.ui

import nl.neerdael.backdrop_launcher.core.*
import org.junit.Assert.*
import org.junit.Test

class UiStateTest {
    private val a = AppTile("a", "A", null)
    private val b = AppTile("b", "B", null)
    private fun scene(rows: List<AppRow>, watch: List<ResumeItem> = emptyList()) =
        Scene("Home", "wall.jpg", rows, watch, false, false, -1, .1f, false, false)
    private fun item(id: Long) = ResumeItem("item$id", "", null, 0, null, "app", id, "same-content")

    @Test fun eachPersistentRowRemembersItsAppAcrossReorderAndDeletion() {
        val before = scene(listOf(AppRow("first", listOf(a, b), 10), AppRow("second", listOf(b, a), 20)))
        val selection = HomeSelection()
        selection.remember(before, 0, 1)
        selection.remember(before, 1, 1)
        assertEquals("b", selection.focusForRow(before, 0)?.packageName)
        val reordered = scene(listOf(before.rows[1].copy(apps = listOf(a, b)), before.rows[0]))
        selection.reconcile(reordered)
        assertEquals(20L, selection.rowId)
        assertEquals(AppFocus(0, 0, "a"), selection.app)
        val removed = scene(listOf(reordered.rows[0].copy(apps = listOf(b))))
        selection.reconcile(removed)
        assertEquals("b", selection.app?.packageName)
        selection.reconcile(scene(emptyList()))
        assertEquals(HomeSelection.Area.SETTINGS, selection.area)
    }

    @Test fun providerIdKeepsResumeSelectionWhenAnotherItemIsInserted() {
        val selection = HomeSelection()
        val initial = scene(emptyList(), listOf(item(5), item(6)))
        selection.reconcile(initial)
        selection.selectResume(initial, 1)
        selection.reconcile(scene(emptyList(), listOf(item(9), item(5), item(6))))
        assertEquals(2, selection.resumeIndex)
        selection.cycle(3, -4)
        assertEquals(1, selection.resumeIndex)
        selection.cycle(3, 10, wrap = false)
        assertEquals(2, selection.resumeIndex)
        selection.cycle(0, -1)
        assertEquals(0, selection.resumeIndex)
    }

    @Test fun removingHiddenFocusedAppDoesNotEraseOtherRowMemory() {
        val initial = scene(listOf(AppRow("one", listOf(a, b), 1), AppRow("two", listOf(a, b), 2)))
        val selection = HomeSelection()
        selection.remember(initial, 0, 1)
        selection.remember(initial, 1, 0)
        val hidden = scene(listOf(initial.rows[0].copy(apps = listOf(a)), initial.rows[1]))
        selection.reconcile(hidden)
        assertEquals("a", selection.focusForRow(hidden, 0)?.packageName)
        assertEquals("a", selection.focusForRow(hidden, 1)?.packageName)
        selection.area = HomeSelection.Area.SETTINGS
        selection.reconcile(hidden)
        assertEquals(HomeSelection.Area.SETTINGS, selection.area)
    }

    @Test fun appearancePreservesLegacyDefaultsAndDisablesAnimationWithoutBorders() {
        val prefs = MemoryPreferences()
        val defaults = HomeAppearance.read(prefs)
        assertEquals(0xffffffffL, defaults.accent)
        assertEquals(.1f, defaults.opacity)
        assertEquals(1.5f, defaults.borderDp)
        assertFalse(defaults.animateBorder)
        assertTrue(defaults.showTime)
        assertFalse(defaults.showDate)
        prefs.putDouble("app_icon_background_opacity", .46)
        prefs.putDouble("focus_border_thickness", 2.0)
        prefs.putString("accent_color", "7C4DFF")
        prefs.putBool("app_highlight_animation_enabled", true)
        prefs.putBool("show_focus_borders", false)
        val changed = HomeAppearance.read(prefs)
        assertEquals(.5f, changed.opacity)
        assertEquals(0xff7c4dffL, changed.accent)
        assertFalse(changed.animateBorder)
        assertEquals(2f, changed.borderDp)
    }

    @Test fun rowSlotsCarryTheirLabelsAndTheChosenSpacing() {
        assertEquals(162f, homeRowExtent(110, false, false, 52), .01f)
        assertEquals(216f, homeRowExtent(110, true, true, 52), .01f)
        assertEquals(118f, homeRowExtent(110, false, false, 8), .01f)
    }

    @Test fun rowControlsMatchTheEarlierLayoutUntilChanged() {
        val layout = HomeAppearance.read(MemoryPreferences()).rows
        assertEquals(HomeRowLayout(42, 32, 100, 24, 52, 20), layout)
        assertEquals(110, layout.tileHeight(110))
        assertEquals(313.2f, homeRowTop(540f, layout.position, 140, false, false), .01f)
    }

    @Test fun rowControlsStayInRangeAndUseNativePreferences() {
        val prefs = MemoryPreferences()
        for (control in RowControl.entries) {
            assertTrue(control.key.startsWith("native."))
            assertEquals(0, (control.default - control.min) % control.step)
            prefs.putInt(control.key, control.max + 1000)
            assertEquals(control.max, control.read(prefs))
            prefs.putInt(control.key, control.min - 1000)
            assertEquals(control.min, control.read(prefs))
            assertEquals(control.max, control.stepped(control.max, 1))
            assertEquals(control.min, control.stepped(control.min, -1))
            assertEquals(control.min, control.cycled(control.max))
            assertEquals(control.min + control.step, control.cycled(control.min))
        }
    }

    @Test fun appSizeScalesEachRowWithoutLosingItsOwnSize() {
        val layout = HomeRowLayout(42, 32, 150, 24, 52, 20)
        assertEquals(123, layout.tileHeight(82))
        assertEquals(210, layout.tileHeight(140))
        assertEquals(40, layout.copy(size = 50).tileHeight(50))
    }

    @Test fun activeRowStaysOnScreenAtLargeSizes() {
        assertEquals(108f, homeRowTop(540f, 80, 280, true, true), .01f)
        // 280 tile + 26 title + 28 name + 18 focus room leaves 188 of a 540 screen.
        assertEquals(188f, homeRowTop(540f, 14, 280, true, true), .01f)
        assertEquals(0f, homeRowTop(200f, 14, 280, true, true), .01f)
    }

    @Test fun continueWatchingIsTheUpperLimitForTheRow() {
        assertEquals(310f, WATCH_NEXT_BOTTOM, .01f)
        assertEquals(42, homeRowPositionLimit(540f, WATCH_NEXT_BOTTOM))
        assertEquals(313.2f, homeRowTop(540f, 42, 110, false, false, WATCH_NEXT_BOTTOM), .01f)
        assertEquals(WATCH_NEXT_BOTTOM, homeRowTop(540f, 80, 110, false, false, WATCH_NEXT_BOTTOM), .01f)
        assertEquals(RowControl.POSITION.max, homeRowPositionLimit(540f, 0f))
        assertEquals(RowControl.POSITION.min, homeRowPositionLimit(100f, WATCH_NEXT_BOTTOM))
    }

    @Test fun tilesShrinkRatherThanRiseIntoContinueWatching() {
        val layout = HomeRowLayout(42, 32, 200, 24, 52, 20)
        assertEquals(212, homeTileLimit(540f, WATCH_NEXT_BOTTOM, false, false))
        assertEquals(158, homeTileLimit(540f, WATCH_NEXT_BOTTOM, true, true))
        val tile = layout.tileHeight(140, homeTileLimit(540f, WATCH_NEXT_BOTTOM, true, true))
        assertEquals(158, tile)
        assertEquals(WATCH_NEXT_BOTTOM, homeRowTop(540f, 42, tile, true, true, WATCH_NEXT_BOTTOM), .01f)
        assertEquals(280, layout.tileHeight(140, homeTileLimit(540f, 0f, true, true)))
    }

    @Test fun focusedRowHeightStepsStopAtTheLimit() {
        val control = RowControl.POSITION
        assertEquals(42, control.stepped(42, 1, 42))
        assertEquals(40, control.stepped(42, -1, 42))
        assertEquals(control.min, control.cycled(42, 42))
    }

    @Test fun rowTravelCoversUnevenRowsInBothDirections() {
        val extents = listOf(162f, 192f, 134f)
        assertEquals(354f, homeRowTravel(extents, 0, 2), .01f)
        assertEquals(-192f, homeRowTravel(extents, 2, 1), .01f)
        assertEquals(0f, homeRowTravel(extents, 1, 1), .01f)
        assertEquals(1.5f, homeRowPage(extents, 1, 96f), .01f)
        assertEquals(5f, homeRowPage(extents, 5, 96f), .01f)
    }

    @Test fun rowsAboveDisappearAndRowsBelowSettleAtTheirOpacity() {
        assertEquals(1f, homeRowAlpha(0f, .2f), .001f)
        assertEquals(.6f, homeRowAlpha(.5f, .2f), .001f)
        assertEquals(.2f, homeRowAlpha(1f, .2f), .001f)
        assertEquals(.2f, homeRowAlpha(4f, .2f), .001f)
        assertEquals(.5f, homeRowAlpha(-.5f, .2f), .001f)
        assertEquals(0f, homeRowAlpha(-1f, .2f), .001f)
        assertEquals(0f, homeRowAlpha(-3f, .2f), .001f)
    }

    @Test fun smallSizeStepsReuseTheDecodedArtwork() {
        assertEquals(120, homeDecodeHeight(110))
        assertEquals(120, homeDecodeHeight(120))
        assertEquals(160, homeDecodeHeight(121))
    }

    @Test fun blackCardsKeepAVisibleFocusIndicator() {
        val prefs = MemoryPreferences()
        prefs.putString("accent_color", "000000")
        val black = HomeAppearance.read(prefs)
        assertEquals(0xff000000L, black.accent)
        assertEquals(0xffffffffL, black.indicator)
        prefs.putString("accent_color", "9E9E9E")
        val grey = HomeAppearance.read(prefs)
        assertEquals(0xff9e9e9eL, grey.accent)
        assertEquals(0xff9e9e9eL, grey.indicator)
    }

    @Test fun releaseNotesAreReadableWithoutImageOrLinkMarkup() {
        val lines = readableNoteLines("![banner](https://example.test/a.png)\n## What's new\n- **Rows** and [downloads](https://example.test)\n<p>Ready &amp; signed</p>")
        assertEquals(listOf("What's new", "- Rows and downloads", "Ready & signed"), lines)
    }

    @Test fun membershipChangesRetainGloballyHiddenMembersAndTheirOrder() {
        val library = MembershipLibrary()
        toggleRowMembership(library, 8, "new")
        assertEquals(listOf("visible", "hidden", "new"), library.members)
        toggleRowMembership(library, 8, "visible")
        assertEquals(listOf("hidden", "new"), library.members)
    }
}

internal class MemoryPreferences : PreferenceAccess {
    private val values = mutableMapOf<String, Any>()
    override fun bool(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun string(key: String, default: String) = values[key] as? String ?: default
    override fun int(key: String, default: Int) = values[key] as? Int ?: default
    override fun long(key: String, default: Long) = values[key] as? Long ?: default
    override fun double(key: String, default: Double) = values[key] as? Double ?: default
    @Suppress("UNCHECKED_CAST") override fun strings(key: String, default: List<String>) = values[key] as? List<String> ?: default
    override fun putBool(key: String, value: Boolean) { values[key] = value }
    override fun putString(key: String, value: String) { values[key] = value }
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun putStrings(key: String, value: List<String>) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun listen(changed: (String) -> Unit): () -> Unit = {}
}

private class MembershipLibrary : LibraryAccess {
    var members = listOf("visible", "hidden")
    override fun rowPackages(id: Long) = members
    override fun setRowApps(id: Long, packages: List<String>) { members = packages }
    override fun snapshot(refreshInstalled: Boolean) = LibrarySnapshot(emptyList(), emptyList())
    override fun createRow(name: String) = 1L
    override fun renameRow(id: Long, name: String) {}
    override fun updateRow(id: Long, hidden: Boolean, sort: Int, height: Int) {}
    override fun moveRow(id: Long, delta: Int) {}
    override fun deleteRow(id: Long) {}
    override fun moveApp(rowId: Long, packageName: String, delta: Int) {}
    override fun setHidden(packageName: String, hidden: Boolean) {}
    override fun setArtwork(packageName: String, asset: String?) {}
    override fun recordLaunch(packageName: String) {}
    override fun close() {}
}
