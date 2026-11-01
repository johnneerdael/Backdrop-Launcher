package nl.neerdael.backdrop_launcher.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import nl.neerdael.backdrop_launcher.core.*
import nl.neerdael.backdrop_launcher.data.LegacyPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ChannelRowTest {
    @get:Rule val compose = createComposeRule()
    private val prefs = LegacyPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
    private val apps = List(4) { LauncherApp("test.app$it", "App $it", "1", false, false, null, "appicons/settings.png") }
    private val appRow = LauncherRow(1, "Apps", false, 0, 1, 110, 0, apps)
    private val channelRow = LauncherRow(2, "Movies", false, 0, 3, 110, 1, emptyList(), "tv.one|movies")
    private val published = listOf(TvChannel("tv.one|movies", "Movies", "test.app0", "One"), TvChannel("tv.two|news", "News", "test.app1", "Two"))
    private fun program(id: Long, aspect: Float = 2f / 3f, image: String? = "appicons/settings.png") =
        ChannelProgram(ResumeItem("Film $id", "", image, 0, "intent:$id", "test.app0", id), aspect)
    private val programs = List(8) { program(it + 1L) }
    private fun appTiles() = apps.map { AppTile(it.packageName, it.label, it.artwork) }
    private fun scene(tiles: List<ChannelProgram>) = Scene("Test", "backdrops/fight-club-55e2393686745.jpg",
        listOf(AppRow("Apps", appTiles(), 1), AppRow("Movies", tiles.map { it.tile() }, 2)), emptyList(), false, true, -1, .1f, false, false)

    private val played = mutableListOf<ResumeItem>()
    private val launched = mutableListOf<String>()
    private val channelWrites = mutableListOf<Triple<String, String, Boolean>>()
    private val deleted = mutableListOf<Long>()
    private var snapshot = LibrarySnapshot(apps, listOf(appRow, channelRow))
    private val library = object : LibraryAccess {
        override fun snapshot(refreshInstalled: Boolean) = snapshot
        override fun createRow(name: String) = error("unused")
        override fun renameRow(id: Long, name: String) = Unit
        override fun updateRow(id: Long, hidden: Boolean, sort: Int, height: Int) = Unit
        override fun moveRow(id: Long, delta: Int) = Unit
        override fun deleteRow(id: Long) { deleted += id }
        override fun setRowApps(id: Long, packages: List<String>) = Unit
        override fun rowPackages(id: Long) = emptyList<String>()
        override fun moveApp(rowId: Long, packageName: String, delta: Int) = Unit
        override fun setHidden(packageName: String, hidden: Boolean) = Unit
        override fun setArtwork(packageName: String, asset: String?) = Unit
        override fun recordLaunch(packageName: String) = Unit
        override fun setChannelRow(key: String, name: String, shown: Boolean) { channelWrites += Triple(key, name, shown) }
        override fun close() = Unit
    }
    private var permission = true
    private var channelsOnTv = published
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
        override fun checkWatchNextPermission() = permission
        override fun tvChannels() = channelsOnTv
    }
    private val updates = object : UpdateAccess {
        override val enabled = false
        override fun check(): UpdateInfo? = null
        override fun download(update: UpdateInfo): File = error("unused")
    }
    private val actions = object : UiActions {
        override val library get() = this@ChannelRowTest.library
        override fun refresh() = Unit
        override fun launch(packageName: String) { launched += packageName }
        override fun play(item: ResumeItem) { played += item }
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
    private var density = 1f
    private var home by mutableStateOf<Scene?>(null)

    private fun home(tiles: List<ChannelProgram> = programs) {
        home = scene(tiles)
        compose.setContent {
            val context = LocalContext.current
            density = LocalDensity.current.density
            val artwork = remember { ComposeArtwork(context) }
            val selection = remember { HomeSelection() }
            DisposableEffect(artwork) { artwork.setActive(true); onDispose { artwork.close() } }
            LauncherScreen(HomeData(home!!, ""), library.snapshot(false), prefs, media, updates, actions, selection, artwork, true, 0)
        }
        compose.onNodeWithContentDescription("App 0").assertIsFocused()
    }
    private fun settings(route: SettingsRoute) {
        compose.setContent {
            val context = LocalContext.current
            val artwork = remember { ComposeArtwork(context) }
            DisposableEffect(artwork) { artwork.setActive(true); onDispose { artwork.close() } }
            LauncherSettings(route, library.snapshot(false), prefs, media, updates, actions, artwork, true, 0, {}, { _, _ -> })
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
    private fun scrollDownToChannel() { press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithContentDescription("Film 1").assertIsFocused() }

    @Test fun posterTilesKeepTheRowHeightAndTakeTheirArtShape() {
        home(listOf(program(1), program(2, 16f / 9f)))
        scrollDownToChannel()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithContentDescription("Film 2").assertIsFocused()
        val poster = bounds("Film 1")
        assertEquals(110f, poster.height / density, 1f)
        assertEquals(110f * 2f / 3f, poster.width / density, 1f)
        assertEquals(16f / 9f, bounds("Film 2").width / bounds("Film 2").height, .02f)
    }

    @Test fun selectingAProgramPlaysItInsteadOfOpeningItsApp() {
        home()
        scrollDownToChannel()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(1L), played.map { it.id })
        assertTrue(launched.isEmpty())
    }

    @Test fun holdingAProgramOpensItsRowNotAnAppMenu() {
        home()
        scrollDownToChannel()
        val down = SystemClock.uptimeMillis()
        send(down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithText("Show on home screen").assertExists()
        assertTrue(played.isEmpty())
    }

    @Test fun rowNavigationTreatsAChannelLikeAnyOtherRow() {
        home()
        val line = bounds("App 0").center.y
        scrollDownToChannel()
        assertEquals(line, bounds("Film 1").center.y, 4f)
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithContentDescription("App 0").assertIsFocused()
    }

    @Test fun programWithoutArtShowsItsTitle() {
        home(listOf(program(1, image = null)))
        scrollDownToChannel()
        compose.onNodeWithText("Film 1", useUnmergedTree = true).assertExists()
    }

    @Test fun postersArrivingDoNotMoveFocusOrTheRow() {
        // Mid-row in a row that overflows, so focus sits at the pivot rather than either end.
        val wide = List(8) { program(it + 1L, 16f / 9f) }
        home(wide.map { it.copy(item = it.item.copy(image = null)) })
        scrollDownToChannel()
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        compose.onNodeWithContentDescription("Film 4").assertIsFocused()
        val before = bounds("Film 4")
        home = scene(wide)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Film 4").assertIsFocused()
        assertEquals(before.left, bounds("Film 4").left, 1f)
    }

    @Test fun channelPageListsWhatTheTvPublishesAndTogglesRows() {
        settings(SettingsRoute("channels"))
        compose.onNodeWithText("News").assertExists().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil { channelWrites.isNotEmpty() }
        assertEquals(Triple("tv.two|news", "News", true), channelWrites.single())
    }

    @Test fun selectedChannelCanBeTurnedOff() {
        settings(SettingsRoute("channels"))
        compose.onNodeWithText("Movies").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil { channelWrites.isNotEmpty() }
        assertEquals(Triple("tv.one|movies", "Movies", false), channelWrites.single())
    }

    @Test fun selectedChannelThatStoppedPublishingStaysListedSoItCanBeRemoved() {
        channelsOnTv = emptyList()
        settings(SettingsRoute("channels"))
        compose.onNodeWithText("Movies").assertExists()
        compose.onNodeWithText("Not publishing right now").assertExists()
    }

    @Test fun missingPermissionOffersTheGrantInsteadOfAnEmptyList() {
        permission = false
        channelsOnTv = emptyList()
        snapshot = LibrarySnapshot(apps, listOf(appRow))
        settings(SettingsRoute("channels"))
        compose.onNodeWithText("Grant TV listings permission").assertExists()
    }

    @Test fun tvWithoutChannelsSaysSo() {
        channelsOnTv = emptyList()
        snapshot = LibrarySnapshot(apps, listOf(appRow))
        settings(SettingsRoute("channels"))
        compose.onNodeWithText("No app on this TV publishes channels right now.").assertExists()
    }

    @Test fun applicationsPageLeadsToChannelsAndLabelsChannelRows() {
        settings(SettingsRoute("rows"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("TV channels"))
        compose.onNodeWithText("TV channels").assertExists()
        compose.onNodeWithText("TV channel · Select to move · Hold/Right to edit").assertExists()
    }

    @Test fun channelRowEditorKeepsRowControlsButHasNoAppList() {
        settings(SettingsRoute("row", rowId = 2))
        compose.onNodeWithText("Row name").assertExists()
        compose.onNodeWithText("Show on home screen").assertExists()
        compose.onNodeWithText("Move row up").assertExists()
        compose.onNodeWithText("Remove channel").assertExists()
        compose.onNodeWithText("App order").assertDoesNotExist()
        compose.onNodeWithText("App 0").assertDoesNotExist()
    }
}
