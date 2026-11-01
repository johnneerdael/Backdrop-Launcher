package nl.neerdael.backdrop_launcher.media

import android.media.tv.TvContract
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class TvChannelFeedTest {
    private val movies = channel(1, "Movies", "tv.one", "movies")
    private val news = channel(2, "News", "tv.two", "news")
    private val moviesKey = "tv.one|movies"
    private val newsKey = "tv.two|news"

    @Test fun onlyPreviewChannelsArePublishedForSelection() = disk { disk ->
        val tuner = channel(3, "Tuner", "tv.three", "tuner").put("type", "TYPE_DVB_T")
        val provider = ChannelProvider(listOf(movies, tuner, news))
        assertEquals(listOf(moviesKey, newsKey), TvChannelFeed(provider, disk).published().map { it.key })
    }

    @Test fun channelWithoutProviderIdIsKeyedByItsName() = disk { disk ->
        val unnamed = channel(4, "Picks", "tv.one", null)
        assertEquals("tv.one|Picks", TvChannelFeed(ChannelProvider(listOf(unnamed)), disk).published().single().key)
    }

    @Test fun channelNamesItsAppAndFallsBackToThePackage() = disk { disk ->
        val named = channel(5, "Picks", "tv.one", "picks").put("appName", "One")
        val published = TvChannelFeed(ChannelProvider(listOf(named, news)), disk).published()
        assertEquals(listOf("One", "tv.two"), published.map { it.appName })
    }

    @Test fun permissionLossPublishesNothing() = disk { disk ->
        val provider = ChannelProvider(listOf(movies)).apply { allowed = false }
        assertTrue(TvChannelFeed(provider, disk).published().isEmpty())
    }

    @Test fun providerRefusalPublishesNothing() = disk { disk ->
        val provider = ChannelProvider(listOf(movies)).apply { failure = SecurityException("denied") }
        assertTrue(TvChannelFeed(provider, disk).published().isEmpty())
    }

    @Test fun onlySelectedChannelsAreRead() = disk { disk ->
        val provider = ChannelProvider(listOf(movies, news), mapOf(1L to listOf(program(10)), 2L to listOf(program(20))))
        val result = TvChannelFeed(provider, disk).refresh(setOf(newsKey))
        assertEquals(setOf(newsKey), result.keys)
        assertEquals(listOf(2L), provider.programQueries)
    }

    @Test fun programsRemovedByTheUserOrWithoutTitlesAreLeftOut() = disk { disk ->
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10), program(11).put("browsable", 0L), program(12).apply { remove("title") })))
        assertEquals(listOf(10L), TvChannelFeed(provider, disk).refresh(setOf(moviesKey)).getValue(moviesKey).map { it.item.id })
    }

    @Test fun postersAreChosenOverStills() = disk { disk ->
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = "content://poster", still = "content://still"))))
        val feed = TvChannelFeed(provider, disk)
        feed.refresh(setOf(moviesKey))
        val ready = mutableListOf<nl.neerdael.backdrop_launcher.core.ChannelProgram>()
        feed.prepare(moviesKey) { ready.clear(); ready.addAll(it) }
        assertEquals(listOf("content://poster"), provider.imageLoads)
        assertEquals(disk.relative(disk.file(TvChannelFeed.NAMESPACE, "content://poster")), ready.single().item.image)
        assertEquals(2f / 3f, ready.single().aspect, .001f)
    }

    @Test fun tileTakesTheShapeTheProgramDeclaresForItsArt() = disk { disk ->
        val wide = program(10, poster = "content://wide").put("posterAspect", 0L)
        val square = program(11, poster = "content://square").put("posterAspect", 3L)
        val film = program(12, poster = "content://film").put("posterAspect", 5L)
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(wide, square, film)))
        val aspects = TvChannelFeed(provider, disk).refresh(setOf(moviesKey)).getValue(moviesKey).map { it.aspect }
        assertEquals(listOf(16f / 9f, 1f, 1000f / 1441f), aspects)
    }

    @Test fun stillIsUsedWhenThereIsNoPoster() = disk { disk ->
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = null, still = "content://still"))))
        val feed = TvChannelFeed(provider, disk)
        val tile = feed.refresh(setOf(moviesKey)).getValue(moviesKey).single()
        assertEquals(16f / 9f, tile.aspect, .001f)
        assertNull(tile.item.image)
        feed.prepare(moviesKey) {}
        assertEquals(listOf("content://still"), provider.imageLoads)
    }

    @Test fun stillReplacesAPosterThatCannotBeLoaded() = disk { disk ->
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = "content://poster", still = "content://still"))))
        provider.broken += "content://poster"
        val feed = TvChannelFeed(provider, disk)
        feed.refresh(setOf(moviesKey))
        var tile = emptyList<nl.neerdael.backdrop_launcher.core.ChannelProgram>()
        feed.prepare(moviesKey) { tile = it }
        assertEquals(listOf("content://poster", "content://still"), provider.imageLoads)
        assertEquals(disk.relative(disk.file(TvChannelFeed.NAMESPACE, "content://still")), tile.single().item.image)
        assertEquals(16f / 9f, tile.single().aspect, .001f)
    }

    @Test fun brokenPosterIsNotRetriedUntilTheCooldownEnds() = disk { disk ->
        var clock = 1_000_000L
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = "content://poster", still = null))))
        provider.broken += "content://poster"
        val feed = TvChannelFeed(provider, disk, now = { clock })
        feed.refresh(setOf(moviesKey))
        feed.prepare(moviesKey) {}
        feed.refresh(setOf(moviesKey))
        feed.prepare(moviesKey) {}
        assertEquals(1, provider.imageLoads.size)
        clock += TvChannelFeed.RETRY_MILLIS
        provider.broken.clear()
        feed.prepare(moviesKey) {}
        assertEquals(2, provider.imageLoads.size)
    }

    @Test fun unsafeArtworkLocationsAreNeverOpened() = disk { disk ->
        val requests = mutableListOf<String>()
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = "file:///data/secret.png", still = "https://user:pw@host/a.png"))))
        val feed = TvChannelFeed(provider, disk, MediaTransport { requests += it.url; error("unexpected") })
        feed.refresh(setOf(moviesKey))
        feed.prepare(moviesKey) {}
        assertTrue(provider.imageLoads.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test fun remotePostersAreDownloadedWithRedirectsAndAnImageLimit() = disk { disk ->
        val requests = mutableListOf<MediaRequest>()
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = "https://img.example/p.jpg"))))
        val feed = TvChannelFeed(provider, disk, MediaTransport { requests += it; MediaResponse(200, emptyMap(), byteArrayOf(1)) })
        feed.refresh(setOf(moviesKey))
        feed.prepare(moviesKey) {}
        assertTrue(requests.single().followImageRedirects)
        assertEquals(TvChannelFeed.MAX_IMAGE_BYTES, requests.single().maxBytes)
    }

    @Test fun tilesAppearInBatchesWhilePostersLoad() = disk { disk ->
        val programs = List(TvChannelFeed.ART_BATCH + 1) { program(10L + it, poster = "content://p$it") }
        val feed = TvChannelFeed(ChannelProvider(listOf(movies), mapOf(1L to programs)), disk)
        feed.refresh(setOf(moviesKey))
        val filled = mutableListOf<Int>()
        feed.prepare(moviesKey) { batch -> filled += batch.count { it.item.image != null } }
        assertEquals(listOf(TvChannelFeed.ART_BATCH, TvChannelFeed.ART_BATCH + 1), filled)
    }

    @Test fun cachedPostersAreNotFetchedAgain() = disk { disk ->
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = "content://poster"))))
        val feed = TvChannelFeed(provider, disk)
        feed.refresh(setOf(moviesKey))
        feed.prepare(moviesKey) {}
        var calls = 0
        feed.prepare(moviesKey) { calls++ }
        assertEquals(1, provider.imageLoads.size)
        assertEquals(0, calls)
    }

    @Test fun restartShowsSavedRowsAndPostersWithoutTouchingTheProvider() = disk { disk ->
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = "content://poster"))))
        TvChannelFeed(provider, disk).apply { refresh(setOf(moviesKey)); prepare(moviesKey) {} }
        val queries = provider.channelQueries
        val restored = TvChannelFeed(provider, disk).restore(setOf(moviesKey)).getValue(moviesKey).single()
        assertEquals(disk.relative(disk.file(TvChannelFeed.NAMESPACE, "content://poster")), restored.item.image)
        assertEquals(queries, provider.channelQueries)
    }

    @Test fun transientProviderFailureKeepsTheLastRows() = disk { disk ->
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10))))
        val feed = TvChannelFeed(provider, disk)
        feed.refresh(setOf(moviesKey))
        provider.failure = IOException("busy")
        assertEquals(1, feed.refresh(setOf(moviesKey)).getValue(moviesKey).size)
    }

    @Test fun revokedPermissionClearsRowsAndCachedPosters() = disk { disk ->
        val provider = ChannelProvider(listOf(movies), mapOf(1L to listOf(program(10, poster = "content://poster"))))
        val feed = TvChannelFeed(provider, disk)
        feed.refresh(setOf(moviesKey))
        feed.prepare(moviesKey) {}
        provider.allowed = false
        assertTrue(feed.refresh(setOf(moviesKey)).isEmpty())
        assertFalse(disk.file(TvChannelFeed.NAMESPACE, "content://poster").exists())
    }

    @Test fun postersOfDeselectedChannelsAreRemoved() = disk { disk ->
        val provider = ChannelProvider(listOf(movies, news), mapOf(1L to listOf(program(10, poster = "content://one")), 2L to listOf(program(20, poster = "content://two"))))
        val feed = TvChannelFeed(provider, disk)
        feed.refresh(setOf(moviesKey, newsKey))
        feed.prepare(moviesKey) {}
        feed.prepare(newsKey) {}
        feed.refresh(setOf(newsKey))
        assertFalse(disk.file(TvChannelFeed.NAMESPACE, "content://one").exists())
        assertTrue(disk.file(TvChannelFeed.NAMESPACE, "content://two").exists())
    }

    private fun channel(id: Long, name: String, packageName: String, internalId: String?) = JSONObject().put("id", id).put("name", name)
        .put("packageName", packageName).put("type", TvContract.Channels.TYPE_PREVIEW).apply { if (internalId != null) put("internalId", internalId) }
    private fun program(id: Long, poster: String? = "content://poster$id", still: String? = "content://still$id") = JSONObject()
        .put("id", id).put("title", "Program $id").put("packageName", "tv.one").put("intentUri", "intent://$id#Intent;end").apply {
            if (poster != null) put("posterUri", poster).put("posterAspect", 4L)
            if (still != null) put("thumbnailUri", still).put("thumbnailAspect", 0L)
        }
    private fun disk(block: (MediaDisk) -> Unit) {
        val root = Files.createTempDirectory("tv-channel-test").toFile()
        try { block(MediaDisk(root) { it.isNotEmpty() }) } finally { root.deleteRecursively() }
    }
}

internal class ChannelProvider(private val channels: List<JSONObject>, private val programs: Map<Long, List<JSONObject>> = emptyMap()) : TvChannelProvider {
    var allowed = true
    var failure: Exception? = null
    var channelQueries = 0
    val programQueries = mutableListOf<Long>()
    val imageLoads = mutableListOf<String>()
    val broken = mutableSetOf<String>()
    override fun hasPermission() = allowed
    override fun channels(): List<JSONObject> { channelQueries++; failure?.let { throw it }; return channels }
    override fun programs(channelId: Long): List<JSONObject> { programQueries += channelId; return programs[channelId].orEmpty() }
    override fun contentImage(uri: String, maxBytes: Int): ByteArray {
        imageLoads += uri
        if (uri in broken) throw IOException("gone")
        return byteArrayOf(1, 2, 3)
    }
}
