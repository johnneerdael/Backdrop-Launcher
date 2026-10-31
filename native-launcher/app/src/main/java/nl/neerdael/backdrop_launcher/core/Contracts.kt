package nl.neerdael.backdrop_launcher.core

import java.io.File

interface PreferenceAccess {
    fun bool(key: String, default: Boolean = false): Boolean
    fun string(key: String, default: String = ""): String
    fun int(key: String, default: Int = 0): Int
    fun long(key: String, default: Long = 0): Long
    fun double(key: String, default: Double = 0.0): Double
    fun strings(key: String, default: List<String> = emptyList()): List<String>
    fun putBool(key: String, value: Boolean)
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putDouble(key: String, value: Double)
    fun putStrings(key: String, value: List<String>)
    fun remove(key: String)
    fun listen(changed: (String) -> Unit): () -> Unit
}

data class LauncherApp(val packageName: String, val label: String, val version: String,
    val androidApp: Boolean, val hidden: Boolean, val lastLaunchedAt: Long?, val artwork: String?)
data class LauncherRow(val id: Long, val name: String, val hidden: Boolean, val sort: Int,
    val source: Int, val height: Int, val order: Int, val apps: List<LauncherApp>)
data class LibrarySnapshot(val apps: List<LauncherApp>, val rows: List<LauncherRow>)

/** Blocking operations: call on the data worker or Dispatchers.IO. Preserve schema12 identities. */
interface LibraryAccess {
    fun snapshot(refreshInstalled: Boolean = false): LibrarySnapshot
    fun createRow(name: String): Long
    fun renameRow(id: Long, name: String)
    fun updateRow(id: Long, hidden: Boolean, sort: Int, height: Int)
    fun moveRow(id: Long, delta: Int)
    fun deleteRow(id: Long)
    fun setRowApps(id: Long, packages: List<String>)
    fun rowPackages(id: Long): List<String>
    fun moveApp(rowId: Long, packageName: String, delta: Int)
    fun setHidden(packageName: String, hidden: Boolean)
    fun setArtwork(packageName: String, asset: String?)
    fun recordLaunch(packageName: String)
    fun close()
}

data class DeviceAuthorization(val deviceCode: String, val userCode: String, val verificationUrl: String,
    val expiresAtMillis: Long, var intervalSeconds: Int) {
    /** Unlike verificationUrl, this keeps the code through Trakt sign-in so it need not be typed. */
    val activationUrl: String get() =
        "https://auth.trakt.tv/activate/${java.net.URLEncoder.encode(userCode, "UTF-8").replace("+", "%20")}"
}
enum class AuthorizationResult { PENDING, APPROVED, SLOW_DOWN, DENIED, EXPIRED, INVALID, ALREADY_USED, CANCELLED }
data class CatalogOption(val key: String, val title: String, val itemCount: Int = 0)
data class Backdrop(val image: String, val title: String? = null)

/** Network/provider operations are blocking; UI must dispatch them off the main thread. */
interface MediaAccess {
    val configured: Boolean
    val connected: Boolean
    fun beginAuthorization(): DeviceAuthorization
    fun pollAuthorization(auth: DeviceAuthorization): AuthorizationResult
    fun cancelAuthorization()
    fun disconnect()
    fun catalogs(): List<CatalogOption>
    fun refreshPopularLists(): List<CatalogOption>
    fun selectedCatalogs(): List<String>
    fun selectCatalogs(keys: List<String>)
    fun restoredBackdrop(): Backdrop
    fun nextBackdrop(): Backdrop
    fun chooseBundledBackdrop(asset: String): Backdrop
    fun watchNext(): List<ResumeItem>
    fun restoredWatchNext(): List<ResumeItem> = emptyList()
    fun prepareWatchNextImage(item: ResumeItem): ResumeItem = item
    fun selectWatchNext(item: ResumeItem) {}
    fun checkWatchNextPermission(): Boolean
}

data class UpdateInfo(val version: String, val code: Long, val url: String, val notes: String)
interface UpdateAccess {
    val enabled: Boolean
    fun check(): UpdateInfo?
    fun download(update: UpdateInfo): File
}

interface UiActions {
    val library: LibraryAccess
    fun refresh()
    fun launch(packageName: String)
    fun play(item: ResumeItem)
    fun selectResume(item: ResumeItem)
    fun showBackdrop(backdrop: Backdrop) { refresh() }
    fun showWatchNext(items: List<ResumeItem>) { refresh() }
    fun homeBack(action: String) {
        when (action) { "CLOCK" -> showClock(); "SCREENSAVER" -> startScreensaver() }
    }
    fun appInfo(packageName: String)
    fun uninstall(packageName: String)
    fun systemSettings()
    fun openNetworkSettings()
    fun showClock()
    fun screensaverSettings()
    fun startScreensaver()
    fun requestWatchNextPermission()
    fun installApk(file: File)
    fun message(text: String)
}
