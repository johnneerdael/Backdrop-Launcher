package nl.neerdael.backdrop_launcher.media

import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import nl.neerdael.backdrop_launcher.core.ResumeItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI

internal interface WatchNextProvider {
    fun hasPermission(): Boolean
    /** Throw on transient failure; an empty list means the provider successfully returned no items. */
    fun query(): List<JSONObject>
    fun contentImage(uri: String, maxBytes: Int): ByteArray
}

internal class WatchNextFeed(private val prefs: PreferenceAccess, private val provider: WatchNextProvider,
    private val disk: MediaDisk, private val transport: MediaTransport = UrlMediaTransport) {
    private var raw: List<JSONObject> = emptyList()
    private var restored = false
    private var selected: Long? = null
    private val images = linkedMapOf<String, String>()
    private val failed = mutableSetOf<String>()
    private var protectedCandidates = emptyList<List<String>>()
    private val protectedUris: Set<String> get() = protectedCandidates.mapNotNull { alternatives ->
        alternatives.firstOrNull { it in images }
    }.toSet()

    fun permission(): Boolean = runCatching { provider.hasPermission() }.getOrDefault(false)

    @Synchronized fun restore(): List<ResumeItem> {
        if (!prefs.bool("show_watch_next_section")) return emptyList()
        if (!restored) {
            restored = true
            if (!permission()) { clear(); return emptyList() }
            selected = storedSelection()
            disk.readJson(NAMESPACE, "feed")?.let { saved ->
                val rows = saved.optJSONArray("items")
                raw = if (rows == null) emptyList() else (0 until minOf(rows.length(), 50)).mapNotNull { rows.optJSONObject(it) }
                if (selected == null && !saved.isNull("selected")) selected = saved.optLong("selected")
            }
            persistSelection()
        }
        val visible = filtered()
        val start = visible.indexOfFirst { it.optLong("id") == selected }.coerceAtLeast(0)
        protectedCandidates = visible.drop(start).take(2).map(::uris)
        // Restore only from disk; startup never waits for network access.
        visible.drop(start).take(2).forEach { row ->
            for (uri in uris(row)) {
                val cached = disk.cachedImage(NAMESPACE, uri, MAX_IMAGE_BYTES) ?: continue
                images[uri] = cached
                break
            }
        }
        trimImages()
        return visible.map(::resume)
    }

    @Synchronized fun refresh(): List<ResumeItem> {
        if (Thread.currentThread().isInterrupted) return filtered().map(::resume)
        if (!prefs.bool("show_watch_next_section")) return emptyList()
        restore()
        if (!permission()) { clear(); return emptyList() }
        try {
            val queried = provider.query()
            ensureMediaActive()
            raw = queried.filter { !it.textOrNull("title").isNullOrBlank() }.take(50)
            failed.clear()
            val retained = raw.flatMap(::uris).toSet()
            images.keys.retainAll(retained)
            if (raw.none { it.optLong("id") == selected }) selected = raw.firstOrNull()?.optLong("id")
            save()
        } catch (_: SecurityException) {
            clear()
        } catch (_: Exception) {
            // Keep the last successful provider result, including Flutter's startup feed.
        }
        val visible = filtered()
        val start = visible.indexOfFirst { it.optLong("id") == selected }.coerceAtLeast(0)
        protectedCandidates = visible.drop(start).take(2).map(::uris)
        visible.drop(start).take(2).forEach { prepare(it) }
        return filtered().map(::resume)
    }

    @Synchronized fun prepare(item: ResumeItem): ResumeItem {
        if (Thread.currentThread().isInterrupted) return item
        if (!prefs.bool("show_watch_next_section")) return item
        if (!permission()) { clear(); return item.copy(image = null) }
        val row = matching(item) ?: return item
        prepare(row)
        return resume(row)
    }

    @Synchronized fun select(item: ResumeItem) {
        val row = matching(item) ?: return
        selected = row.optLong("id")
        val visible = filtered()
        val index = visible.indexOf(row).coerceAtLeast(0)
        protectedCandidates = visible.drop(index).take(2).map(::uris)
        save()
    }

    private fun matching(item: ResumeItem): JSONObject? = raw.firstOrNull {
        if (item.id != 0L) it.optLong("id") == item.id
        else it.textOrNull("title") == item.title && it.textOrNull("packageName") == item.packageName &&
            it.textOrNull("intentUri") == item.intentUri
    }

    private fun filtered(): List<JSONObject> {
        val excluded = prefs.strings("watch_next_excluded_packages").toSet()
        val hideWithoutImages = prefs.bool("watch_next_hide_programs_without_images")
        val maxItems = prefs.int("watch_next_max_items").let { if (it > 0) it.coerceAtMost(50) else 50 }
        return raw.filter { it.textOrNull("packageName") !in excluded &&
            (!hideWithoutImages || uris(it).any { uri -> uri !in failed }) }.take(maxItems)
    }
    private fun resume(row: JSONObject): ResumeItem = ResumeItem(row.optString("title"), row.textOrNull("description") ?: "",
        uris(row).firstNotNullOfOrNull { images[it] } ?: uris(row).firstOrNull { it !in failed },
        progress(row), row.textOrNull("intentUri"), row.textOrNull("packageName"), row.optLong("id"), row.textOrNull("contentId"))

    private fun prepare(row: JSONObject) {
        for (uri in uris(row)) {
            if (Thread.currentThread().isInterrupted) return
            if (uri in failed) continue
            if (images[uri] != null) {
                val file = disk.file(NAMESPACE, uri)
                if (file.isFile && file.length() in 1..MAX_IMAGE_BYTES.toLong()) { trimImages(); return }
                images.remove(uri)
            }
            try {
                disk.cachedImage(NAMESPACE, uri, MAX_IMAGE_BYTES)?.let { images[uri] = it; trimImages(); return }
                val parsed = URI(uri)
                val bytes = if (parsed.scheme == "content") provider.contentImage(uri, MAX_IMAGE_BYTES)
                    else {
                        require(parsed.scheme in setOf("https", "http") && parsed.host != null && parsed.userInfo == null) { "Unsupported poster URI" }
                        val response = transport.execute(MediaRequest(uri, maxBytes = MAX_IMAGE_BYTES, timeoutMillis = 10_000, followImageRedirects = true))
                        if (response.status != 200) throw IOException("Poster download failed")
                        response.bytes
                    }
                require(bytes.size in 1..MAX_IMAGE_BYTES) { "Invalid poster size" }
                ensureMediaActive()
                val path = disk.writeImage(NAMESPACE, uri, bytes) ?: throw IOException("Invalid poster image")
                images[uri] = path
                trimImages()
                return
            } catch (_: Exception) { failed.add(uri) }
        }
    }
    private fun trimImages() {
        fun size(): Long = images.keys.sumOf { disk.file(NAMESPACE, it).length() }
        while (images.size > 12 || size() > 8 * 1024 * 1024) {
            val victim = images.keys.firstOrNull { it !in protectedUris } ?: break
            images.remove(victim)
        }
        disk.prune(NAMESPACE, images.keys + "feed")
    }
    private fun save() {
        disk.writeJson(NAMESPACE, "feed", JSONObject().put("items", JSONArray(raw)).put("selected", selected ?: JSONObject.NULL))
        persistSelection()
    }
    private fun storedSelection(): Long? = listOf(SELECTED_KEY, "native_watch_next_selected_id",
        "continue_watching_selected_program_id").firstNotNullOfOrNull { key ->
        prefs.long(key, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }
    }
    private fun persistSelection() {
        runCatching { selected?.let { if (prefs.long(SELECTED_KEY, Long.MIN_VALUE) != it) prefs.putLong(SELECTED_KEY, it) } }
    }
    private fun clear() {
        raw = emptyList(); images.clear(); failed.clear(); selected = null
        runCatching { prefs.remove(SELECTED_KEY) }
        disk.prune(NAMESPACE, emptySet())
    }
    companion object {
        const val NAMESPACE = "continue_watching"
        const val SELECTED_KEY = "native.watch_next_selected_id"
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        fun progress(row: JSONObject): Int {
            if (!row.isNull("playbackPosition")) {
                val duration = row.optLong("duration")
                if (duration <= 0) return 0
                return (row.optLong("playbackPosition").coerceAtLeast(0).toDouble() * 100 / duration).toInt().coerceIn(0, 100)
            }
            return row.optInt("progressPercent").coerceIn(0, 100)
        }
        fun uris(row: JSONObject): List<String> = listOfNotNull(row.textOrNull("backdropUri"), row.textOrNull("posterUri")).distinct()
    }
}
