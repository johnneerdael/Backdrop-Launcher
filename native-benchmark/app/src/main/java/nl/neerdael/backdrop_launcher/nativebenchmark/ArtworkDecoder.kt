package nl.neerdael.backdrop_launcher.nativebenchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.CancellationSignal
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Shared byte, path, cancellation and hardware-decode policy for both benchmark renderers. */
class ArtworkDecoder(private val context: Context) {
    private val generation = AtomicInteger()
    private val activeRequests = java.util.Collections.newSetFromMap(ConcurrentHashMap<CancellationSignal, Boolean>())
    val epoch get() = generation.get()
    fun cancel() {
        generation.incrementAndGet()
        activeRequests.forEach { it.cancel() }
    }
    private fun open(path: String, cancellation: CancellationSignal): InputStream {
        cancellation.throwIfCanceled()
        if (path.startsWith("https://")) {
            val connection = URL(path).openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = false
            cancellation.setOnCancelListener { connection.disconnect() }
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                error("Artwork HTTP status unavailable")
            }
            return object : java.io.FilterInputStream(connection.inputStream) {
                override fun close() { try { super.close() } finally { connection.disconnect() } }
            }
        }
        if (path.startsWith("content://")) return requireNotNull(
            context.contentResolver.openAssetFileDescriptor(Uri.parse(path), "r", cancellation)).createInputStream()
        require(!path.startsWith('/') && ':' !in path && '\\' !in path)
        val directory = requireNotNull(context.getExternalFilesDir(null)).canonicalFile
        val file = File(directory, path).canonicalFile
        require(file.path.startsWith(directory.path + File.separator)) { "Artwork outside scene directory" }
        return if (file.isFile) file.inputStream() else context.assets.open(path)
    }

    fun decode(path: String, width: Int, height: Int, epoch: Int): Bitmap? {
        if (epoch != generation.get()) throw IOException("Artwork request cancelled")
        if (path.startsWith("package:")) {
            val drawable = context.packageManager.getApplicationIcon(path.removePrefix("package:"))
            val bitmap = Bitmap.createBitmap(width.coerceAtMost(480), height.coerceAtMost(270), Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val hardware = bitmap.copy(Bitmap.Config.HARDWARE, false)
                if (hardware != null) { bitmap.recycle(); return hardware }
            }
            return bitmap
        }
        // Freeze both decode passes to the same bytes without holding a large encoded buffer in RAM.
        val snapshot = File.createTempFile("artwork-", ".tmp", context.cacheDir)
        val cancellation = CancellationSignal()
        activeRequests.add(cancellation)
        try {
            if (epoch != generation.get()) cancellation.cancel()
            open(path, cancellation).use { input -> snapshot.outputStream().use { output ->
                copyArtwork(input, output, cancelled = { epoch != generation.get() })
            } }
            if (epoch != generation.get()) throw IOException("Artwork request cancelled")
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(snapshot)) { decoder, info, _ ->
                    val size = decodeSize(info.size.width, info.size.height, width, height)
                    decoder.setTargetSize(size.width, size.height)
                    decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                    if (epoch != generation.get()) throw IOException("Artwork request cancelled")
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(snapshot.path, bounds)
            val size = decodeSize(bounds.outWidth, bounds.outHeight, width, height)
            if (epoch != generation.get()) throw IOException("Artwork request cancelled")
            val options = BitmapFactory.Options().apply { inSampleSize = size.sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = BitmapFactory.decodeFile(snapshot.path, options) ?: return null
            if (decoded.width == size.width && decoded.height == size.height) return decoded
            return Bitmap.createScaledBitmap(decoded, size.width, size.height, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } finally {
            activeRequests.remove(cancellation)
            cancellation.setOnCancelListener(null)
            snapshot.delete()
        }
    }

}

fun copyArtwork(input: InputStream, output: OutputStream, maxBytes: Int = 16 * 1024 * 1024,
                cancelled: () -> Boolean = { false }) {
    val buffer = ByteArray(8192)
    var total = 0
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
    while (true) {
        if (Thread.currentThread().isInterrupted || cancelled()) throw IOException("Artwork request cancelled")
        if (System.nanoTime() > deadline) throw IOException("Artwork fetch deadline exceeded")
        val count = input.read(buffer)
        if (count < 0) return
        total += count
        if (total > maxBytes) throw IOException("Encoded artwork exceeds limit")
        output.write(buffer, 0, count)
    }
}
