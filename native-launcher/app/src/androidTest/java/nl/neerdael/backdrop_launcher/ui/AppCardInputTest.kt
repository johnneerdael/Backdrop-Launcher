package nl.neerdael.backdrop_launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCardInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteFocusAndHeldSelectDoNotLaunchTheApp() {
        var launches = 0
        var held = 0
        compose.setContent {
            val first = remember { FocusRequester() }
            MaterialTheme {
                Row(Modifier.padding(40.dp)) {
                    AppCardSurface({ launches++ }, Modifier.size(150.dp, 90.dp).testTag("first").focusRequester(first),
                        Color.DarkGray, onLongClick = { held++ }) { Text("First") }
                    AppCardSurface({}, Modifier.size(150.dp, 90.dp).testTag("second"), Color.DarkGray) { Text("Second") }
                }
            }
            LaunchedEffect(first) { withFrameNanos { }; first.requestFocus() }
        }
        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("second").assertIsFocused()
        compose.onNodeWithTag("second").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("first").performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("first").performKeyInput { keyUp(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, held); assertEquals(0, launches) }
        compose.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, held); assertEquals(1, launches) }
    }
    @Test fun busySettingsPreferenceCannotMutate() {
        var mutations = 0
        compose.setContent {
            val context = LocalContext.current
            val artwork = remember(context) { ComposeArtwork(context) }
            DisposableEffect(artwork) { onDispose { artwork.close() } }
            MaterialTheme {
                SettingsPanel("Settings", listOf(SettingsEntry("save", "Save") { mutations++ }), artwork, true, 0, "Saving…", null)
            }
        }
        compose.onNodeWithText("Settings").assertHasNoClickAction()
        compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Save").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(0, mutations) }
    }

}
