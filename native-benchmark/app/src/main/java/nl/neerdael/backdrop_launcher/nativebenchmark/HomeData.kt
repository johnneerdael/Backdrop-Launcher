package nl.neerdael.backdrop_launcher.nativebenchmark

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.util.Log
import java.io.File

data class HomeData(val scene: Scene, val providerStatus: String)

fun loadHome(context: Context, knownRows: List<AppRow>? = null): HomeData {
    val file = File(context.getExternalFilesDir(null), "scene.json")
    require(!file.exists() || file.length() <= 1024 * 1024) { "Scene exceeds 1 MiB" }
    var scene = Scene.parse(if (file.isFile) file.readText() else context.assets.open("scene.json").bufferedReader().use { it.readText() })
    if (scene.discoverApps && scene.rows.isEmpty()) {
        if (knownRows != null) scene = scene.copy(rows = knownRows)
        else {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppTile(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString(), null) }
            .sortedBy { it.label.lowercase() }
        scene = scene.copy(rows = listOf(AppRow("Applications", apps)))
        }
    }
    if (scene.watchNext.isNotEmpty()) return HomeData(scene, "Local scene Continue Watching")
    if (android.os.Build.VERSION.SDK_INT < 26) return HomeData(scene, "Watch Next requires Android 8 or later")
    return try {
        val items = mutableListOf<ResumeItem>()
        context.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null,
            "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC")?.use { cursor ->
            fun text(column: String): String? = cursor.getColumnIndex(column).takeIf { it >= 0 }
                ?.let { if (cursor.isNull(it)) null else cursor.getString(it) }
            while (cursor.moveToNext() && items.size < 10) {
                val packageName = text(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME) ?: continue
                val title = text(TvContract.WatchNextPrograms.COLUMN_TITLE) ?: continue
                val duration = text(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)?.toLongOrNull() ?: 0
                val position = text(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)?.toLongOrNull() ?: 0
                val image = text(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI)
                    ?: text(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI)
                items.add(ResumeItem(title, text(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION).orEmpty(),
                    image, if (duration > 0) (100.0 * position / duration).toInt().coerceIn(0, 100) else 0,
                    text(TvContract.WatchNextPrograms.COLUMN_INTENT_URI), packageName))
            }
        }
        HomeData(scene.copy(watchNext = items), "Public Watch Next: ${items.size} items")
    } catch (e: SecurityException) {
        HomeData(scene, "Watch Next permission unavailable; grant READ_TV_LISTINGS if supported")
    } catch (e: Exception) {
        Log.w("BackdropBenchmark", "Watch Next unavailable", e)
        HomeData(scene, "Watch Next provider unavailable")
    }
}
