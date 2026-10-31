package nl.neerdael.backdrop_launcher.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class TraktQrTest {
    @Test fun modulesDecodeBackToTheActivationUrl() {
        val url = "https://auth.trakt.tv/activate/ABCD1234"
        val modules = qrModules(url)
        val cell = 4
        val size = (modules.size + 8) * cell
        val pixels = IntArray(size * size) { index ->
            val x = (index % size) / cell - 4
            val y = (index / size) / cell - 4
            if (modules.getOrNull(y)?.getOrNull(x) == true) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        assertEquals(url, QRCodeReader().decode(image).text)
    }
}
