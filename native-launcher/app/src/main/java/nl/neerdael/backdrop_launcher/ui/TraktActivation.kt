package nl.neerdael.backdrop_launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import nl.neerdael.backdrop_launcher.core.DeviceAuthorization
import kotlin.math.floor

private const val QR_QUIET_MODULES = 4

internal fun qrModules(text: String): Array<BooleanArray> {
    val matrix = Encoder.encode(text, ErrorCorrectionLevel.M).matrix
    return Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y).toInt() == 1 } }
}

/** Whole-pixel modules stay sharp for phone cameras; any remainder widens the quiet zone. */
@Composable
private fun QrCode(text: String, modifier: Modifier) {
    val modules = remember(text) { qrModules(text) }
    Canvas(modifier, contentDescription = "QR code to activate Trakt") {
        drawRect(Color.White)
        val cell = floor(size.width / (modules.size + QR_QUIET_MODULES * 2))
        val inset = floor((size.width - cell * modules.size) / 2)
        modules.forEachIndexed { y, row -> row.forEachIndexed { x, dark ->
            if (dark) drawRect(Color.Black, Offset(inset + x * cell, inset + y * cell), Size(cell, cell))
        } }
    }
}

/** Device activation: scanning opens Trakt with the code filled in, and the code remains as a fallback. */
@Composable
internal fun TraktActivation(auth: DeviceAuthorization?, status: String) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 56.dp, top = 16.dp, bottom = 12.dp)) {
        if (auth == null) Text(status, style = NativeSettingsStyle.primaryStyle)
        else {
            Text("Scan with your phone to approve", style = NativeSettingsStyle.primaryStyle)
            QrCode(auth.activationUrl, Modifier.padding(vertical = 12.dp).size(180.dp))
            Text("Or open auth.trakt.tv/activate and enter", style = NativeSettingsStyle.summaryStyle)
            Text(auth.userCode, Modifier.padding(top = 2.dp), style = NativeSettingsStyle.titleStyle)
            Text(status, Modifier.padding(top = 8.dp), style = NativeSettingsStyle.summaryStyle)
        }
    }
}
