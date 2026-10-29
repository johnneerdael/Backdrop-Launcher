package nl.neerdael.backdrop_launcher.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

data class AppTile(val packageName: String, val label: String, val artwork: String?)
data class AppRow(val name: String, val apps: List<AppTile>, val id: Long = 0, val height: Int = 110)
data class AppFocus(val row: Int, val tile: Int, val packageName: String)
data class ResumeItem(
    val title: String, val description: String, val image: String?, val progress: Int,
    val intentUri: String?, val packageName: String?, val id: Long = 0, val contentId: String? = null,
)
data class Scene(
    val name: String, val backdrop: String, val rows: List<AppRow>,
    val watchNext: List<ResumeItem>, val bigPicture: Boolean, val scrim: Boolean,
    val accent: Int, val tileOpacity: Float, val discoverApps: Boolean, val showAppNames: Boolean,
) {
    fun backdropFor(index: Int): String =
        if (bigPicture) watchNext.getOrNull(index)?.image ?: backdrop else backdrop

    fun restoreAppFocus(saved: AppFocus?): AppFocus? {
        if (saved == null) return null
        if (rows.getOrNull(saved.row)?.apps?.getOrNull(saved.tile)?.packageName == saved.packageName) return saved
        rows.forEachIndexed { row, entry ->
            val tile = entry.apps.indexOfFirst { it.packageName == saved.packageName }
            if (tile >= 0) return AppFocus(row, tile, saved.packageName)
        }
        return null
    }

    companion object {
        fun parse(text: String): Scene {
            val json = JSONObject(text)
            require(json.getInt("version") == 1) { "Unsupported scene version" }
            val rows = json.optJSONArray("rows").objects().map { row ->
                AppRow(row.getString("name"), row.optJSONArray("apps").objects().map { app ->
                    AppTile(app.getString("package"), app.getString("label"), localPath(app.optString("artwork")))
                })
            }
            val watchNext = json.optJSONArray("watchNext").objects().map { item ->
                ResumeItem(item.getString("title"), item.optString("description"),
                    localPath(item.optString("image")), item.optInt("progress").coerceIn(0, 100),
                    item.optString("intentUri").ifBlank { null }, item.optString("package").ifBlank { null })
            }
            val accent = json.optString("accent", "A78BFA").removePrefix("#").toLong(16).toInt() or (0xff shl 24)
            return Scene(json.optString("name", "Unnamed scene"),
                localPath(json.optString("backdrop", "backdrops/fight-club-55e2393686745.jpg"))!!,
                rows, watchNext, json.optBoolean("bigPicture"), json.optBoolean("scrim"),
                accent, json.optDouble("tileOpacity", 0.2).toFloat().coerceIn(0f, 1f),
                json.optBoolean("discoverApps"), json.optBoolean("showAppNames"))
        }

        private fun localPath(path: String): String? {
            if (path.isBlank()) return null
            require(!path.startsWith('/') && ':' !in path && '\\' !in path &&
                path.split('/').none { it == ".." || it.isEmpty() }) { "Artwork must be a local relative path" }
            return path
        }
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

data class DecodeSize(val width: Int, val height: Int, val sample: Int)

fun decodeSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): DecodeSize {
    require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
    require(width <= 65536 && height <= 65536 && width.toLong() * height <= 268435456) { "Source image exceeds decode limits" }
    val scale = min(1.0, min(targetWidth.coerceAtMost(3840).toDouble() / width,
        targetHeight.coerceAtMost(2160).toDouble() / height))
    val w = (width * scale).roundToInt().coerceAtLeast(1)
    val h = (height * scale).roundToInt().coerceAtLeast(1)
    var sample = 1
    while (width / (sample * 2) >= w && height / (sample * 2) >= h && sample < 32768) sample *= 2
    return DecodeSize(w, h, sample)
}
