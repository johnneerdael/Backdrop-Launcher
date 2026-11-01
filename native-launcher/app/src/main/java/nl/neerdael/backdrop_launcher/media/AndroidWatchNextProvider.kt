package nl.neerdael.backdrop_launcher.media

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.io.IOException

internal class AndroidWatchNextProvider(private val context: Context) : WatchNextProvider {
    override fun hasPermission(): Boolean = Build.VERSION.SDK_INT >= 26 &&
        context.checkSelfPermission("android.permission.READ_TV_LISTINGS") == PackageManager.PERMISSION_GRANTED

    override fun query(): List<JSONObject> {
        if (Build.VERSION.SDK_INT < 26) return emptyList()
        val fields = linkedMapOf(
            "id" to TvContract.WatchNextPrograms._ID,
            "title" to TvContract.WatchNextPrograms.COLUMN_TITLE,
            "description" to TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION,
            "posterUri" to TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            "backdropUri" to TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            "packageName" to TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            "contentId" to TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
            "duration" to TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            "playbackPosition" to TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            "aspectRatio" to TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            "lastEngagementDate" to TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            "intentUri" to TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            "watchNextType" to TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE)
        val cursor = context.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI,
            fields.values.toTypedArray(), null, null, "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC")
            ?: throw IOException("Continue Watching provider is unavailable")
        return cursor.use { it.rows(fields, 150).filter { row -> row.textOrNull("title") != null }.take(50) }
    }

    override fun contentImage(uri: String, maxBytes: Int): ByteArray = context.providerImage(uri, maxBytes)
}

/** Reads at most limit provider rows; columns the provider leaves empty are left out. */
internal fun Cursor.rows(fields: Map<String, String>, limit: Int): List<JSONObject> {
    val result = mutableListOf<JSONObject>()
    while (result.size < limit && moveToNext()) {
        val row = JSONObject()
        fields.forEach { (key, column) ->
            val index = getColumnIndex(column)
            if (index >= 0 && !isNull(index)) row.put(key, if (getType(index) == Cursor.FIELD_TYPE_INTEGER) getLong(index) else getString(index))
        }
        result.add(row)
    }
    return result
}

internal fun Context.providerImage(uri: String, maxBytes: Int): ByteArray {
    val parsed = Uri.parse(uri)
    require(parsed.scheme in PROVIDER_IMAGE_SCHEMES) { "Unsupported poster URI" }
    val input = contentResolver.openInputStream(parsed) ?: throw IOException("Poster is unavailable")
    return input.use { readBounded(it, maxBytes) }
}
