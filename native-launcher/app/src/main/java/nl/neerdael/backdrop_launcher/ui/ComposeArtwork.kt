package nl.neerdael.backdrop_launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import nl.neerdael.backdrop_launcher.core.ArtworkDecoder
import nl.neerdael.backdrop_launcher.core.ImageRequests
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Viewport-visible targets share bounded asynchronous work; detached targets never retain jobs. */
class ComposeArtwork(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val decoder = ArtworkDecoder(context.applicationContext)
    private val workers = ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(32))
    private class Target(val key: String, val retain: Boolean, val deliver: (Bitmap?) -> Unit)
    private val targets = mutableSetOf<Target>()
    private val requests = ImageRequests<Target>()
    private val jobs = mutableMapOf<String, DecodeJob>()
    private val pending = VisibleWorkQueue<DecodeJob> { job ->
        try { workers.execute(job); true } catch (_: RejectedExecutionException) { false }
    }
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private var active = false

    private inner class DecodeJob(val batch: ImageRequests.Batch<Target>, val path: String,
                                  val width: Int, val height: Int, val epoch: Int) : Runnable {
        val cancelled = AtomicBoolean()
        override fun run() {
            val bitmap = if (cancelled.get()) null else try { decoder.decode(path, width, height, epoch) }
                catch (failure: Exception) {
                    if (!cancelled.get() && epoch == decoder.epoch) Log.w("Backdrop", "Artwork unavailable (${failure.javaClass.simpleName})")
                    null
                }
            main.post { complete(this, bitmap) }
        }
    }

    fun observe(path: String, width: Int, height: Int, retain: Boolean, deliver: (Bitmap?) -> Unit): () -> Unit {
        if (!active) { deliver(null); return {} }
        val key = "$path@$width:$height"
        val target = Target(key, retain, deliver)
        targets.add(target)
        val cached = cache.get(key)
        if (cached != null) deliver(cached)
        else {
            val (batch, start) = requests.join(key, target)
            if (start) {
                val job = DecodeJob(batch, path, width, height, decoder.epoch)
                jobs[key] = job
                pending.offer(job)
            }
        }
        return {
            targets.remove(target)
            requests.remove(target)
            jobs[key]?.takeIf { it.batch.targets.isEmpty() }?.let { job ->
                job.cancelled.set(true)
                pending.cancel(job)
                workers.remove(job)
                jobs.remove(key)
                requests.complete(job.batch)
                if (active) pending.drain()
            }
            deliver(null)
        }
    }

    private fun complete(job: DecodeJob, bitmap: Bitmap?) {
        if (jobs[job.batch.key] === job) jobs.remove(job.batch.key)
        val current = requests.complete(job.batch).filter { it in targets }
        if (active && !job.cancelled.get() && job.epoch == decoder.epoch && bitmap != null) {
            if (current.any { it.retain }) cache.put(job.batch.key, bitmap)
            current.forEach { it.deliver(bitmap) }
        }
        if (active) pending.drain()
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            decoder.cancel()
            jobs.values.forEach { it.cancelled.set(true) }
            jobs.clear()
            workers.queue.clear()
            pending.clear()
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
