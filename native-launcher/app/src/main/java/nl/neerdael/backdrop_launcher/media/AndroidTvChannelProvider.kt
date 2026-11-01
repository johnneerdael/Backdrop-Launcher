package nl.neerdael.backdrop_launcher.media

import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.os.Build
import org.json.JSONObject
import java.io.IOException

internal class AndroidTvChannelProvider(private val context: Context) : TvChannelProvider {
    override fun hasPermission(): Boolean = Build.VERSION.SDK_INT >= 26 &&
        context.checkSelfPermission("android.permission.READ_TV_LISTINGS") == PackageManager.PERMISSION_GRANTED

    // The provider refuses a selection from a launcher, so rows are filtered after reading.
    override fun channels(): List<JSONObject> {
        if (Build.VERSION.SDK_INT < 26) return emptyList()
        val fields = linkedMapOf(
            "id" to TvContract.Channels._ID,
            "name" to TvContract.Channels.COLUMN_DISPLAY_NAME,
            "packageName" to TvContract.Channels.COLUMN_PACKAGE_NAME,
            "internalId" to TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,
            "type" to TvContract.Channels.COLUMN_TYPE)
        val cursor = context.contentResolver.query(TvContract.Channels.CONTENT_URI, fields.values.toTypedArray(), null, null, null)
            ?: throw IOException("TV channel provider is unavailable")
        return cursor.use { it.rows(fields, MAX_CHANNELS) }.onEach { row -> appName(row.optString("packageName"))?.let { row.put("appName", it) } }
    }

    private fun appName(packageName: String): String? = runCatching {
        context.packageManager.run { getApplicationLabel(getApplicationInfo(packageName, 0)).toString() }
    }.getOrNull()

    override fun programs(channelId: Long): List<JSONObject> {
        if (Build.VERSION.SDK_INT < 26) return emptyList()
        val fields = linkedMapOf(
            "id" to TvContract.PreviewPrograms._ID,
            "title" to TvContract.PreviewPrograms.COLUMN_TITLE,
            "posterUri" to TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            "posterAspect" to TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            "thumbnailUri" to TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
            "thumbnailAspect" to TvContract.PreviewPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO,
            "packageName" to TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
            "contentId" to TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,
            "intentUri" to TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            "browsable" to TvContract.PreviewPrograms.COLUMN_BROWSABLE)
        val cursor = context.contentResolver.query(TvContract.buildPreviewProgramsUriForChannel(channelId), fields.values.toTypedArray(),
            null, null, "${TvContract.PreviewPrograms.COLUMN_WEIGHT} DESC") ?: throw IOException("TV channel provider is unavailable")
        return cursor.use { it.rows(fields, MAX_PROGRAM_ROWS) }
    }

    override fun contentImage(uri: String, maxBytes: Int): ByteArray = context.providerImage(uri, maxBytes)

    private companion object {
        const val MAX_CHANNELS = 200
        const val MAX_PROGRAM_ROWS = 100
    }
}
