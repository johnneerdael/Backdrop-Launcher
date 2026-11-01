package nl.neerdael.backdrop_launcher.media

import android.content.ContentValues
import android.media.tv.TvContract
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files

/** Runs against the emulator's real TV provider, whose rules no fake can stand in for. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class AndroidTvChannelProviderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val provider = AndroidTvChannelProvider(context)
    private val MOVIE_POSTER = 5  // Published by androidx.tvprovider, not the framework.
    private lateinit var channel: Uri
    private var channelId = 0L

    @Before fun publish() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, "android.permission.READ_TV_LISTINGS")
        channel = resolver.insert(TvContract.Channels.CONTENT_URI, ContentValues().apply {
            put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
            put(TvContract.Channels.COLUMN_DISPLAY_NAME, "Test movies")
            put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, "test-movies")
        })!!
        channelId = channel.lastPathSegment!!.toLong()
        program("Light", 10)
        program("Heavy", 90)
    }
    @After fun remove() { resolver.delete(channel, null, null) }

    private fun program(title: String, weight: Int) = resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, ContentValues().apply {
        put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
        put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_MOVIE)
        put(TvContract.PreviewPrograms.COLUMN_TITLE, title)
        put(TvContract.PreviewPrograms.COLUMN_WEIGHT, weight)
        put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, "https://img.example/$title-poster.jpg")
        put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, MOVIE_POSTER)
        put(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI, "https://img.example/$title-still.jpg")
        put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, "intent://$title#Intent;end")
    })!!

    @Test fun channelsAreListedWithTheFieldsARowIsBuiltFrom() {
        val row = provider.channels().single { it.optLong("id") == channelId }
        assertEquals("Test movies", row.getString("name"))
        assertEquals(context.packageName, row.getString("packageName"))
        assertEquals(context.applicationInfo.loadLabel(context.packageManager).toString(), row.getString("appName"))
        assertEquals("test-movies", row.getString("internalId"))
        assertEquals(TvContract.Channels.TYPE_PREVIEW, row.getString("type"))
    }

    @Test fun programsComeBackHeaviestFirstWithTheirArt() {
        val rows = provider.programs(channelId)
        assertEquals(listOf("Heavy", "Light"), rows.map { it.getString("title") })
        assertEquals("https://img.example/Heavy-poster.jpg", rows[0].getString("posterUri"))
        assertEquals("https://img.example/Heavy-still.jpg", rows[0].getString("thumbnailUri"))
        assertEquals(MOVIE_POSTER.toLong(), rows[0].getLong("posterAspect"))
        assertEquals("intent://Heavy#Intent;end", rows[0].getString("intentUri"))
    }

    @Test fun feedTurnsAPublishedChannelIntoPosterTiles() {
        val root = Files.createTempDirectory("tv-channels").toFile()
        try {
            val feed = TvChannelFeed(provider, MediaDisk(root))
            val key = feed.published().single { it.name == "Test movies" }.key
            val tiles = feed.refresh(setOf(key)).getValue(key)
            assertEquals(listOf("Heavy", "Light"), tiles.map { it.item.title })
            assertEquals(1000f / 1441f, tiles[0].aspect, .001f)
        } finally { root.deleteRecursively() }
    }
}
