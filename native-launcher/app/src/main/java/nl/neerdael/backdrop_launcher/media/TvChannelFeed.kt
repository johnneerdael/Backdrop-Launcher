package nl.neerdael.backdrop_launcher.media

import android.media.tv.TvContract
import nl.neerdael.backdrop_launcher.core.ChannelProgram
import nl.neerdael.backdrop_launcher.core.HOME_TILE_ASPECT
import nl.neerdael.backdrop_launcher.core.ResumeItem
import nl.neerdael.backdrop_launcher.core.TvChannel
import org.json.JSONArray
import org.json.JSONObject

internal interface TvChannelProvider {
    fun hasPermission(): Boolean
    /** Throw on transient failure; an empty list means the provider successfully returned no channels. */
    fun channels(): List<JSONObject>
    fun programs(channelId: Long): List<JSONObject>
    fun contentImage(uri: String, maxBytes: Int): ByteArray
}

/** Reads only selected channels, and caches art on disk so Home never waits for the network. */
internal class TvChannelFeed(private val provider: TvChannelProvider, private val disk: MediaDisk,
    private val transport: MediaTransport = UrlMediaTransport, private val now: () -> Long = System::currentTimeMillis) {
    private data class Art(val uri: String, val aspect: Float)
    private var raw: Map<String, List<JSONObject>> = emptyMap()
    private var restored = false
    private val failed = mutableMapOf<String, Long>()

    fun permission(): Boolean = runCatching { provider.hasPermission() }.getOrDefault(false)

    fun published(): List<TvChannel> {
        if (!permission()) return emptyList()
        return try {
            provider.channels().filter(::preview).map { TvChannel(key(it), it.textOrNull("name") ?: "", it.optString("packageName"),
                it.textOrNull("appName") ?: it.optString("packageName")) }.distinctBy { it.key }
        } catch (_: SecurityException) { emptyList() }
    }

    @Synchronized fun restore(keys: Set<String>): Map<String, List<ChannelProgram>> {
        if (!restored) {
            restored = true
            if (!permission()) { clear(); return emptyMap() }
            disk.readJson(NAMESPACE, "feed")?.let { saved ->
                raw = saved.keys().asSequence().associateWith { key ->
                    saved.optJSONArray(key).let { rows -> if (rows == null) emptyList() else (0 until rows.length()).mapNotNull(rows::optJSONObject) }
                }
            }
        }
        return programs(keys)
    }

    @Synchronized fun refresh(keys: Set<String>): Map<String, List<ChannelProgram>> {
        if (Thread.currentThread().isInterrupted) return programs(keys)
        restore(keys)
        if (!permission()) { clear(); return emptyMap() }
        try {
            val queried = provider.channels().filter { preview(it) && key(it) in keys }.associate { channel ->
                ensureMediaActive()
                key(channel) to provider.programs(channel.optLong("id")).filter { !it.textOrNull("title").isNullOrBlank() && it.optLong("browsable", 1L) != 0L }.take(MAX_PROGRAMS)
            }
            ensureMediaActive()
            raw = queried
            val retained = raw.values.flatten().flatMap(::art).map { it.uri }.toSet()
            failed.keys.retainAll(retained)
            disk.writeJson(NAMESPACE, "feed", JSONObject().apply { raw.forEach { (key, rows) -> put(key, JSONArray(rows)) } })
            disk.prune(NAMESPACE, retained + "feed")
        } catch (_: SecurityException) {
            clear()
        } catch (_: Exception) {
            // Keep the last successful provider result.
        }
        return programs(keys)
    }

    /** Fetches art a channel is missing, reporting filled tiles in batches so a row fills in as it loads. */
    @Synchronized fun prepare(key: String, ready: (List<ChannelProgram>) -> Unit) {
        var changed = 0
        for (row in raw[key].orEmpty()) {
            if (Thread.currentThread().isInterrupted) return
            if (fetch(row) && ++changed % ART_BATCH == 0) ready(programs(setOf(key)).getValue(key))
        }
        if (changed % ART_BATCH != 0) ready(programs(setOf(key)).getValue(key))
    }

    private fun programs(keys: Set<String>) = raw.filterKeys { it in keys }.mapValues { (_, rows) -> rows.map(::program) }

    private fun program(row: JSONObject): ChannelProgram {
        val candidates = art(row)
        val chosen = candidates.firstOrNull { !cooling(it.uri) } ?: candidates.firstOrNull()
        return ChannelProgram(ResumeItem(row.optString("title"), "", chosen?.let { cached(it.uri) }, 0, row.textOrNull("intentUri"),
            row.textOrNull("packageName"), row.optLong("id"), row.textOrNull("contentId")), chosen?.aspect ?: HOME_TILE_ASPECT)
    }

    /** True when the tile's art changed. The first art that loads wins, so a poster beats a still. */
    private fun fetch(row: JSONObject): Boolean {
        var changed = false
        for (art in art(row)) {
            if (cached(art.uri) != null) return changed
            if (cooling(art.uri)) continue
            try {
                val bytes = programImage(art.uri, MAX_IMAGE_BYTES, transport, provider::contentImage)
                ensureMediaActive()
                if (disk.writeImage(NAMESPACE, art.uri, bytes) == null) error("Invalid poster image")
                return true
            } catch (_: Exception) {
                if (Thread.currentThread().isInterrupted) return changed
                failed[art.uri] = now()
                changed = true
            }
        }
        return changed
    }

    private fun cached(uri: String): String? = disk.file(NAMESPACE, uri).takeIf { it.isFile && it.length() in 1..MAX_IMAGE_BYTES.toLong() }?.let(disk::relative)
    private fun cooling(uri: String) = failed[uri]?.let { now() - it < RETRY_MILLIS } ?: false

    private fun clear() {
        raw = emptyMap(); failed.clear()
        disk.prune(NAMESPACE, emptySet())
    }

    companion object {
        const val NAMESPACE = "tv_channels"
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        const val MAX_PROGRAMS = 24
        const val ART_BATCH = 6
        const val RETRY_MILLIS = 15 * 60 * 1000L
        private const val POSTER_ASPECT = 2f / 3f
        /** TvContract's aspect ratio codes, in order. */
        private val ASPECTS = listOf(16f / 9f, 3f / 2f, 4f / 3f, 1f, 2f / 3f, 1000f / 1441f)

        private fun preview(channel: JSONObject) = channel.optString("type") == TvContract.Channels.TYPE_PREVIEW
        /** Row ids must outlive the provider's, which change whenever an app republishes its channels. */
        private fun key(channel: JSONObject) = "${channel.optString("packageName")}|${channel.textOrNull("internalId") ?: channel.optString("name")}"
        private fun aspect(row: JSONObject, column: String, default: Float) = if (row.isNull(column)) default else ASPECTS.getOrElse(row.optInt(column, -1)) { default }
        private fun art(row: JSONObject) = listOfNotNull(
            row.textOrNull("posterUri")?.let { Art(it, aspect(row, "posterAspect", POSTER_ASPECT)) },
            row.textOrNull("thumbnailUri")?.let { Art(it, aspect(row, "thumbnailAspect", HOME_TILE_ASPECT)) }).distinctBy { it.uri }
    }
}
