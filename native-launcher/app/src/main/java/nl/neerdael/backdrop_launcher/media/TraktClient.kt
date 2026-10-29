package nl.neerdael.backdrop_launcher.media

import nl.neerdael.backdrop_launcher.core.AuthorizationResult
import nl.neerdael.backdrop_launcher.core.CatalogOption
import nl.neerdael.backdrop_launcher.core.DeviceAuthorization
import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.FutureTask
import java.util.concurrent.ExecutionException

internal data class Candidate(val title: String, val tmdbId: Int, val type: String) {
    val key: String get() = "${type}_$tmdbId"
}

internal class TraktClient(private val prefs: PreferenceAccess, private val clientId: String,
    private val tmdbApiKey: String, private val redirectUri: String,
    private val transport: MediaTransport = UrlMediaTransport,
    private val now: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private var generation = 0L
    private var device: DeviceAuthorization? = null
    private var polling: DeviceAuthorization? = null
    private var refresh: FutureTask<String?>? = null
    val configured: Boolean get() = clientId.isNotBlank() && tmdbApiKey.isNotBlank()
    val connected: Boolean get() = !(token().opt("access_token") as? String).isNullOrBlank()

    fun begin(): DeviceAuthorization {
        check(configured) { "Trakt is not configured in this build." }
        val stamp = synchronized(lock) { cancelLocked(); generation }
        val response = post("/oauth/device/code", JSONObject().put("client_id", clientId))
        checkStatus(response, "Unable to connect to Trakt")
        val body = responseObject(response)
        val verification = URI(body.getString("verification_url"))
        require(verification.scheme == "https" && verification.host in
            setOf("trakt.tv", "auth.trakt.tv", "app.trakt.tv") && verification.userInfo == null) {
            "Trakt returned an invalid activation URL."
        }
        val auth = DeviceAuthorization(body.getString("device_code"), body.getString("user_code"),
            verification.toString(), now() + body.getLong("expires_in").coerceIn(1, 86_400) * 1000,
            body.getInt("interval").coerceIn(1, 300))
        require(auth.deviceCode.isNotBlank() && auth.userCode.isNotBlank()) { "Invalid Trakt authorization." }
        synchronized(lock) {
            check(stamp == generation) { "Login cancelled." }
            device = auth
        }
        return auth
    }

    fun cancel() { synchronized(lock) { cancelLocked() } }
    private fun cancelLocked() { generation++; device = null }
    fun disconnect() { synchronized(lock) { cancelLocked(); prefs.remove("trakt_token") } }

    fun poll(auth: DeviceAuthorization): AuthorizationResult {
        val stamp = synchronized(lock) {
            if (device !== auth) return AuthorizationResult.CANCELLED
            if (now() >= auth.expiresAtMillis) { cancelLocked(); return AuthorizationResult.EXPIRED }
            if (polling != null) return AuthorizationResult.PENDING
            polling = auth
            generation
        }
        try {
            val response = post("/oauth/device/token", JSONObject().put("code", auth.deviceCode).put("client_id", clientId))
            synchronized(lock) {
                if (stamp != generation || device !== auth) return AuthorizationResult.CANCELLED
                if (now() >= auth.expiresAtMillis) { cancelLocked(); return AuthorizationResult.EXPIRED }
                val result = when (response.status) {
                    200 -> AuthorizationResult.APPROVED
                    400 -> AuthorizationResult.PENDING
                    404 -> AuthorizationResult.INVALID
                    409 -> AuthorizationResult.ALREADY_USED
                    410 -> AuthorizationResult.EXPIRED
                    418 -> AuthorizationResult.DENIED
                    429 -> AuthorizationResult.SLOW_DOWN
                    else -> throw IOException("Trakt login failed (HTTP ${response.status}).")
                }
                when (result) {
                    AuthorizationResult.APPROVED -> { saveToken(responseObject(response)); device = null }
                    AuthorizationResult.SLOW_DOWN -> auth.intervalSeconds = maxOf(auth.intervalSeconds + 5,
                        response.header("retry-after")?.toIntOrNull() ?: 0).coerceIn(1, 600)
                    AuthorizationResult.PENDING -> Unit
                    else -> cancelLocked()
                }
                return result
            }
        } finally { synchronized(lock) { if (polling === auth) polling = null } }
    }

    fun selected(): List<String> = prefs.strings("trakt_catalogs", BUILT_INS.keys.toList())
    fun select(keys: List<String>) { prefs.putStrings("trakt_catalogs", keys.filter(::validCatalog).distinct()) }
    fun catalogs(): List<CatalogOption> = BUILT_INS.map { CatalogOption(it.key, it.value) } + savedLists().mapNotNull(::listOption)

    fun popular(): List<CatalogOption> {
        val stamp = synchronized(lock) { generation }
        val rows = get(listOf("lists", "popular"), 30)
        val updated = mutableListOf<JSONObject>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val list = row.optJSONObject("list") ?: continue
            val user = row.optJSONObject("user") ?: list.optJSONObject("user") ?: continue
            val owner = user.optJSONObject("ids")?.textOrNull("slug") ?: user.textOrNull("username") ?: continue
            val id = list.optJSONObject("ids")?.let { it.textOrNull("slug") ?: it.textOrNull("trakt") } ?: continue
            val title = list.textOrNull("name") ?: continue
            if (':' in owner || ':' in id) continue
            updated.add(JSONObject().put("owner", owner).put("id", id).put("title", title).put("itemCount", list.optInt("item_count")))
        }
        synchronized(lock) {
            if (stamp == generation) {
                val merged = linkedMapOf<String, JSONObject>()
                savedLists().filter { listOption(it)?.key in selected() }.forEach { merged[listOption(it)!!.key] = it }
                updated.forEach { merged[listOption(it)!!.key] = it }
                prefs.putString("trakt_lists", JSONArray(merged.values.toList()).toString())
            }
        }
        return updated.mapNotNull(::listOption)
    }

    fun candidates(): List<Candidate> {
        val candidates = linkedMapOf<String, Candidate>()
        for (source in selected().filter(::validCatalog)) {
            ensureMediaActive()
            val parts = source.split(':')
            val isList = parts.size == 3
            for ((plural, type, field) in listOf(Triple("movies", "movie", "movie"), Triple("shows", "tv", "show"))) {
                if (!isList && !source.endsWith("_$plural")) continue
                val path = if (isList) listOf("users", parts[1], "lists", parts[2], "items", plural)
                    else listOf(plural, if (source.startsWith("anticipated_")) "anticipated" else "trending")
                val rows = get(path)
                for (index in 0 until rows.length()) {
                    val media = rows.optJSONObject(index)?.optJSONObject(field) ?: continue
                    val id = media.optJSONObject("ids")?.optInt("tmdb", 0) ?: 0
                    val title = media.textOrNull("title") ?: continue
                    if (id <= 0) continue
                    val candidate = Candidate(title, id, type)
                    candidates[candidate.key] = candidate
                }
            }
        }
        return candidates.values.toList()
    }

    fun backdrop(candidate: Candidate): String? {
        val response = transport.execute(MediaRequest("https://api.themoviedb.org/3/${candidate.type}/${candidate.tmdbId}?api_key=${encode(tmdbApiKey)}"))
        if (response.status == 404) return null
        checkStatus(response, "Unable to load backdrop")
        val path = responseObject(response).textOrNull("backdrop_path") ?: return null
        if (!Regex("^/[A-Za-z0-9_.-]+$").matches(path)) return null
        return "https://image.tmdb.org/t/p/w1280$path"
    }

    private fun token(): JSONObject = try { JSONObject(prefs.string("trakt_token", "{}")) } catch (_: Exception) { JSONObject() }
    private fun saveToken(body: JSONObject) {
        val access = body.opt("access_token") as? String
        val refresh = body.opt("refresh_token") as? String
        val expires = body.opt("expires_in") as? Number
        require(!access.isNullOrBlank() && !refresh.isNullOrBlank() && expires != null && expires.toLong() > 0) { "Trakt returned an invalid token." }
        prefs.putString("trakt_token", JSONObject().put("access_token", access).put("refresh_token", refresh)
            .put("expires_at", now() / 1000 + expires.toLong()).toString())
    }

    private fun authorization(): String? {
        val future: FutureTask<String?>
        val refreshToken: String
        val stamp: Long
        var owner = false
        synchronized(lock) {
            val token = token()
            if (token.optLong("expires_at") - 60 > now() / 1000 && token.textOrNull("access_token") != null)
                return "Bearer ${token.getString("access_token")}"
            refreshToken = token.textOrNull("refresh_token") ?: return null
            stamp = generation
            future = refresh ?: FutureTask<String?> { refreshToken(refreshToken, stamp) }.also { refresh = it; owner = true }
        }
        if (owner) {
            try { future.run() }
            finally { synchronized(lock) { if (refresh === future) refresh = null } }
        }
        return try { future.get() } catch (error: ExecutionException) { throw (error.cause as? Exception ?: IOException("Trakt token refresh failed.")) }
    }

    private fun refreshToken(refreshToken: String, stamp: Long): String? {
        val response = post("/oauth/token", JSONObject().put("refresh_token", refreshToken).put("client_id", clientId)
            .put("grant_type", "refresh_token").put("redirect_uri", redirectUri))
        return synchronized(lock) {
            if (stamp != generation) null
            else if (response.status in setOf(400, 401, 403)) { disconnect(); null }
            else { checkStatus(response, "Trakt token refresh failed"); val body = responseObject(response); saveToken(body); "Bearer ${body.getString("access_token")}" }
        }
    }

    private fun get(segments: List<String>, limit: Int = 20): JSONArray {
        ensureMediaActive()
        val authorization = authorization() ?: throw IOException("Connect your Trakt account first.")
        val stamp = synchronized(lock) { generation }
        val response = transport.execute(MediaRequest("https://api.trakt.tv/${segments.joinToString("/") { encode(it) }}?limit=$limit",
            headers = headers() + ("Authorization" to authorization)))
        if (response.status == 401) {
            synchronized(lock) { if (stamp == generation) disconnect() }
            throw IOException("Your Trakt connection expired. Connect again.")
        }
        checkStatus(response, "Unable to load Trakt catalog")
        return try { JSONArray(response.text()) } catch (_: Exception) { throw IOException("Trakt returned an invalid catalog.") }
    }

    private fun headers() = mapOf("Content-Type" to "application/json", "User-Agent" to "BackdropLauncher/Native",
        "trakt-api-version" to "2", "trakt-api-key" to clientId)
    private fun post(path: String, body: JSONObject): MediaResponse {
        ensureMediaActive()
        return transport.execute(MediaRequest("https://api.trakt.tv$path", "POST", headers(), body.toString().toByteArray()))
    }
    private fun savedLists(): List<JSONObject> = try { val rows = JSONArray(prefs.string("trakt_lists", "[]")); (0 until rows.length()).mapNotNull { rows.optJSONObject(it) } } catch (_: Exception) { emptyList() }
    private fun listOption(row: JSONObject): CatalogOption? {
        val owner = row.textOrNull("owner") ?: return null
        val id = row.textOrNull("id") ?: return null
        val title = row.textOrNull("title") ?: return null
        val key = "popular_list:$owner:$id"
        return if (validCatalog(key)) CatalogOption(key, title, row.optInt("itemCount")) else null
    }

    companion object {
        val BUILT_INS = linkedMapOf("anticipated_movies" to "Anticipated movies", "anticipated_shows" to "Anticipated shows",
            "trending_movies" to "Trending movies", "trending_shows" to "Trending shows")
        fun validCatalog(key: String): Boolean = key in BUILT_INS ||
            (key.split(':').let { it.size == 3 && it[0] == "popular_list" && it[1].isNotBlank() && it[2].isNotBlank() })
        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        private fun checkStatus(response: MediaResponse, message: String) { if (response.status != 200) throw IOException("$message (HTTP ${response.status}).") }
        private fun responseObject(response: MediaResponse): JSONObject = try { JSONObject(response.text()) }
            catch (_: Exception) { throw IOException("Media service returned an invalid response.") }
    }
}

internal fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
