package nl.neerdael.backdrop_launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reference: the image-only picker contract from lib/widgets/app_artwork_picker.dart. */
@RunWith(AndroidJUnit4::class)
class ArtworkGridTest {
    @get:Rule val compose = createComposeRule()
    private val chosen = mutableListOf<String>()

    private fun content(count: Int = 56, busy: String? = null) {
        compose.setContent {
            val context = LocalContext.current
            val artwork = remember { ComposeArtwork(context) }
            DisposableEffect(artwork) { onDispose { artwork.close() } }
            MaterialTheme {
                Box(Modifier.testTag("host")) {
                    SettingsPanel("Choose app icon / banner", (0 until count).map { index ->
                        SettingsEntry("appicons/icon$index.png", "icon$index", artwork = "appicons/icon$index.png", localize = false) { chosen += "icon$index" }
                    }, artwork, true, 0, busy, null, artworkGrid = true)
                }
            }
        }
    }
    private fun tile(index: Int) = compose.onNodeWithContentDescription("icon$index")
    private fun press(key: Key) = compose.onNodeWithTag("host").performKeyInput { pressKey(key) }

    @Test fun tilesCarryNamesForAccessibilityButShowNoText() {
        content()
        compose.onNodeWithText("icon0").assertDoesNotExist()
        tile(0).assertExists()
    }

    @Test fun threeTilesShareEachRowInsideTheUnchangedPanelWidth() {
        content()
        compose.onNodeWithTag("host").assertWidthIsEqualTo(360.dp)
        val tiles = (0..3).map { tile(it).getUnclippedBoundsInRoot() }
        assertEquals(tiles[0].top, tiles[1].top)
        assertEquals(tiles[0].top, tiles[2].top)
        assertTrue(tiles[0].right < tiles[1].left && tiles[1].right < tiles[2].left)
        assertTrue(tiles[2].right <= 360.dp)
        assertEquals(tiles[0].left, tiles[3].left)
        assertTrue(tiles[3].top > tiles[0].bottom)
    }

    @Test fun dpadWalksThePartialLastRowAndActivatesIt() {
        content()
        tile(0).assertIsFocused()
        press(Key.DirectionRight); tile(1).assertIsFocused()
        press(Key.DirectionRight); tile(2).assertIsFocused()
        for (index in 5..53 step 3) { press(Key.DirectionDown); tile(index).assertIsFocused() }
        press(Key.DirectionDown); tile(55).assertIsFocused()
        press(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf("icon55"), chosen) }
    }

    @Test fun busyPanelBlocksActivation() {
        content(busy = "Saving artwork…")
        tile(0).assertIsNotEnabled()
        press(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(emptyList<String>(), chosen) }
    }
}
