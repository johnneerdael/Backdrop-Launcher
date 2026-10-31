package nl.neerdael.backdrop_launcher.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import nl.neerdael.backdrop_launcher.core.*
import nl.neerdael.backdrop_launcher.data.LegacyPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class HomeRowLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val prefs = LegacyPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
    private val savedKeys = RowControl.entries.map { it.key } + listOf("accent_color", "app_icon_background_opacity")
    private val appsPerRow = 8
    private val heights = listOf(110, 140, 82)
    private val rows = heights.mapIndexed { index, height ->
        LauncherRow(index + 1L, "Row $index", false, 0, 0, height, index,
            List(appsPerRow) { LauncherApp("test.r$index.a$it", "App $index-$it", "1", false, false, null, "appicons/settings.png") })
    }
    private val library = object : LibraryAccess {
        override fun snapshot(refreshInstalled: Boolean) = LibrarySnapshot(rows.flatMap { it.apps }, rows)
        override fun createRow(name: String) = error("unused")
        override fun renameRow(id: Long, name: String) = Unit
        override fun updateRow(id: Long, hidden: Boolean, sort: Int, height: Int) = Unit
        override fun moveRow(id: Long, delta: Int) = Unit
        override fun deleteRow(id: Long) = Unit
        override fun setRowApps(id: Long, packages: List<String>) = Unit
        override fun rowPackages(id: Long) = emptyList<String>()
        override fun moveApp(rowId: Long, packageName: String, delta: Int) = Unit
        override fun setHidden(packageName: String, hidden: Boolean) = Unit
        override fun setArtwork(packageName: String, asset: String?) = Unit
        override fun recordLaunch(packageName: String) = Unit
        override fun close() = Unit
    }
    private val media = object : MediaAccess {
        override val configured = false
        override val connected = false
        override fun beginAuthorization(): DeviceAuthorization = error("unused")
        override fun pollAuthorization(auth: DeviceAuthorization): AuthorizationResult = error("unused")
        override fun cancelAuthorization() = Unit
        override fun disconnect() = Unit
        override fun catalogs() = emptyList<CatalogOption>()
        override fun refreshPopularLists() = emptyList<CatalogOption>()
        override fun selectedCatalogs() = emptyList<String>()
        override fun selectCatalogs(keys: List<String>) = Unit
        override fun restoredBackdrop() = Backdrop("backdrops/fight-club-55e2393686745.jpg")
        override fun nextBackdrop() = restoredBackdrop()
        override fun chooseBundledBackdrop(asset: String) = Backdrop(asset)
        override fun watchNext() = emptyList<ResumeItem>()
        override fun checkWatchNextPermission() = false
    }
    private val updates = object : UpdateAccess {
        override val enabled = false
        override fun check(): UpdateInfo? = null
        override fun download(update: UpdateInfo): File = error("unused")
    }
    private val actions = object : UiActions {
        override val library get() = this@HomeRowLayoutTest.library
        override fun refresh() = Unit
        override fun launch(packageName: String) = Unit
        override fun play(item: ResumeItem) = Unit
        override fun selectResume(item: ResumeItem) = Unit
        override fun appInfo(packageName: String) = Unit
        override fun uninstall(packageName: String) = Unit
        override fun systemSettings() = Unit
        override fun openNetworkSettings() = Unit
        override fun showClock() = Unit
        override fun screensaverSettings() = Unit
        override fun startScreensaver() = Unit
        override fun requestWatchNextPermission() = Unit
        override fun installApk(file: File) = Unit
        override fun message(text: String) = Unit
    }
    private lateinit var density: Density

    private fun clearPrefs() = savedKeys.forEach { prefs.remove(it) }
    @Before fun before() = clearPrefs()
    @After fun after() = clearPrefs()

    private val longText = "A very long line of text that wraps as far as the hero layout allows ".repeat(8)
    private val watchNext = listOf(
        ResumeItem(longText, longText, "appicons/settings.png", 35, "intent://one#Intent;scheme=test;package=test.player;end", "test.player", 17),
        ResumeItem("Second", "Second", "appicons/settings.png", 10, "intent://two#Intent;scheme=test;package=test.player;end", "test.player", 18))
    private fun scene(rowCount: Int, watch: Boolean, hero: Boolean) = Scene("Test", media.restoredBackdrop().image,
        rows.take(rowCount).map { row -> AppRow(row.name, row.apps.map { AppTile(it.packageName, it.label, it.artwork) }, row.id, row.height) },
        if (watch) watchNext else emptyList(), hero, true, -1, .1f, false, false)

    private fun home(rowCount: Int = 3, watch: Boolean = false, hero: Boolean = false) {
        compose.setContent {
            val context = LocalContext.current
            density = LocalDensity.current
            val artwork = remember { ComposeArtwork(context) }
            val selection = remember { HomeSelection() }
            DisposableEffect(artwork) { artwork.setActive(true); onDispose { artwork.close() } }
            LauncherScreen(HomeData(scene(rowCount, watch, hero), ""), library.snapshot(false), prefs, media, updates, actions, selection, artwork, true, 0)
        }
        compose.onNodeWithContentDescription("App 0-0").assertIsFocused()
    }
    private fun settings(route: String, maxRowPosition: Int = RowControl.POSITION.max) {
        compose.setContent {
            val context = LocalContext.current
            val artwork = remember { ComposeArtwork(context) }
            DisposableEffect(artwork) { artwork.setActive(true); onDispose { artwork.close() } }
            LauncherSettings(SettingsRoute(route), library.snapshot(false), prefs, media, updates, actions, artwork, true, 0, {}, { _, _ -> }, maxRowPosition)
        }
        compose.waitForIdle()
    }
    private fun press(code: Int, repeats: Int = 0) {
        val down = SystemClock.uptimeMillis()
        for (repeat in 0..repeats) send(down, KeyEvent.ACTION_DOWN, code, repeat)
        send(down, KeyEvent.ACTION_UP, code, 0)
    }
    private fun send(down: Long, action: Int, code: Int, repeat: Int) {
        val event = KeyEvent(down, SystemClock.uptimeMillis(), action, code, repeat, 0, -1, 97,
            if (repeat > 0) KeyEvent.FLAG_LONG_PRESS else 0, InputDevice.SOURCE_DPAD)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            WindowInspector.getGlobalWindowViews().last { it.isShown && it.hasFocus() }.dispatchKeyEvent(event)
        }
        compose.waitForIdle()
    }
    private fun bounds(label: String): Rect = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
    private fun Float.dp() = this / density.density

    /** Focus scales a tile about its centre, so compare centres against the unscaled height. */
    private fun top(label: String, tileHeight: Int) = bounds(label).center.y.dp() - tileHeight / 2f

    @Test fun everyRowStopsOnTheSameLineIncludingTheLastRow() {
        home()
        val line = top("App 0-0", 110)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithContentDescription("App 1-0").assertIsFocused()
        assertEquals(line, top("App 1-0", 140), 1f)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithContentDescription("App 2-0").assertIsFocused()
        assertEquals(line, top("App 2-0", 82), 1f)
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithContentDescription("App 0-0").assertIsFocused()
        assertEquals(line, top("App 0-0", 110), 1f)
    }

    @Test fun twoRowsAlsoShareTheLine() {
        home(rowCount = 2)
        val line = top("App 0-0", 110)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithContentDescription("App 1-0").assertIsFocused()
        assertEquals(line, top("App 1-0", 140), 1f)
    }

    @Test fun focusStaysOnTheRowsThroughoutTheSlide() {
        home()
        compose.mainClock.autoAdvance = false
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(4) {
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithContentDescription("Settings").assertIsNotFocused()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("App 1-0").assertIsFocused()
        compose.onNodeWithContentDescription("Settings").assertIsNotFocused()
    }

    @Test fun pressesDuringASlideCountFromTheRowBeingEntered() {
        home()
        compose.mainClock.autoAdvance = false
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.mainClock.advanceTimeBy(80)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("App 2-0").assertIsFocused()
    }

    @Test fun upToTheToolbarDuringASlideIsNotPulledBackToTheRow() {
        home()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithContentDescription("App 1-0").assertIsFocused()
        compose.mainClock.autoAdvance = false
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.mainClock.advanceTimeBy(80)
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
    }

    @Test fun theRowAboveLeavesNoPixelsBehindAndTheRowBelowUsesItsOpacity() {
        prefs.putString("accent_color", "FF0000")
        prefs.putDouble("app_icon_background_opacity", 1.0)
        prefs.putInt(RowControl.BELOW.key, 50)
        home()
        // A card corner stays clear of the centred artwork.
        fun pixel(rect: Rect) = compose.onRoot().captureToImage().toPixelMap()[(rect.left + 24).toInt(), (rect.top + 6).toInt()]
        val next = bounds("App 1-1")
        val backdropOnly = compose.onRoot().captureToImage().toPixelMap()
        val faded = pixel(next)
        assertTrue("Row below should be part way between backdrop and card: $faded", faded.red in .4f..0.85f)

        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithContentDescription("App 1-0").assertIsFocused()
        val left = bounds("App 0-1")
        assertTrue("The old row must slide upwards", left.bottom < bounds("App 1-0").top)
        val before = backdropOnly[(left.left + 24).toInt(), (left.top + 6).toInt()]
        val after = pixel(left)
        assertEquals("Nothing of the old row may remain", before.red, after.red, .02f)
        assertEquals(before.green, after.green, .02f)
    }

    @Test fun rowPositionHoldsThroughFocusBeyondTheTvPivot() {
        prefs.putInt(RowControl.START.key, 320)
        home()
        val width = 195.55f
        fun focusedLeft(label: String) = bounds(label).center.x.dp() - width / 2f
        // The pivot sees the 1.06 focus scale, so the tile's visible edge is what lands on the start.
        val scaleRoom = 8f
        assertEquals(320f, focusedLeft("App 0-0"), scaleRoom)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithContentDescription("App 0-1").assertIsFocused()
        assertEquals(320f, focusedLeft("App 0-1"), scaleRoom)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithContentDescription("App 0-0").assertIsFocused()
        assertEquals(320f, focusedLeft("App 0-0"), scaleRoom)
    }

    @Test fun layoutChangesApplyLiveToTilesAndRows() {
        home()
        val line = top("App 0-0", 110)
        prefs.putInt(RowControl.SIZE.key, 150)
        prefs.putInt(RowControl.APP_GAP.key, 60)
        prefs.putInt(RowControl.ROW_GAP.key, 100)
        prefs.putInt(RowControl.POSITION.key, 60)
        compose.waitForIdle()
        val tile = bounds("App 0-1")
        assertEquals(165f, tile.height.dp(), 1f)
        assertEquals(293.3f, tile.width.dp(), 1f)
        assertEquals(293.3f + 60f, bounds("App 0-2").left.dp() - tile.left.dp(), 1f)
        assertEquals(165f + 100f, bounds("App 1-1").top.dp() - tile.top.dp(), 1f)
        assertTrue("A higher setting lifts the row", top("App 0-1", 165) < line - 90f)
    }

    @Test fun theCompactFeedStopsTheRowAtItsMaximumHeight() {
        prefs.putInt(RowControl.POSITION.key, RowControl.POSITION.max)
        home(watch = true)
        val feed = compose.onNodeWithContentDescription("Continue watching $longText").fetchSemanticsNode().boundsInRoot
        assertEquals(WATCH_NEXT_BOTTOM, feed.bottom.dp(), 1f)
        assertTrue("Row must sit below the feed", top("App 0-0", 110) >= feed.bottom.dp())
    }

    @Test fun theHeroFeedKeepsTheRowBelowEvenWithLongText() {
        prefs.putInt(RowControl.POSITION.key, RowControl.POSITION.max)
        home(watch = true, hero = true)
        val hero = compose.onNodeWithText("1 / 2").fetchSemanticsNode().boundsInRoot
        assertTrue("Hero ends at ${hero.bottom.dp()} dp", hero.bottom.dp() <= WATCH_NEXT_BOTTOM)
        assertTrue("Row must sit below the hero", top("App 0-0", 110) >= hero.bottom.dp())
    }

    @Test fun withoutTheFeedTheRowCanUseTheFullRange() {
        prefs.putInt(RowControl.POSITION.key, RowControl.POSITION.max)
        home()
        assertEquals(118f, top("App 0-0", 110), 2f)
    }

    @Test fun largestAppSizeStaysBetweenTheFeedAndTheScreenEdge() {
        prefs.putInt(RowControl.SIZE.key, RowControl.SIZE.max)
        prefs.putInt(RowControl.POSITION.key, RowControl.POSITION.max)
        home(watch = true)
        val tile = bounds("App 0-1")
        assertEquals(212f, tile.height.dp(), 1f)
        assertTrue(tile.top.dp() >= WATCH_NEXT_BOTTOM)
        assertTrue(tile.bottom.dp() <= 540f)
    }

    @Test fun focusedRowHeightControlStopsAtTheFeedAndSaysWhy() {
        settings("rowlayout", maxRowPosition = 42)
        compose.onNodeWithText("Stops just below Continue Watching").assertExists()
        press(KeyEvent.KEYCODE_DPAD_RIGHT, repeats = 2)
        compose.waitForIdle()
        compose.onNodeWithText("42%").assertExists()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitUntil(3000) { prefs.int(RowControl.POSITION.key, 0) == 40 }
    }

    @Test fun homePassesTheFeedLimitToTheSettingsPage() {
        home(watch = true)
        press(KeyEvent.KEYCODE_MENU)
        compose.onNodeWithText("Interface").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("App row layout").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Stops just below Continue Watching").assertExists()
    }

    @Test fun smallRowGapKeepsFullSizeTiles() {
        prefs.putInt(RowControl.ROW_GAP.key, RowControl.ROW_GAP.min)
        prefs.putInt(RowControl.POSITION.key, RowControl.POSITION.max)
        home()
        assertEquals(140f, bounds("App 1-1").height.dp(), 1f)
        assertEquals(110f + 8f, bounds("App 1-1").top.dp() - bounds("App 0-1").top.dp(), 1f)
    }

    @Test fun leftAndRightAdjustTheFocusedSettingIncludingHeldRepeats() {
        settings("rowlayout")
        compose.onNodeWithText("42%").assertExists()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitUntil(3000) { prefs.int(RowControl.POSITION.key, 0) == 44 }
        press(KeyEvent.KEYCODE_DPAD_RIGHT, repeats = 2)
        compose.waitUntil(3000) { prefs.int(RowControl.POSITION.key, 0) == 50 }
        compose.waitUntil(3000) { compose.onAllNodesWithText("50%").fetchSemanticsNodes().isNotEmpty() }
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitUntil(3000) { prefs.int(RowControl.POSITION.key, 0) == 48 }
    }

    @Test fun selectStepsAndWrapsAndResetRestoresDefaults() {
        prefs.putInt(RowControl.POSITION.key, RowControl.POSITION.max)
        prefs.putInt(RowControl.ROW_GAP.key, 100)
        settings("rowlayout")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(3000) { prefs.int(RowControl.POSITION.key, 0) == RowControl.POSITION.min }
        compose.onNodeWithText("Reset app row layout").performScrollTo().performClick()
        compose.waitUntil(3000) { RowControl.entries.all { prefs.int(it.key, -1) == -1 } }
    }

    @Test fun greyAndBlackAccentsCanBeChosen() {
        settings("accent")
        compose.onNodeWithText("Grey").performScrollTo().performClick()
        compose.waitUntil(3000) { prefs.string("accent_color") == "9E9E9E" }
        compose.onNodeWithText("Black").performScrollTo().performClick()
        compose.waitUntil(3000) { prefs.string("accent_color") == "000000" }
    }
}
