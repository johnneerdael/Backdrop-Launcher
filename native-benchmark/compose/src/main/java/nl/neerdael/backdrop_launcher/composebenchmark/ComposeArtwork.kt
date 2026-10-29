package nl.neerdael.backdrop_launcher.composebenchmark

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import nl.neerdael.backdrop_launcher.nativebenchmark.ArtworkDecoder
import nl.neerdael.backdrop_launcher.nativebenchmark.ImageRequests
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Only viewport-visible composables subscribe; disposal removes their delivery targets. */
class ComposeArtwork(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val decoder = ArtworkDecoder(context.applicationContext)
    private val workers = ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(32))
    private class Target(val retain: Boolean, val deliver: (Bitmap?) -> Unit)
    private val targets = mutableSetOf<Target>()
    private val requests = ImageRequests<Target>()
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private var active = false

    fun observe(path: String, width: Int, height: Int, retain: Boolean, deliver: (Bitmap?) -> Unit): () -> Unit {
        if (!active) { deliver(null); return {} }
        val key = "$path@$width:$height"
        val target = Target(retain, deliver)
        targets.add(target)
        val cached = cache.get(key)
        if (cached != null) deliver(cached)
        else {
            val epoch = decoder.epoch
            val (batch, start) = requests.join(key, target)
            if (start) {
                try {
                    workers.execute {
                        // No main-thread mutable request state is read on a decoder worker.
                        val bitmap = try { decoder.decode(path, width, height, epoch) } catch (e: Exception) {
                            if (epoch == decoder.epoch) Log.w("BackdropCompose", "Artwork unavailable", e)
                            null
                        }
                        main.post {
                            val current = requests.complete(batch).filter { it in targets }
                            if (active && epoch == decoder.epoch && bitmap != null) {
                                if (current.any { it.retain }) cache.put(key, bitmap)
                                current.forEach { it.deliver(bitmap) }
                            }
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    requests.complete(batch)
                }
            }
        }
        return { targets.remove(target); requests.remove(target); deliver(null) }
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            decoder.cancel()
            workers.queue.clear()
            requests.clear()
            targets.toList().forEach { it.deliver(null) }
            targets.clear()
        }
    }
    fun invalidate() {
        val resume = active
        setActive(false)
        cache.evictAll()
        setActive(resume)
    }
    fun close() { setActive(false); cache.evictAll(); workers.shutdownNow() }
}
