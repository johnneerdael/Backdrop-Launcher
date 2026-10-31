package nl.neerdael.backdrop_launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import androidx.tv.material3.MaterialTheme
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import nl.neerdael.backdrop_launcher.core.DeviceAuthorization
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reference: lib/widgets/settings/trakt_connect_dialog.dart, inside the measured settings panel. */
@RunWith(AndroidJUnit4::class)
class TraktActivationTest {
    @get:Rule val compose = createComposeRule()
    private val auth = DeviceAuthorization("device", "ABCD1234", "https://trakt.tv/activate", Long.MAX_VALUE, 5)

    private fun content(auth: DeviceAuthorization? = this.auth, status: String = "Waiting for approval…") {
        compose.setContent {
            val context = LocalContext.current
            val artwork = remember { ComposeArtwork(context) }
            DisposableEffect(artwork) { onDispose { artwork.close() } }
            MaterialTheme {
                Box(Modifier.testTag("host")) {
                    SettingsPanel("Connect Trakt", listOf(SettingsEntry("cancel", "Cancel device login") {}), artwork, true, 0, null, null,
                        lead = { TraktActivation(auth, status) })
                }
            }
        }
    }
    private fun textStyle(text: String): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style
    }

    @Test fun renderedQrOpensActivationPageWithCodeFilledIn() {
        content()
        val bitmap = compose.onNodeWithContentDescription("QR code to activate Trakt").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
        assertEquals("https://auth.trakt.tv/activate/ABCD1234", QRCodeReader().decode(image).text)
    }

    @Test fun copyUsesOnlySettingsTypography() {
        content()
        assertEquals(14.sp, textStyle("Scan with your phone to approve").fontSize)
        assertEquals(12.sp, textStyle("Or open auth.trakt.tv/activate and enter").fontSize)
        assertEquals(20.sp, textStyle("ABCD1234").fontSize)
        assertEquals(12.sp, textStyle("Waiting for approval…").fontSize)
        assertEquals(FontFamily.SansSerif, textStyle("Scan with your phone to approve").fontFamily)
    }

    @Test fun activationReadsTopDownAndCancelStaysVisibleInThePanel() {
        content()
        compose.onNodeWithTag("host").assertWidthIsEqualTo(360.dp)
        val qr = compose.onNodeWithContentDescription("QR code to activate Trakt").getUnclippedBoundsInRoot()
        val code = compose.onNodeWithText("ABCD1234", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cancel = compose.onNodeWithContentDescription("Cancel device login").assertIsDisplayed().assertIsFocused().getUnclippedBoundsInRoot()
        assertTrue(qr.bottom <= code.top && code.bottom <= cancel.top)
        assertTrue(qr.right <= 360.dp)
    }

    @Test fun finishedActivationShowsOnlyItsStatus() {
        content(auth = null, status = "Activation code expired. Connect again.")
        compose.onNodeWithContentDescription("QR code to activate Trakt").assertDoesNotExist()
        assertEquals(14.sp, textStyle("Activation code expired. Connect again.").fontSize)
    }
}
