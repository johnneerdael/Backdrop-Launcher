package nl.neerdael.backdrop_launcher.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import nl.neerdael.backdrop_launcher.BuildConfig
import nl.neerdael.backdrop_launcher.core.UpdateAccess
import nl.neerdael.backdrop_launcher.core.UpdateInfo
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

internal data class UpdateArchive(val packageName: String, val version: String, val code: Long, val certificates: Set<String>)
internal interface UpdateNetwork {
    fun json(url: String): JSONObject
    fun download(url: String, target: File)
}

/** Only returns an installer file after package, version, pinned certificate and manifest checks. */
class NativeUpdater internal constructor(override val enabled: Boolean, private val installedCode: () -> Long,
    private val cache: File, private val network: UpdateNetwork,
    private val archive: (File) -> UpdateArchive?) : UpdateAccess {
    constructor(context: Context) : this(BuildConfig.SELF_UPDATER, {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
    }, File(context.cacheDir, "updates"), GitHubUpdateNetwork, { file -> readArchive(context, file) })

    private data class Expected(val code: Long, val sha256: String, val size: Long)
    @Volatile private var expected: Pair<String, Expected>? = null

    override fun check(): UpdateInfo? {
        if (!enabled) return null
        active()
        val release = network.json(RELEASE_API)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val tag = release.optString("tag_name")
        val version = stableVersion(tag) ?: throw IOException("GitHub returned an invalid release version.")
        val assets = release.optJSONArray("assets") ?: return null
        val apkAssets = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.filter { it.optString("name") == APK_NAME }
        val manifestAssets = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.filter { it.optString("name") == "release-manifest.json" }
        if (apkAssets.isEmpty() || manifestAssets.isEmpty()) return null
        require(apkAssets.size == 1 && manifestAssets.size == 1) { "Ambiguous release assets." }
        val apkUrl = apkAssets.single().getString("browser_download_url")
        val manifestUrl = manifestAssets.single().getString("browser_download_url")
        requireAssetUrl(apkUrl, tag, APK_NAME)
        requireAssetUrl(manifestUrl, tag, "release-manifest.json")
        active()
        val manifest = network.json(manifestUrl)
        require(manifest.optInt("schema") == 1 && manifest.optString("repository") == REPOSITORY &&
            manifest.optString("version") == version) { "Release manifest does not match this release." }
        val codeValue = manifest.opt("android_version_code")
        require(codeValue is Int || codeValue is Long) { "Release manifest has no Android version code." }
        val code = (codeValue as Number).toLong()
        require(code in 1..MAX_CODE) { "Invalid Android version code." }
        val cert = manifest.optString("signing_certificate_sha256")
        require(cert == CERTIFICATE) { "Release manifest has an unexpected signing certificate." }
        val metadata = manifest.optJSONObject("apks")?.optJSONObject(APK_NAME)
            ?: throw IOException("Release manifest has no universal APK metadata.")
        val checksum = metadata.optString("sha256")
        val size = metadata.optLong("size_bytes")
        require(Regex("^[0-9a-f]{64}$").matches(checksum) && size in 1..MAX_APK_BYTES) { "Invalid release APK metadata." }
        if (code <= installedCode()) return null
        expected = apkUrl to Expected(code, checksum, size)
        return UpdateInfo(version, code, apkUrl, cleanNotes(release.opt("body") as? String ?: ""))
    }

    override fun download(update: UpdateInfo): File {
        check(enabled) { "Updates are disabled in this build." }
        active()
        val tag = URI(update.url).rawPath.split('/').getOrNull(5) ?: throw IOException("Invalid update URL.")
        requireAssetUrl(update.url, tag, APK_NAME)
        require(stableVersion(tag) == update.version && update.code in 1..MAX_CODE && update.code > installedCode()) { "Update is not newer than the installed app." }
        val metadata = checkNotNull(expected?.takeIf { it.first == update.url && it.second.code == update.code }?.second) {
            "Check this release again before downloading."
        }
        check(cache.isDirectory || cache.mkdirs()) { "Cannot create update download directory." }
        val temporary = File.createTempFile("update_", ".part", cache)
        try {
            network.download(update.url, temporary)
            active()
            require(temporary.length() in 1..MAX_APK_BYTES) { "Invalid update download size." }
            require(temporary.length() == metadata.size && sha256(temporary) == metadata.sha256) { "Downloaded update differs from its release manifest." }
            verifyArchive(archive(temporary), update, installedCode())
            active()
            val final = File(cache, "backdrop-launcher-${update.code}.apk")
            check(temporary.renameTo(final)) { "Cannot save verified update." }
            cache.listFiles()?.filter { it != final && it.name.startsWith("backdrop-launcher-") && it.extension == "apk" }?.forEach { it.delete() }
            return final
        } finally { temporary.delete() }
    }

    companion object {
        internal const val REPOSITORY = "johnneerdael/Backdrop-Launcher"
        internal const val PACKAGE = "nl.neerdael.backdrop_launcher"
        internal const val CERTIFICATE = "18a076c8f5666fb103184caf1a98c753a044cc54ffc8ddc54cdd13ccc339f93d"
        internal const val RELEASE_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        internal const val APK_NAME = "backdrop-launcher.apk"
        internal const val MAX_APK_BYTES = 128L * 1024 * 1024
        private const val MAX_CODE = 2_100_000_000L
        internal fun stableVersion(tag: String): String? = tag.removePrefix("v").takeIf { Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matches(it) }
        internal fun requireAssetUrl(value: String, tag: String, name: String) {
            val uri = URI(value)
            require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
                uri.rawQuery == null && uri.rawFragment == null && stableVersion(tag) != null &&
                uri.rawPath == "/$REPOSITORY/releases/download/$tag/$name") { "Update asset is not owned by the official release repository." }
        }
        internal fun requireRedirectUrl(value: String) {
            val uri = URI(value)
            require(uri.scheme == "https" && uri.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com") &&
                uri.userInfo == null && uri.port == -1 && uri.rawFragment == null) { "Untrusted update redirect." }
        }
        internal fun verifyArchive(value: UpdateArchive?, update: UpdateInfo, installed: Long) {
            require(value != null && value.packageName == PACKAGE && value.code == update.code && value.code > installed &&
                value.version == update.version && value.certificates == setOf(CERTIFICATE)) { "Update package, version or signing certificate verification failed." }
        }
        internal fun cleanNotes(value: String): String {
            var text = value
            val changes = Regex("(?im)^##\\s+What['’]s new\\s*$").find(text)
            if (changes != null) text = text.substring(changes.range.last + 1)
            text = text.split(Regex("(?im)^##\\s+(Downloads|Source and verification|VirusTotal)\\s*$"), limit = 2)[0]
            text = text.substringBefore("<!-- virustotal-report -->")
            text = text.replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
                .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
                .replace(Regex("\\*\\*([^\\n]+?)\\*\\*"), "$1")
                .replace(Regex("(?im)^.*Full Changelog.*$"), "")
                .replace(Regex("https://github\\.com/[^/\\s]+/[^/\\s]+/(?:issues|pull)/(\\d+)"), "#$1")
                .replace(Regex("(?m)^#{1,6}\\s*"), "")
            return text.trim().take(24_000).ifBlank { "No release notes provided." }
        }
        private fun readArchive(context: Context, file: File): UpdateArchive? {
            @Suppress("DEPRECATION") val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            @Suppress("DEPRECATION") val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
            @Suppress("DEPRECATION") val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            val certificates = signatures?.map { signature -> digest(signature.toByteArray()) }?.toSet().orEmpty()
            @Suppress("DEPRECATION") val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            return UpdateArchive(info.packageName, info.versionName ?: "", code, certificates)
        }
        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val bytes = ByteArray(32 * 1024)
                while (true) { active(); val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
            }
            return hex(digest.digest())
        }
        private fun digest(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        internal fun active() { if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Update cancelled.") }
    }
}

internal object GitHubUpdateNetwork : UpdateNetwork {
    override fun json(url: String): JSONObject {
        val bytes = ByteArrayOutputStream()
        request(url, 1024 * 1024L) { input, deadline -> transfer(input, 1024 * 1024L, deadline) { buffer, count -> bytes.write(buffer, 0, count) } }
        return try { JSONObject(bytes.toString("UTF-8")) } catch (_: Exception) { throw IOException("GitHub returned invalid update metadata.") }
    }
    override fun download(url: String, target: File) {
        target.outputStream().use { output ->
            request(url, NativeUpdater.MAX_APK_BYTES) { input, deadline -> transfer(input, NativeUpdater.MAX_APK_BYTES, deadline) { bytes, count -> output.write(bytes, 0, count) } }
            output.fd.sync()
        }
    }
    private fun request(initial: String, limit: Long, consume: (InputStream, Long) -> Unit) {
        var address = initial
        val deadline = System.nanoTime() + 120_000_000_000L
        for (redirect in 0..4) {
            NativeUpdater.active()
            val remainingMillis = ((deadline - System.nanoTime()) / 1_000_000).coerceAtMost(20_000).toInt()
            if (remainingMillis <= 0) throw IOException("Update request timed out.")
            if (address != NativeUpdater.RELEASE_API) NativeUpdater.requireRedirectUrl(address)
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = minOf(15_000, remainingMillis)
                connection.readTimeout = remainingMillis
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "BackdropLauncher-Updater")
                connection.setRequestProperty("Accept", if (initial == NativeUpdater.RELEASE_API) "application/vnd.github+json" else "application/octet-stream")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    if (redirect == 4) throw IOException("Too many update redirects.")
                    val location = connection.getHeaderField("Location") ?: throw IOException("Invalid update redirect.")
                    address = URL(URL(address), location).toString()
                    NativeUpdater.requireRedirectUrl(address)
                    continue
                }
                if (status != 200) throw IOException("Update request failed (HTTP $status).")
                val length = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1
                if (length > limit) throw IOException("Update response exceeds size limit.")
                connection.inputStream.use { consume(it, deadline) }
                return
            } catch (_: IOException) { throw IOException("Update download or connection failed.") }
            finally { connection.disconnect() }
        }
    }
    private fun transfer(input: InputStream, limit: Long, deadline: Long, append: (ByteArray, Int) -> Unit) {
        val bytes = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            NativeUpdater.active()
            if (System.nanoTime() >= deadline) throw IOException("Update request timed out.")
            val count = input.read(bytes)
            NativeUpdater.active()
            if (System.nanoTime() >= deadline) throw IOException("Update request timed out.")
            if (count < 0) return
            total += count
            if (total > limit) throw IOException("Update response exceeds size limit.")
            append(bytes, count)
        }
    }
}
