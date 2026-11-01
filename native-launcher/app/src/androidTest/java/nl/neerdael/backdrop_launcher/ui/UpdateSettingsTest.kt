package nl.neerdael.backdrop_launcher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class UpdateSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val prefs = LegacyPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
    private val file = File("/data/local/tmp/update.apk")
    private val waiting = PendingUpdate(UpdateInfo("1.4.12", 4200, "https://example.test/a.apk", "New things"), file)
    private var ready: PendingUpdate? = waiting
    private var updatesOn = true
    private var latest: UpdateInfo? = null
    private val installs = mutableListOf<File>()
    private val library = object : LibraryAccess {
        override fun snapshot(refreshInstalled: Boolean) = LibrarySnapshot(emptyList(), emptyList())
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
        override fun checkWatchNextPermission() = true
    }
    private val updates = object : UpdateAccess {
        override val enabled get() = updatesOn
        override fun check() = latest
        override fun download(update: UpdateInfo): File = file
        override fun pending() = ready
    }
    private val actions = object : UiActions {
        override val library get() = this@UpdateSettingsTest.library
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
        override fun installApk(file: File) { installs += file }
        override fun message(text: String) = Unit
    }
    @Before fun before() = prefs.remove(AUTO_UPDATE_KEY)
    @After fun after() = prefs.remove(AUTO_UPDATE_KEY)

    private fun settings(section: String) {
        compose.setContent {
            val context = LocalContext.current
            val artwork = remember { ComposeArtwork(context) }
            DisposableEffect(artwork) { artwork.setActive(true); onDispose { artwork.close() } }
            LauncherSettings(SettingsRoute(section), library.snapshot(false), prefs, media, updates, actions, artwork, true, 0, {}, { _, _ -> })
        }
        compose.waitForIdle()
    }
    private fun select(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)

    @Test fun rejectedUpdateIsAnActionOnTheMainSettingsPage() {
        settings("settings")
        compose.onNodeWithText("Install update 1.4.12").assertExists()
        select("Install update 1.4.12")
        compose.waitUntil { installs.isNotEmpty() }
        assertEquals(listOf(file), installs)
    }

    @Test fun noActionWhenNothingIsWaiting() {
        ready = null
        settings("settings")
        compose.onNodeWithText("Install update 1.4.12").assertDoesNotExist()
    }

    @Test fun updatesPageOffersTheDownloadInsteadOfFetchingItAgain() {
        latest = waiting.info
        settings("settings")
        select("Check for updates")
        compose.onNodeWithText("Install update 1.4.12", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Download 1.4.12").assertDoesNotExist()
    }

    @Test fun automaticUpdatesAreOnByDefaultAndCanBeTurnedOff() {
        settings("system")
        compose.onNodeWithText("Install updates automatically").assertExists()
        assertTrue(prefs.bool(AUTO_UPDATE_KEY, true))
        select("Install updates automatically")
        compose.waitUntil { !prefs.bool(AUTO_UPDATE_KEY, true) }
    }

    @Test fun buildsWithoutTheUpdaterHaveNoSwitch() {
        updatesOn = false
        settings("system")
        compose.onNodeWithText("Install updates automatically").assertDoesNotExist()
    }
}
