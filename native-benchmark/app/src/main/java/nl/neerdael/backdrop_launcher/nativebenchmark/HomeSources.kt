package nl.neerdael.backdrop_launcher.nativebenchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.tv.TvContract
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

/** Observe actual changes; no hidden-Home polling or repeated package enumeration. */
class HomeSources(private val context: Context, private val changed: (Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val directory = requireNotNull(context.getExternalFilesDir(null)).canonicalFile
    private var artworkPaths = emptyList<String>()
    private val artworkObservers = mutableMapOf<String, FileObserver>()
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { changed(true) }
    }
    private val programs = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { changed(false) }
    }
    @Suppress("DEPRECATION")
    private fun observer(folder: File) = object : FileObserver(folder.path,
        CLOSE_WRITE or MOVED_TO or MOVED_FROM or DELETE or CREATE) {
        override fun onEvent(event: Int, path: String?) {
            if (path == "scene.json" || path?.matches(Regex(".*\\.(png|jpe?g|webp)$", RegexOption.IGNORE_CASE)) == true ||
                (path != null && File(folder, path).isDirectory)) {
                main.post { updateArtworkObservers(); changed(true) }
            }
        }
    }
    private val files = observer(directory)
    private var providerRegistered = false

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(packages, filter)
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                context.contentResolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, programs)
                providerRegistered = true
            } catch (_: RuntimeException) { /* Missing providers/permission changes are also handled on return. */ }
        }
        files.startWatching()
    }

    private fun paths(scene: Scene) = buildList {
        add(scene.backdrop)
        scene.rows.forEach { row -> row.apps.forEach { it.artwork?.let(::add) } }
        scene.watchNext.forEach { it.image?.let(::add) }
    }.filter { ':' !in it && !it.startsWith('/') && it.split('/').none { part -> part == ".." } }.distinct()

    fun artworkStamp(scene: Scene): String =
        runCatching { localArtworkStamp(directory, paths(scene)) }.getOrDefault("invalid-artwork")

    fun watchArtwork(scene: Scene) {
        artworkPaths = paths(scene)
        updateArtworkObservers()
    }

    private fun updateArtworkObservers() {
        val desired = mutableSetOf<String>()
        for (path in artworkPaths) {
            var parent = File(directory, path).canonicalFile.parentFile
            while (parent != null && parent != directory && parent.path.startsWith(directory.path + File.separator)) {
                if (parent.isDirectory) desired.add(parent.path)
                parent = parent.parentFile
            }
        }
        // Watch ancestors first; revision validation on return covers larger scene collections too.
        val bounded = desired.sortedBy { it.length }.take(64).toSet()
        artworkObservers.keys.toList().filter { it !in bounded }.forEach { artworkObservers.remove(it)?.stopWatching() }
        for (path in bounded) if (path !in artworkObservers) {
            artworkObservers[path] = observer(File(path)).also { it.startWatching() }
        }
    }

    fun close() {
        context.unregisterReceiver(packages)
        if (providerRegistered) context.contentResolver.unregisterContentObserver(programs)
        files.stopWatching()
        artworkObservers.values.forEach { it.stopWatching() }
        artworkObservers.clear()
        main.removeCallbacksAndMessages(null)
    }
}
