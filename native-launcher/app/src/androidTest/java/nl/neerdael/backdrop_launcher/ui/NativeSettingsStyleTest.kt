package nl.neerdael.backdrop_launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reference: measured native TV settings contract, not TV Material card defaults. */
@RunWith(AndroidJUnit4::class)
class NativeSettingsStyleTest {
    @get:Rule val compose = createComposeRule()
    private fun content() {
        compose.setContent {
            val context = LocalContext.current
            val artwork = remember { ComposeArtwork(context) }
            DisposableEffect(artwork) { onDispose { artwork.close() } }
            MaterialTheme {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, color = Color.Magenta)) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Text("Home typography", Modifier.testTag("home-type"))
                        Box(Modifier.align(Alignment.CenterEnd).testTag("settings-host")) {
                            SettingsPanel("Settings", listOf(
                                SettingsEntry("network", "Network", subtitle = "Connected") {},
                                SettingsEntry("enabled", "Enabled", checked = true, control = SettingsControl.SWITCH) {},
                                SettingsEntry("long-value", "Row name", value = "Long row name ".repeat(16)) {},
                            ), artwork, true, 0, null, null)
                        }
                    }
                }
            }
        }
    }
    @Test fun nativePanelWidthAndFlatSurfaceMatchMeasuredSystemSettings() {
        content()
        val host = compose.onNodeWithTag("settings-host")
        host.assertWidthIsEqualTo(360.dp)
        val pixels = host.captureToImage().toPixelMap()
        assertEquals(Color(0xff1b1c1e), pixels[4, 4])
        assertEquals(Color(0xff202124), pixels[4, pixels.height - 4])
        val row = compose.onNodeWithContentDescription("Network, Connected")
        row.assertWidthIsEqualTo(360.dp).assertIsFocused()
        val image = row.captureToImage().toPixelMap()
        // A native focused row fills its corners; no rounded card, border or scale.
        assertEquals(Color(0xff2e2f32), image[2, 2])
        assertEquals(Color(0xff2e2f32), image[image.width - 3, image.height - 3])
    }
    private fun textStyle(text: String): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style
    }
    @Test fun nativeTypographyIsIsolatedFromHomeTypography() {
        content()
        assertEquals(20.sp, textStyle("Settings").fontSize)
        assertEquals(14.sp, textStyle("Network").fontSize)
        assertEquals(12.sp, textStyle("Connected").fontSize)
        assertEquals(FontFamily.SansSerif, textStyle("Network").fontFamily)
        assertNull(textStyle("Network").shadow)
        assertEquals(30.sp, textStyle("Home typography").fontSize)
        assertEquals(FontFamily.Serif, textStyle("Home typography").fontFamily)
    }
    @Test fun nativeSwitchUsesBlueAndLongValuesKeepTheirLabels() {
        content()
        val toggle = compose.onNodeWithContentDescription("Enabled")
        toggle.assertIsOn().assertHasClickAction()
        val pixels = toggle.captureToImage().toPixelMap()
        val density = pixels.width / 360f
        assertEquals(Color(0xff51aaff), pixels[(pixels.width - 69.625f * density).toInt(), pixels.height / 2])
        compose.onNodeWithText("Row name", useUnmergedTree = true).assertIsDisplayed()
        val label = compose.onNodeWithText("Row name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Long trailing values must leave room for the row label", label.width >= 60 * density)
    }

}
