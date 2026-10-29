package nl.neerdael.backdrop_launcher.media

import nl.neerdael.backdrop_launcher.core.AuthorizationResult
import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NativeMediaTest {
    @Test fun authRequestAndPersistedTokenMatchLegacyEncoding() {
        val prefs = MediaPrefs()
        val requests = mutableListOf<MediaRequest>()
        val client = client(prefs, MediaTransport {
            requests.add(it)
            if (it.url.endsWith("/code")) deviceResponse() else response(200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""")
        })
        val auth = client.begin()
        assertEquals("ABCD", auth.userCode)
        assertEquals(AuthorizationResult.APPROVED, client.poll(auth))
        val token = JSONObject(prefs.string("trakt_token"))
        assertEquals(setOf("access_token", "refresh_token", "expires_at"), token.keys().asSequence().toSet())
        assertEquals(4600L, token.getLong("expires_at"))
        assertEquals("client", JSONObject(requests[0].body!!.toString(Charsets.UTF_8)).getString("client_id"))
        assertEquals("2", requests[0].headers["trakt-api-version"])
        assertTrue(client.connected)
    }

    @Test fun devicePollingCoversAllStatusesAndSlowdown() {
        val mapping = mapOf(400 to AuthorizationResult.PENDING, 404 to AuthorizationResult.INVALID,
            409 to AuthorizationResult.ALREADY_USED, 410 to AuthorizationResult.EXPIRED,
            418 to AuthorizationResult.DENIED, 429 to AuthorizationResult.SLOW_DOWN)
        mapping.forEach { (status, expected) ->
            val client = client(MediaPrefs(), MediaTransport { if (it.url.endsWith("/code")) deviceResponse() else response(status, "{}", mapOf("Retry-After" to "40")) })
            val auth = client.begin()
            assertEquals(expected, client.poll(auth))
            if (status == 429) assertEquals(40, auth.intervalSeconds)
            if (status !in setOf(400, 429)) assertEquals(AuthorizationResult.CANCELLED, client.poll(auth))
        }
    }

    @Test fun expiryNeverMakesTokenRequest() {
        var clock = 1_000_000L
        var requests = 0
        val client = TraktClient(MediaPrefs(), "client", "tmdb", "https://example.com", MediaTransport { requests++; deviceResponse() }, { clock })
        val auth = client.begin()
        clock = auth.expiresAtMillis
        assertEquals(AuthorizationResult.EXPIRED, client.poll(auth))
        assertEquals(1, requests)
    }

    @Test fun cancellationRejectsLateApproval() {
        val prefs = MediaPrefs()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val client = client(prefs, MediaTransport {
            if (it.url.endsWith("/code")) deviceResponse() else {
                entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
                response(200, """{"access_token":"late","refresh_token":"r","expires_in":3600}""")
            }
        })
        try {
            val auth = client.begin()
            val future = executor.submit<AuthorizationResult> { client.poll(auth) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            client.cancel()
            release.countDown()
            assertEquals(AuthorizationResult.CANCELLED, future.get(5, TimeUnit.SECONDS))
            assertFalse(client.connected)
            assertEquals("", prefs.string("trakt_token"))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun rejectsUntrustedVerificationUrl() {
        val client = client(MediaPrefs(), MediaTransport { response(200, """{"device_code":"d","user_code":"u","verification_url":"https://trakt.tv.evil.example/activate","expires_in":100,"interval":5}""") })
        assertThrows(IllegalArgumentException::class.java) { client.begin() }
    }

    @Test fun malformedTokenCannotReplaceExistingToken() {
        val prefs = MediaPrefs()
        val client = client(prefs, MediaTransport { if (it.url.endsWith("/code")) deviceResponse() else response(200, """{"access_token":"a","expires_in":3600}""") })
        val auth = client.begin()
        assertThrows(IllegalArgumentException::class.java) { client.poll(auth) }
        assertFalse(client.connected)
    }

    @Test fun concurrentCatalogCallsCoalesceRefresh() {
        val prefs = connectedPrefs(expired = true)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val refreshes = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)
        val client = client(prefs, MediaTransport {
            if (it.url.endsWith("/oauth/token")) {
                refreshes.incrementAndGet(); entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
                response(200, """{"access_token":"fresh","refresh_token":"new","expires_in":3600}""")
            } else {
                assertEquals("Bearer fresh", it.headers["Authorization"])
                response(200, "[]")
            }
        })
        try {
            val first = executor.submit<List<*>> { client.popular() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = executor.submit<List<*>> { client.popular() }
            release.countDown()
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS)
            assertEquals(1, refreshes.get())
            assertEquals("new", JSONObject(prefs.string("trakt_token")).getString("refresh_token"))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun rejectedRefreshDisconnectsButTransientFailurePreservesToken() {
        for (status in listOf(400, 401, 403, 503)) {
            val prefs = connectedPrefs(expired = true)
            val client = client(prefs, MediaTransport { response(status, "{}") })
            assertThrows(IOException::class.java) { client.popular() }
            assertEquals(status == 503, client.connected)
        }
    }

    @Test fun selectedDroppedPopularListSurvivesRefresh() {
        val prefs = connectedPrefs()
        prefs.putStrings("trakt_catalogs", listOf("popular_list:old:kept"))
        prefs.putString("trakt_lists", """[{"owner":"old","id":"kept","title":"Retained","itemCount":7},{"owner":"old","id":"gone","title":"Gone"}]""")
        val client = client(prefs, MediaTransport { response(200, """[{"list":{"name":"Current","ids":{"slug":"latest"},"item_count":4},"user":{"ids":{"slug":"person"}}}]""") })
        assertEquals("Current", client.popular().single().title)
        val options = client.catalogs()
        assertTrue(options.any { it.key == "popular_list:old:kept" && it.itemCount == 7 })
        assertFalse(options.any { it.key == "popular_list:old:gone" })
        assertTrue(options.any { it.key == "popular_list:person:latest" })
    }

    @Test fun candidateDedupAndTmdbPathValidation() {
        val prefs = connectedPrefs()
        prefs.putStrings("trakt_catalogs", listOf("anticipated_movies", "trending_movies"))
        var tmdbBody = """{"backdrop_path":"/good.jpg"}"""
        val client = client(prefs, MediaTransport {
            if (it.url.contains("themoviedb")) response(200, tmdbBody)
            else response(200, """[{"movie":{"title":"Film","ids":{"tmdb":42}}},{"movie":{"title":"Missing","ids":{}}}]""")
        })
        val candidate = client.candidates().single()
        assertEquals("https://image.tmdb.org/t/p/w1280/good.jpg", client.backdrop(candidate))
        tmdbBody = """{"backdrop_path":"//evil.example/a.jpg"}"""
        assertNull(client.backdrop(candidate))
    }

    @Test fun catalogSelectionIsValidatedAndDefaultsMatchFlutter() {
        val client = client(MediaPrefs(), MediaTransport { response(200, "[]") })
        assertEquals(4, client.selected().size)
        client.select(listOf("trending_movies", "trending_movies", "bad", "popular_list:u:l", "popular_list::bad"))
        assertEquals(listOf("trending_movies", "popular_list:u:l"), client.selected())
    }

    @Test fun boundedReadRejectsOversizeWithoutAcceptingPartialImage() {
        assertThrows(IOException::class.java) { readBounded(ByteArrayInputStream(ByteArray(1025)), 1024) }
        assertEquals(1024, readBounded(ByteArrayInputStream(ByteArray(1024)), 1024).size)
    }

    @Test fun interruptionStopsTransportAndPrefetchWithoutReading() {
        var read = false
        val input = object : java.io.InputStream() { override fun read(): Int { read = true; return -1 } }
        Thread.currentThread().interrupt()
        try {
            assertThrows(java.io.InterruptedIOException::class.java) { readBounded(input, 1024) }
            assertThrows(java.io.InterruptedIOException::class.java) { UrlMediaTransport.execute(MediaRequest("https://example.com")) }
            assertFalse(read)
        } finally { Thread.interrupted() }
    }

    @Test fun flutterCacheLayoutAndLastBundledWallpaperRestore() = withDisk { disk ->
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", MediaDisk.hash("test"))
        val prefs = MediaPrefs()
        val asset = NativeMedia.BUNDLED_BACKDROPS[7]
        disk.writeJson("wallpaper", "last", JSONObject().put("asset", asset))
        val media = media(prefs, disk)
        assertEquals(asset, media.restoredBackdrop().image)
        assertEquals(NativeMedia.BUNDLED_BACKDROPS[8], media.nextBackdrop().image)
        assertEquals(10, NativeMedia.BUNDLED_BACKDROPS.size)
        assertThrows(IllegalArgumentException::class.java) { media.chooseBundledBackdrop("../../private.jpg") }
    }

    @Test fun offlineRestoredTraktImageSurvivesRefreshFailure() = withDisk { disk ->
        val prefs = connectedPrefs().apply { putBool("trakt_backdrops_enabled", true) }
        val image = File(disk.root, "trakt_backdrops/movie_42.jpg")
        disk.write(image, byteArrayOf(1))
        disk.writeJson("wallpaper", "last", JSONObject().put("file", image.name).put("title", "Film"))
        val media = media(prefs, disk, MediaTransport { throw IOException("offline") })
        assertEquals("Film", media.restoredBackdrop().title)
        assertEquals("cache:trakt_backdrops/movie_42.jpg", media.nextBackdrop().image)
    }

    @Test fun invalidLastWallpaperPathCannotEscapeCache() = withDisk { disk ->
        val prefs = connectedPrefs().apply { putBool("trakt_backdrops_enabled", true) }
        disk.writeJson("wallpaper", "last", JSONObject().put("file", "../private.jpg"))
        assertTrue(media(prefs, disk).restoredBackdrop().image in NativeMedia.BUNDLED_BACKDROPS)
    }

    @Test fun downloadsBoundedBackdropAndPrunesOnlyOtherImages() = withDisk { disk ->
        val prefs = connectedPrefs().apply {
            putBool("trakt_backdrops_enabled", true)
            putStrings("trakt_catalogs", listOf("trending_movies"))
        }
        for (id in 1..5) disk.write(File(disk.root, "trakt_backdrops/movie_$id.jpg"), byteArrayOf(1))
        val transport = MediaTransport {
            when {
                it.url.contains("themoviedb") -> response(200, """{"backdrop_path":"/film.jpg"}""")
                it.url.contains("image.tmdb") -> {
                    assertEquals(NativeMedia.MAX_BACKDROP_BYTES, it.maxBytes)
                    assertTrue(it.followImageRedirects)
                    MediaResponse(200, mapOf("Content-Type" to "image/jpeg"), byteArrayOf(1, 2, 3))
                }
                else -> response(200, """[{"movie":{"title":"New film","ids":{"tmdb":42}}}]""")
            }
        }
        val media = media(prefs, disk, transport)
        assertEquals("cache:trakt_backdrops/movie_42.jpg", media.nextBackdrop().image)
        assertEquals("New film", media.restoredBackdrop().title)
        assertEquals(3, File(disk.root, "trakt_backdrops").listFiles()!!.size)
        assertTrue(File(disk.root, "trakt_backdrops/movie_42.jpg").exists())
    }

    @Test fun disconnectDuringDownloadCannotPersistOrDisplayLateImage() = withDisk { disk ->
        val prefs = connectedPrefs().apply {
            putBool("trakt_backdrops_enabled", true)
            putStrings("trakt_catalogs", listOf("trending_movies"))
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val transport = MediaTransport {
            when {
                it.url.contains("themoviedb") -> response(200, """{"backdrop_path":"/film.jpg"}""")
                it.url.contains("image.tmdb") -> {
                    entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
                    MediaResponse(200, mapOf("content-type" to "image/jpeg"), byteArrayOf(1))
                }
                else -> response(200, """[{"movie":{"title":"Late film","ids":{"tmdb":42}}}]""")
            }
        }
        try {
            val media = media(prefs, disk, transport)
            media.restoredBackdrop()
            val future = executor.submit<String> { media.nextBackdrop().image }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            media.disconnect()
            release.countDown()
            assertTrue(future.get(5, TimeUnit.SECONDS) in NativeMedia.BUNDLED_BACKDROPS)
            assertFalse(File(disk.root, "trakt_backdrops/movie_42.jpg").exists())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun feedProgressFilteringAndIntentPreserveLegacyFields() = withDisk { disk ->
        val prefs = MediaPrefs().apply {
            putBool("show_watch_next_section", true)
            putStrings("watch_next_excluded_packages", listOf("excluded"))
            putInt("watch_next_max_items", 1)
        }
        val provider = FeedProvider(listOf(
            row(1, "Skip", "excluded"), row(2, "Resume", "pkg").put("duration", 1000).put("playbackPosition", 1500).put("intentUri", "wuplay://play/42").put("contentId", "internal-42"), row(3)))
        val feed = WatchNextFeed(prefs, provider, disk)
        val item = feed.refresh().single()
        assertEquals("Resume", item.title)
        assertEquals(100, item.progress)
        assertEquals("wuplay://play/42", item.intentUri)
        assertEquals(2L, item.id)
        assertEquals("internal-42", item.contentId)
        val saved = disk.readJson(WatchNextFeed.NAMESPACE, "feed")!!
        assertEquals(3, saved.getJSONArray("items").length())
        assertEquals(0, WatchNextFeed.progress(JSONObject().put("duration", 0).put("playbackPosition", 1)))
        assertEquals(70, WatchNextFeed.progress(JSONObject().put("progressPercent", 70)))
    }

    @Test fun startupFeedRestoresWithoutQueryAndTransientProviderFailureKeepsIt() = withDisk { disk ->
        val prefs = MediaPrefs().apply { putBool("show_watch_next_section", true) }
        val provider = FeedProvider(emptyList()).apply { failure = IOException("temporarily unavailable") }
        disk.writeJson(WatchNextFeed.NAMESPACE, "feed", JSONObject().put("items", JSONArray(listOf(row(8)))).put("selected", 8))
        val feed = WatchNextFeed(prefs, provider, disk)
        assertEquals("Item 8", feed.restore().single().title)
        assertEquals(0, provider.queries)
        assertEquals("Item 8", feed.refresh().single().title)
        assertEquals(1, provider.queries)
        provider.allowed = false
        assertTrue(feed.refresh().isEmpty())
        assertNull(disk.readJson(WatchNextFeed.NAMESPACE, "feed"))
    }

    @Test fun backdropFailureUsesPosterAndPersistsSelectedPair() = withDisk { disk ->
        val prefs = MediaPrefs().apply { putBool("show_watch_next_section", true); putBool("watch_next_hide_programs_without_images", true) }
        val first = row(1).put("backdropUri", "https://images.test/bad").put("posterUri", "content://images/poster")
        val second = row(2).put("posterUri", "content://images/second")
        val feed = WatchNextFeed(prefs, FeedProvider(listOf(first, second)), disk,
            MediaTransport { response(503, "") })
        val items = feed.refresh()
        assertEquals(2, items.size)
        assertTrue(items[0].image!!.startsWith("cache:media_launcher/continue_watching/"))
        assertEquals(1L, disk.readJson(WatchNextFeed.NAMESPACE, "feed")!!.getLong("selected"))
        feed.prepare(items[1])
        assertEquals(1L, disk.readJson(WatchNextFeed.NAMESPACE, "feed")!!.getLong("selected"))
        feed.select(items[1])
        assertEquals(2L, disk.readJson(WatchNextFeed.NAMESPACE, "feed")!!.getLong("selected"))
    }

    @Test fun preparingControllerPairFetchesOnlyThoseTwoAndDoesNotChangeSelection() = withDisk { disk ->
        val prefs = MediaPrefs().apply { putBool("show_watch_next_section", true) }
        val provider = FeedProvider((1..5).map { row(it).put("posterUri", "content://images/$it") })
        val feed = WatchNextFeed(prefs, provider, disk)
        val items = feed.refresh()
        provider.imageLoads.clear()
        feed.select(items[2])
        feed.prepare(items[2])
        feed.prepare(items[3])
        assertEquals(listOf("content://images/3", "content://images/4"), provider.imageLoads)
        assertEquals(3L, prefs.long("native.watch_next_selected_id"))
        assertEquals(3L, disk.readJson(WatchNextFeed.NAMESPACE, "feed")!!.getLong("selected"))
    }

    @Test fun startupChoosesOneCachedImagePerSelectedItemAndPrunesLegacyExcess() = withDisk { disk ->
        val prefs = MediaPrefs().apply { putBool("show_watch_next_section", true) }
        val first = row(1).put("backdropUri", "content://images/backdrop1").put("posterUri", "content://images/poster1")
        val second = row(2).put("backdropUri", "content://images/backdrop2").put("posterUri", "content://images/poster2")
        val bytes = ByteArray(3 * 1024 * 1024) { 1 }
        for (uri in WatchNextFeed.uris(first) + WatchNextFeed.uris(second) + "content://images/stale") {
            disk.writeImage(WatchNextFeed.NAMESPACE, uri, bytes)
        }
        disk.writeJson(WatchNextFeed.NAMESPACE, "feed", JSONObject().put("items", JSONArray(listOf(first, second))).put("selected", 1))
        val provider = FeedProvider(listOf(first, second))
        val feed = WatchNextFeed(prefs, provider, disk)
        val restored = feed.restore()
        assertEquals(2, restored.size)
        assertEquals(disk.relative(disk.file(WatchNextFeed.NAMESPACE, "content://images/backdrop1")), restored[0].image)
        assertEquals(disk.relative(disk.file(WatchNextFeed.NAMESPACE, "content://images/backdrop2")), restored[1].image)
        feed.refresh()
        feed.prepare(restored[0]); feed.prepare(restored[1])
        assertTrue(provider.imageLoads.isEmpty())
        val retained = disk.file(WatchNextFeed.NAMESPACE, "feed").parentFile!!.listFiles()!!
        assertEquals(3, retained.size)
        assertTrue(retained.sumOf { it.length() } < 8 * 1024 * 1024)
        assertFalse(disk.file(WatchNextFeed.NAMESPACE, "content://images/stale").exists())
        assertFalse(disk.file(WatchNextFeed.NAMESPACE, "content://images/poster1").exists())
        assertFalse(disk.file(WatchNextFeed.NAMESPACE, "content://images/poster2").exists())
    }

    @Test fun selectedItemSurvivesRefreshReorderAndRestoresPairOnly() = withDisk { disk ->
        val prefs = MediaPrefs().apply { putBool("show_watch_next_section", true) }
        val provider = FeedProvider((1..5).map { row(it).put("posterUri", "content://images/$it") })
        val feed = WatchNextFeed(prefs, provider, disk)
        val items = feed.refresh()
        assertEquals("content://images/5", items[4].image)
        feed.select(items[3])
        provider.rows = listOf(provider.rows[3], provider.rows[4], provider.rows[0])
        feed.refresh()
        assertEquals(4L, disk.readJson(WatchNextFeed.NAMESPACE, "feed")!!.getLong("selected"))
        val restored = WatchNextFeed(prefs, provider, disk).restore()
        assertEquals(4L, restored[0].id)
        assertTrue(restored[0].image!!.startsWith("cache:"))
        assertTrue(restored[1].image!!.startsWith("cache:"))
        assertEquals("content://images/1", restored[2].image)
    }

    @Test fun selectedIdMigratesLegacyKeysToSeparateNativeStore() {
        for (legacyKey in listOf("native_watch_next_selected_id", "continue_watching_selected_program_id")) withDisk { disk ->
            val prefs = MediaPrefs().apply { putBool("show_watch_next_section", true); putLong(legacyKey, 4) }
            val provider = FeedProvider(listOf(row(1), row(4)))
            disk.writeJson(WatchNextFeed.NAMESPACE, "feed", JSONObject().put("items", JSONArray(provider.rows)).put("selected", 1))
            WatchNextFeed(prefs, provider, disk).restore()
            assertEquals(4L, prefs.long("native.watch_next_selected_id"))
            assertEquals(4L, prefs.long(legacyKey))
        }
    }

    @Test fun nativeSelectionWinsOverOlderKeysAndFeedSelection() = withDisk { disk ->
        val prefs = MediaPrefs().apply {
            putBool("show_watch_next_section", true)
            putLong("native.watch_next_selected_id", 4)
            putLong("native_watch_next_selected_id", 3)
            putLong("continue_watching_selected_program_id", 2)
        }
        val provider = FeedProvider((1..4).map { row(it).put("posterUri", "content://images/$it") })
        disk.writeJson(WatchNextFeed.NAMESPACE, "feed", JSONObject().put("items", JSONArray(provider.rows)).put("selected", 1))
        val feed = WatchNextFeed(prefs, provider, disk)
        feed.restore()
        feed.refresh()
        assertEquals(4L, prefs.long("native.watch_next_selected_id"))
        assertEquals(4L, disk.readJson(WatchNextFeed.NAMESPACE, "feed")!!.getLong("selected"))
        feed.select(feed.refresh()[1])
        assertEquals(2L, prefs.long("native.watch_next_selected_id"))
        assertEquals(3L, prefs.long("native_watch_next_selected_id"))
    }

    @Test fun malformedPersistedTokenIsNotAConnection() {
        for (value in listOf("null", "123", "{}", "{\"access_token\":null}", "{\"access_token\":4}")) {
            val prefs = MediaPrefs().apply { putString("trakt_token", value) }
            assertFalse(client(prefs, MediaTransport { response(200, "[]") }).connected)
        }
    }

    @Test fun disabledFeedNeverQueriesProvider() = withDisk { disk ->
        val provider = FeedProvider(listOf(row(1)))
        val feed = WatchNextFeed(MediaPrefs(), provider, disk)
        assertTrue(feed.restore().isEmpty())
        assertTrue(feed.refresh().isEmpty())
        assertEquals(0, provider.queries)
    }

    private fun client(prefs: MediaPrefs, transport: MediaTransport) = TraktClient(prefs, "client", "tmdb", "https://example.com", transport) { 1_000_000 }
    private fun media(prefs: MediaPrefs, disk: MediaDisk, transport: MediaTransport = MediaTransport { response(200, "[]") }): NativeMedia =
        NativeMedia(prefs, client(prefs, transport), disk, WatchNextFeed(prefs, FeedProvider(emptyList()), disk), transport, { 1_000_000 }, { it.isNotEmpty() })
    private fun connectedPrefs(expired: Boolean = false) = MediaPrefs().apply {
        putString("trakt_token", """{"access_token":"existing","refresh_token":"refresh","expires_at":${if (expired) 1 else 5000}}""")
    }
    private fun deviceResponse() = response(200, """{"device_code":"device","user_code":"ABCD","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":5}""")
    private fun response(status: Int, body: String, headers: Map<String, String> = emptyMap()) = MediaResponse(status, headers, body.toByteArray())
    private fun row(id: Int, title: String = "Item $id", pkg: String = "pkg") = JSONObject().put("id", id).put("title", title).put("packageName", pkg)
    private fun withDisk(block: (MediaDisk) -> Unit) {
        val root = Files.createTempDirectory("native-media-test").toFile()
        try { block(MediaDisk(root) { it.isNotEmpty() }) } finally { root.deleteRecursively() }
    }
}

internal class FeedProvider(var rows: List<JSONObject>) : WatchNextProvider {
    var allowed = true
    var failure: Exception? = null
    var queries = 0
    val imageLoads = mutableListOf<String>()
    override fun hasPermission() = allowed
    override fun query(): List<JSONObject> { queries++; failure?.let { throw it }; return rows }
    override fun contentImage(uri: String, maxBytes: Int): ByteArray { imageLoads.add(uri); return byteArrayOf(1, 2, 3) }
}

internal class MediaPrefs : PreferenceAccess {
    private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
    override fun bool(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun string(key: String, default: String) = values[key] as? String ?: default
    override fun int(key: String, default: Int) = values[key] as? Int ?: default
    override fun long(key: String, default: Long) = values[key] as? Long ?: default
    override fun double(key: String, default: Double) = values[key] as? Double ?: default
    @Suppress("UNCHECKED_CAST") override fun strings(key: String, default: List<String>) = values[key] as? List<String> ?: default
    override fun putBool(key: String, value: Boolean) { values[key] = value }
    override fun putString(key: String, value: String) { values[key] = value }
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun putStrings(key: String, value: List<String>) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun listen(changed: (String) -> Unit): () -> Unit = {}
}
