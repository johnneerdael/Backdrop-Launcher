package nl.neerdael.backdrop_launcher.media

import android.content.Context
import nl.neerdael.backdrop_launcher.core.Backdrop
import nl.neerdael.backdrop_launcher.core.DeviceAuthorization
import nl.neerdael.backdrop_launcher.core.MediaAccess
import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import nl.neerdael.backdrop_launcher.core.ResumeItem
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Blocking service. Lifecycle scheduling belongs to the controller, never this object. */
class NativeMedia internal constructor(private val prefs: PreferenceAccess, private val trakt: TraktClient,
    private val disk: MediaDisk, private val feed: WatchNextFeed,
    private val transport: MediaTransport = UrlMediaTransport,
    private val now: () -> Long = System::currentTimeMillis,
    private val imageValidator: (ByteArray) -> Boolean = ::validMediaImage) : MediaAccess {
    constructor(context: Context, prefs: PreferenceAccess, clientId: String, tmdbApiKey: String, redirectUri: String) : this(
        prefs, TraktClient(prefs, clientId, tmdbApiKey, redirectUri), MediaDisk(context.cacheDir),
        WatchNextFeed(prefs, AndroidWatchNextProvider(context.applicationContext), MediaDisk(context.cacheDir)))

    private val generation = AtomicLong()
    private val wallpaperLock = Any()
    private var current: Backdrop? = null
    private var bundledIndex = -1
    private var candidates: List<Candidate> = emptyList()
    private var loadedAt = 0L
    private var catalogSignature = ""
    private var lastCandidate: String? = null
    override val configured get() = trakt.configured
    override val connected get() = trakt.connected
    override fun beginAuthorization() = trakt.begin()
    override fun pollAuthorization(auth: DeviceAuthorization) = trakt.poll(auth)
    override fun cancelAuthorization() = trakt.cancel()
    override fun disconnect() { generation.incrementAndGet(); trakt.disconnect() }
    override fun catalogs() = trakt.catalogs()
    override fun refreshPopularLists() = trakt.popular()
    override fun selectedCatalogs() = trakt.selected()
    override fun selectCatalogs(keys: List<String>) { generation.incrementAndGet(); trakt.select(keys) }
    override fun checkWatchNextPermission() = feed.permission()
    override fun watchNext() = feed.refresh()
    override fun restoredWatchNext() = feed.restore()
    override fun prepareWatchNextImage(item: ResumeItem) = feed.prepare(item)
    override fun selectWatchNext(item: ResumeItem) = feed.select(item)

    override fun restoredBackdrop(): Backdrop = synchronized(wallpaperLock) {
        current?.let { return@synchronized it }
        val saved = disk.readJson("wallpaper", "last")
        val asset = saved?.textOrNull("asset")
        if (asset in BUNDLED_BACKDROPS) {
            bundledIndex = BUNDLED_BACKDROPS.indexOf(asset)
            return@synchronized Backdrop(asset!!).also { current = it }
        }
        if (useTrakt()) {
            val name = saved?.textOrNull("file")
            if (name != null && SAFE_BACKDROP.matches(name)) {
                val file = File(disk.root, "trakt_backdrops/$name")
                if (disk.isImage(file, MAX_BACKDROP_BYTES)) return@synchronized Backdrop(disk.relative(file), saved.textOrNull("title")).also { current = it }
            }
            val available = File(disk.root, "trakt_backdrops").listFiles()?.filter { SAFE_BACKDROP.matches(it.name) }
                ?.sortedByDescending { it.lastModified() }?.take(3).orEmpty()
            available.firstOrNull { disk.isImage(it, MAX_BACKDROP_BYTES) }?.let {
                return@synchronized show(Backdrop(disk.relative(it)))
            }
        }
        showBundled()
    }

    override fun chooseBundledBackdrop(asset: String): Backdrop {
        require(asset in BUNDLED_BACKDROPS) { "Choose an included backdrop." }
        generation.incrementAndGet()
        return synchronized(wallpaperLock) {
            bundledIndex = BUNDLED_BACKDROPS.indexOf(asset)
            show(Backdrop(asset))
        }
    }

    override fun nextBackdrop(): Backdrop = synchronized(wallpaperLock) {
        if (Thread.currentThread().isInterrupted) return@synchronized current ?: Backdrop(BUNDLED_BACKDROPS[0])
        if (!useTrakt()) return@synchronized showBundled()
        val stamp = generation.get()
        try {
            val signature = trakt.selected().joinToString("|")
            ensureMediaActive()
            if (loadedAt == 0L || now() - loadedAt >= 6 * 60 * 60 * 1000 || signature != catalogSignature) {
                val loaded = trakt.candidates()
                if (stamp != generation.get() || !useTrakt()) return@synchronized current ?: restoredBackdrop()
                candidates = loaded
                loadedAt = now()
                catalogSignature = signature
            }
            val choices = candidates.shuffled().sortedBy { if (it.key == lastCandidate) 1 else 0 }
            for (candidate in choices.take(10)) {
                ensureMediaActive()
                val url = trakt.backdrop(candidate) ?: continue
                ensureMediaActive()
                if (stamp != generation.get() || !useTrakt()) return@synchronized current ?: restoredBackdrop()
                val file = File(disk.root, "trakt_backdrops/${candidate.key}.jpg")
                if (!disk.isImage(file, MAX_BACKDROP_BYTES)) {
                    val response = transport.execute(MediaRequest(url, maxBytes = MAX_BACKDROP_BYTES, followImageRedirects = true))
                    check(response.status == 200 && response.header("content-type")?.startsWith("image/", true) == true &&
                        response.bytes.size <= MAX_BACKDROP_BYTES && imageValidator(response.bytes)) { "Backdrop download failed." }
                    if (stamp != generation.get() || !useTrakt()) return@synchronized current ?: restoredBackdrop()
                    check(disk.write(file, response.bytes)) { "Unable to cache backdrop." }
                }
                if (stamp != generation.get() || !useTrakt()) return@synchronized current ?: restoredBackdrop()
                ensureMediaActive()
                lastCandidate = candidate.key
                val result = show(Backdrop(disk.relative(file), candidate.title))
                pruneBackdrops(file)
                return@synchronized result
            }
            showBundled()
        } catch (_: Exception) {
            // A network failure must not discard a usable restored image.
            if (Thread.currentThread().isInterrupted) current ?: Backdrop(BUNDLED_BACKDROPS[0])
            else if (stamp != generation.get()) current ?: restoredBackdrop()
            else current?.takeIf { useTrakt() && it.image.startsWith("cache:trakt_backdrops/") } ?: showBundled()
        }
    }

    private fun useTrakt() = prefs.bool("trakt_backdrops_enabled") && connected
    private fun showBundled(): Backdrop {
        bundledIndex = (bundledIndex + 1) % BUNDLED_BACKDROPS.size
        return show(Backdrop(BUNDLED_BACKDROPS[bundledIndex]))
    }
    private fun show(backdrop: Backdrop): Backdrop {
        current = backdrop
        val saved = JSONObject().put("title", backdrop.title ?: JSONObject.NULL)
        if (backdrop.image.startsWith("cache:trakt_backdrops/")) saved.put("file", backdrop.image.substringAfterLast('/'))
        else saved.put("asset", backdrop.image)
        disk.writeJson("wallpaper", "last", saved)
        return backdrop
    }
    private fun pruneBackdrops(protected: File) {
        protected.setLastModified(now())
        protected.parentFile?.listFiles()?.filter { it != protected && SAFE_BACKDROP.matches(it.name) }
            ?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
    }
    companion object {
        internal const val MAX_BACKDROP_BYTES = 8 * 1024 * 1024
        private val SAFE_BACKDROP = Regex("^[a-zA-Z0-9_-]+\\.jpg$")
        val BUNDLED_BACKDROPS: List<String> = listOf(
            "fight-club-55e2393686745.jpg", "forrest-gump-55e4e70827c69.jpg", "inception-52eb41b2ab30f.jpg",
            "interstellar-544bb9af46801.jpg", "pulp-fiction-526d50098865c.jpg", "the-dark-knight-58fa275637ae0.jpg",
            "the-godfather-505714161422a.jpg", "the-lord-of-the-rings-the-return-of-the-king-51121045c2707.jpg",
            "the-matrix-553be0c3bdeeb.jpg", "the-shawshank-redemption-50584de200d3c.jpg").map { "backdrops/$it" }
    }
}
