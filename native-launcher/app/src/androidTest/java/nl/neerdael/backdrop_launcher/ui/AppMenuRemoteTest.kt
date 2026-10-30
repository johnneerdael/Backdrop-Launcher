package nl.neerdael.backdrop_launcher.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.View
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.test.filters.SdkSuppress
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nl.neerdael.backdrop_launcher.core.*
import nl.neerdael.backdrop_launcher.data.LegacyPreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class AppMenuRemoteTest {
    @get:Rule val compose = createComposeRule()
    private val app = LauncherApp("test.one", "Example app", "1", false, false, null, "appicons/settings.png")
    private val row = LauncherRow(41, "My apps", false, 0, 0, 82, 0, listOf(app))
    private val library = object : LibraryAccess {
        override fun snapshot(refreshInstalled: Boolean) = LibrarySnapshot(listOf(app), listOf(row))
        override fun createRow(name: String) = error("unused")
        override fun renameRow(id: Long, name: String) = Unit
        override fun updateRow(id: Long, hidden: Boolean, sort: Int, height: Int) = Unit
        override fun moveRow(id: Long, delta: Int) = Unit
        override fun deleteRow(id: Long) = Unit
        override fun setRowApps(id: Long, packages: List<String>) = Unit
        override fun rowPackages(id: Long) = listOf(app.packageName)
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
    private var launches = 0
    private var playbacks = 0
    private lateinit var homeRoot: View
    private val actions = object : UiActions {
        override val library get() = this@AppMenuRemoteTest.library
        override fun refresh() = Unit
        override fun launch(packageName: String) { launches++ }
        override fun play(item: ResumeItem) { playbacks++ }
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
    private fun content(withWatchNext: Boolean = false, hero: Boolean = false) {
        compose.setContent {
            val context = LocalContext.current
            val view = LocalView.current
            SideEffect { homeRoot = view.rootView }
            val prefs = remember { LegacyPreferences(context) }
            val artwork = remember { ComposeArtwork(context) }
            val selection = remember { HomeSelection() }
            DisposableEffect(artwork) { artwork.setActive(true); onDispose { artwork.close() } }
            val scene = Scene("Test", media.restoredBackdrop().image,
                listOf(AppRow(row.name, listOf(AppTile(app.packageName, app.label, app.artwork)), row.id, row.height)),
                if (withWatchNext) listOf(ResumeItem("Episode fixture", "Resume fixture", "appicons/settings.png", 35,
                    "intent://fixture#Intent;scheme=test;package=test.player;end", "test.player", 17)) else emptyList(),
                hero, true, -1, .1f, false, false)
            LauncherScreen(HomeData(scene, ""), library.snapshot(), prefs, media, updates, actions, selection, artwork, true, 0)
        }
        compose.onNodeWithContentDescription(app.label).assertIsFocused()
    }
    private fun key(downTime: Long, action: Int, repeats: Int = 0, code: Int = KeyEvent.KEYCODE_DPAD_CENTER, originalWindow: Boolean = false) {
        val event = KeyEvent(downTime, SystemClock.uptimeMillis(), action, code,
            repeats, 0, -1, 97, if (repeats > 0) KeyEvent.FLAG_LONG_PRESS else 0, InputDevice.SOURCE_DPAD)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = if (originalWindow) homeRoot else WindowInspector.getGlobalWindowViews().last { it.isShown && it.hasFocus() }
            root.dispatchKeyEvent(event)
        }
        compose.waitForIdle()
    }
    private fun heldMenuGesture(code: Int, originalWindow: Boolean = false, withWatchNext: Boolean = false, hero: Boolean = false) {
        content(withWatchNext, hero)
        val down = SystemClock.uptimeMillis()
        key(down, KeyEvent.ACTION_DOWN, code = code)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithText("Open").assertExists()
        // Real remotes continue sending DOWN repeats after focus moves into the dialog.
        key(down, KeyEvent.ACTION_DOWN, 1, code, originalWindow)
        key(down, KeyEvent.ACTION_DOWN, 2, code, originalWindow)
        key(down, KeyEvent.ACTION_UP, code = code, originalWindow = originalWindow)
        compose.runOnIdle {
            assertEquals("Releasing the menu-opening hold must never start Continue Watching", 0, playbacks)
            assertEquals("Releasing the menu-opening hold must not launch the app", 0, launches)
        }
        compose.onNodeWithText("Choose packaged icon / banner").assertExists()
        compose.onNodeWithText("Reorder in My apps").assertExists()
        compose.onNodeWithText("Hide from home screen").assertExists()
        // A fresh, intentional Select must still activate the focused Open action.
        val fresh = SystemClock.uptimeMillis()
        key(fresh, KeyEvent.ACTION_DOWN, code = code)
        key(fresh, KeyEvent.ACTION_UP, code = code)
        compose.runOnIdle { assertEquals(1, launches); assertEquals(0, playbacks) }
        compose.onNodeWithText("Open").assertDoesNotExist()
    }
    @Test fun heldSelectKeepsTheAppActionsMenuOpenThroughRepeatsAndRelease() =
        heldMenuGesture(KeyEvent.KEYCODE_DPAD_CENTER)

    @Test fun heldEnterKeepsTheAppActionsMenuOpenThroughRepeatsAndRelease() =
        heldMenuGesture(KeyEvent.KEYCODE_ENTER)

    @Test fun shortSelectLaunchesExactlyOnceWithoutOpeningAppActions() {
        content()
        val down = SystemClock.uptimeMillis()
        key(down, KeyEvent.ACTION_DOWN)
        key(down, KeyEvent.ACTION_UP)
        compose.runOnIdle { assertEquals(1, launches); assertEquals(0, playbacks) }
        compose.onNodeWithText("Open").assertDoesNotExist()
    }

    @Test fun repeatedSpacesStillReachAnEditableChildInsteadOfActivatingItsSurface() {
        var text by mutableStateOf("")
        var activations = 0
        compose.setContent {
            val field = remember { FocusRequester() }
            NativeSettingsSurface({ activations++ }) {
                BasicTextField(text, { text = it }, Modifier.focusRequester(field))
            }
            LaunchedEffect(field) { field.requestFocus() }
        }
        val down = SystemClock.uptimeMillis()
        key(down, KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_SPACE)
        key(down, KeyEvent.ACTION_DOWN, 1, KeyEvent.KEYCODE_SPACE)
        key(down, KeyEvent.ACTION_DOWN, 2, KeyEvent.KEYCODE_SPACE)
        key(down, KeyEvent.ACTION_UP, code = KeyEvent.KEYCODE_SPACE)
        compose.runOnIdle { assertEquals("   ", text); assertEquals(0, activations) }
    }

    @Test fun heldSelectEventsStillRoutedToHomeCannotOpenTheToolbarMenu() =
        heldMenuGesture(KeyEvent.KEYCODE_DPAD_CENTER, originalWindow = true)

    @Test fun compactContinueWatchingNeverStartsFromTheOriginalWindowOpeningHold() =
        heldMenuGesture(KeyEvent.KEYCODE_DPAD_CENTER, originalWindow = true, withWatchNext = true)

    @Test fun heroContinueWatchingNeverStartsFromTheOriginalWindowOpeningHold() =
        heldMenuGesture(KeyEvent.KEYCODE_DPAD_CENTER, originalWindow = true, withWatchNext = true, hero = true)

    @Test fun compactContinueWatchingNeverStartsFromTheDialogWindowOpeningHold() =
        heldMenuGesture(KeyEvent.KEYCODE_DPAD_CENTER, withWatchNext = true)

    @Test fun heroContinueWatchingNeverStartsFromTheDialogWindowOpeningHold() =
        heldMenuGesture(KeyEvent.KEYCODE_DPAD_CENTER, withWatchNext = true, hero = true)

}
