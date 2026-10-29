package nl.neerdael.backdrop_launcher.nativebenchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import android.view.View
import java.lang.ref.WeakReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.WeakHashMap

/** Visible images own their bitmaps; the small LRU only retains offscreen card artwork. */
class ArtworkLoader(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val decoder = ArtworkDecoder(context)
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
        val epoch = decoder.epoch
        val key = binding.key
        cache.get(key)?.let { view.setImageBitmap(it); return }
        binding.loading = true
        val (batch, start) = requests.join(key, Target(WeakReference(view), token))
        if (!start) return
        try {
            workers.execute {
                val bitmap = try { decoder.decode(binding.path, binding.width, binding.height, epoch) } catch (e: Exception) {
                    if (decoder.epoch == epoch) Log.w("BackdropBenchmark", "Artwork unavailable", e)
                    null
                }
                main.post {
                    val targets = requests.complete(batch)
                    for (target in targets) {
                        val image = target.view.get() ?: continue
                        val current = bindings[image] ?: continue
                        if (current.token !== target.token) continue
                        current.loading = false
                        if (active && decoder.epoch == epoch && image.isAttachedToWindow && image.isShown && bitmap != null) {
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

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (active) {
            bindings.keys.toList().forEach(::requestVisible)
            return
        }
        decoder.cancel()
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
