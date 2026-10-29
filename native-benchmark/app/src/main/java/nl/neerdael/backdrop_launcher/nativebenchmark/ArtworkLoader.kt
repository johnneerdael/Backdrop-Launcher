package nl.neerdael.backdrop_launcher.nativebenchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.CancellationSignal
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import android.view.View
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.WeakHashMap

/** Visible images own their bitmaps; the small LRU only retains offscreen card artwork. */
class ArtworkLoader(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val activeRequests = java.util.Collections.newSetFromMap(ConcurrentHashMap<CancellationSignal, Boolean>())
    private val workers = ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(32))
    private class Binding(val path: String, val width: Int, val height: Int, val retain: Boolean) {
        var token = Any()
        var loading = false
        val key get() = "$path@$width:$height"
    }
    private data class Target(val view: WeakReference<ImageView>, val token: Any)
    private val bindings = WeakHashMap<ImageView, Binding>()
    private val requests = ImageRequests<Target>()
    private var active = true
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun load(view: ImageView, path: String, width: Int, height: Int, retain: Boolean = true) {
        val previous = bindings[view]
        if (previous?.key == "$path@$width:$height" && (view.drawable != null || previous.loading)) return
        if (previous == null) view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { requestVisible(v as ImageView) }
            override fun onViewDetachedFromWindow(v: View) {
                bindings[v]?.let { it.token = Any(); it.loading = false }
                (v as ImageView).setImageDrawable(null)
            }
        })
        bindings[view] = Binding(path, width, height, retain)
        requestVisible(view)
    }

    private fun requestVisible(view: ImageView) {
        view.post {
            if (active && view.isAttachedToWindow && view.isShown && view.getLocalVisibleRect(Rect())) {
                bindings[view]?.let { enqueue(view, it) }
            }
        }
    }

    private fun enqueue(view: ImageView, binding: Binding) {
        if (!active || binding.loading || view.drawable != null) return
        val token = binding.token
        val epoch = generation.get()
        val key = binding.key
        cache.get(key)?.let { view.setImageBitmap(it); return }
        binding.loading = true
        val (batch, start) = requests.join(key, Target(WeakReference(view), token))
        if (!start) return
        try {
            workers.execute {
                val bitmap = try { decode(binding.path, binding.width, binding.height, epoch) } catch (e: Exception) {
                    if (generation.get() == epoch) Log.w("BackdropBenchmark", "Artwork unavailable", e)
                    null
                }
                main.post {
                    val targets = requests.complete(batch)
                    for (target in targets) {
                        val image = target.view.get() ?: continue
                        val current = bindings[image] ?: continue
                        if (current.token !== target.token) continue
                        current.loading = false
                        if (active && generation.get() == epoch && image.isAttachedToWindow && image.isShown && bitmap != null) {
                            if (current.retain) cache.put(key, bitmap)
                            image.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            for (target in requests.complete(batch)) {
                target.view.get()?.let { bindings[it]?.loading = false }
            }
        }
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

    private fun decode(path: String, width: Int, height: Int, epoch: Int): Bitmap? {
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

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (active) {
            bindings.keys.toList().forEach(::requestVisible)
            return
        }
        generation.incrementAndGet()
        activeRequests.forEach { it.cancel() }
        workers.queue.clear()
        requests.clear()
        bindings.entries.toList().forEach { (view, binding) ->
            binding.token = Any(); binding.loading = false
            view.setImageDrawable(null)
        }
    }

    fun invalidate() {
        val resume = active
        setActive(false)
        cache.evictAll()
        if (resume) setActive(true)
    }

    fun close() { setActive(false); cache.evictAll(); bindings.clear(); workers.shutdownNow() }
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
