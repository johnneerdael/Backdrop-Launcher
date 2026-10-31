package nl.neerdael.backdrop_launcher

import android.content.*
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.tv.material3.MaterialTheme
import nl.neerdael.backdrop_launcher.core.*
import nl.neerdael.backdrop_launcher.data.*
import nl.neerdael.backdrop_launcher.media.NativeMedia
import nl.neerdael.backdrop_launcher.platform.NativeUpdater
import nl.neerdael.backdrop_launcher.ui.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Production Home. Data and network work are event driven and never run on the UI thread. */
class MainActivity : ComponentActivity(), UiActions {
    override lateinit var library: LibraryAccess
        private set
    private lateinit var prefs: PreferenceAccess
    private lateinit var media: MediaAccess
    private lateinit var updater: UpdateAccess
    private lateinit var artwork: ComposeArtwork
    private val dataWorker = Executors.newSingleThreadExecutor()
    private val mediaWorker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val selection = HomeSelection()
    private var home by mutableStateOf<HomeData?>(null)
    private var snapshot by mutableStateOf(LibrarySnapshot(emptyList(), emptyList()))
    private var error by mutableStateOf<String?>(null)
    private var resumed by mutableStateOf(false)
    private var artworkRevision by mutableIntStateOf(0)
    private var backdrop: Backdrop? = null
    private var watchNext = emptyList<ResumeItem>()
    private var packageDirty = true
    private var initialized = false
    private var sourceDirty = false
    private var sourceRevision = 0L
    private var generation = 0L
    private var dataVersion = 0L
    private var pairVersion = 0L
    private var dataTask: Future<*>? = null
    private var mediaTask: Future<*>? = null
    private var mediaBusy = false
    private var pendingMedia = false
    private var pendingRotation = false
    private var pairTask: Future<*>? = null
    private var unsubscribe: (() -> Unit)? = null
    private var pendingInstall: File? = null
    private var observerRegistered = false
    private val provider = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { if (resumed) scheduleProviderRefresh() }
    }
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            packageDirty = true
            artwork.invalidate()
            artworkRevision++
            if (resumed) refresh()
        }
    }
    private val localRefresh = Runnable { if (resumed) { refresh(); scheduleRotation() } }
    private val providerRefresh = Runnable { refreshMedia(false) }
    private val rotate = Runnable { refreshMedia(true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        prefs = LegacyPreferences(applicationContext)
        library = LegacyLibrary(applicationContext, prefs)
        media = NativeMedia(applicationContext, prefs, BuildConfig.TRAKT_CLIENT_ID, BuildConfig.TMDB_API_KEY, BuildConfig.TRAKT_REDIRECT_URI)
        updater = NativeUpdater(applicationContext)
        artwork = ComposeArtwork(applicationContext)
        unsubscribe = prefs.listen { key ->
            if (!key.startsWith("native.") && !key.startsWith("native_")) handler.post {
                handler.removeCallbacks(localRefresh)
                handler.postDelayed(localRefresh, 100)
                if (key.startsWith("watch_next") || key == "show_watch_next_section") scheduleProviderRefresh()
                if (key == "trakt_backdrops_enabled" || key == "trakt_token" || key == "trakt_catalogs") {
                    sourceDirty = true
                    sourceRevision++
                    handler.removeCallbacks(rotate)
                    if (resumed) handler.postDelayed(rotate, 150)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(this, packages, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        setContent {
            MaterialTheme {
                val data = home
                if (data == null) LoadingHome(error)
                else LauncherScreen(data, snapshot, prefs, media, updater, this, selection, artwork, resumed, artworkRevision)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        generation++
        artwork.setActive(true)
        artworkRevision++
        refresh()
        if (initialized) refreshMedia(sourceDirty || prefs.bool("backdrop_refresh_on_resume", true))
        scheduleRotation()
        if (!observerRegistered && Build.VERSION.SDK_INT >= 26) try {
            contentResolver.registerContentObserver(Uri.parse("content://android.media.tv/watch_next_program"), true, provider)
            observerRegistered = true
        } catch (_: SecurityException) { /* Permission control stays available in settings. */ }
        pendingInstall?.let { if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) { pendingInstall = null; installApk(it) } }
    }

    override fun onPause() {
        resumed = false
        generation++
        handler.removeCallbacks(localRefresh); handler.removeCallbacks(providerRefresh); handler.removeCallbacks(rotate)
        dataTask?.cancel(true); mediaTask?.cancel(true); pairTask?.cancel(true)
        dataTask = null; mediaTask = null; pairTask = null
        mediaBusy = false; pendingMedia = false; pendingRotation = false
        artwork.setActive(false)
        if (observerRegistered) { contentResolver.unregisterContentObserver(provider); observerRegistered = false }
        super.onPause()
    }

    override fun refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { refresh() }; return }
        if (!resumed) return
        // Coalesce local mutations without re-enumerating unchanged installed packages.
        dataTask?.cancel(true)
        val request = generation
        val version = ++dataVersion
        val first = !initialized
        val discover = packageDirty
        dataTask = dataWorker.submit {
            try {
                val loaded = library.snapshot(discover)
                val restored = if (first) media.restoredBackdrop() else null
                val restoredFeed = if (first) media.restoredWatchNext() else null
                handler.post {
                    if (!resumed || generation != request || dataVersion != version) return@post
                    snapshot = loaded
                    packageDirty = false
                    if (first) {
                        backdrop = restored
                        watchNext = restoredFeed.orEmpty()
                        val saved = prefs.long("native.watch_next_selected_id", 0)
                        selection.resumeIndex = watchNext.indexOfFirst { it.id == saved }.coerceAtLeast(0)
                    }
                    initialized = true
                    error = null
                    render()
                    if (first) refreshMedia(prefs.bool("backdrop_refresh_on_resume", true))
                }
            } catch (failure: Exception) {
                Log.e("Backdrop", "Library unavailable; retained original data", failure)
                handler.post { if (generation == request && dataVersion == version && resumed) { error = "Launcher data could not be opened. Your existing data was preserved."; message(error!!) } }
            }
        }
    }

    private fun render() {
        val image = backdrop?.image ?: NativeMedia.BUNDLED_BACKDROPS.first()
        val visibleRows = snapshot.rows.filter { !it.hidden }.map { row ->
            AppRow(row.name, row.apps.filter { !it.hidden }.map { AppTile(it.packageName, it.label, it.artwork ?: "package:${it.packageName}") }, row.id)
        }.filter { it.apps.isNotEmpty() }
        val accent = prefs.string("accent_color", "FFFFFF").removePrefix("#").toLongOrNull(16)?.toInt() ?: 0xffffff
        val scene = Scene("Backdrop", image, visibleRows,
            if (prefs.bool("show_watch_next_section")) watchNext else emptyList(),
            prefs.bool("watch_next_big_picture_mode"), true, accent or (0xff shl 24),
            prefs.double("app_icon_background_opacity", .1).toFloat().coerceIn(0f, 1f), false,
            prefs.bool("show_app_names_below_icons"))
        selection.reconcile(scene)
        home = HomeData(scene, if (media.checkWatchNextPermission()) "" else "Watch Next permission is available in Settings")
    }

    private fun scheduleProviderRefresh() {
        handler.removeCallbacks(providerRefresh)
        if (resumed) handler.postDelayed(providerRefresh, 250)
    }

    private fun refreshMedia(changeBackdrop: Boolean) {
        if (!resumed || !initialized) return
        if (mediaBusy) {
            pendingMedia = true
            pendingRotation = pendingRotation || changeBackdrop
            return
        }
        mediaBusy = true
        val request = generation
        val source = sourceRevision
        mediaTask = mediaWorker.submit {
            var loaded: Pair<List<ResumeItem>, Backdrop?>? = null
            try {
                val feed = if (prefs.bool("show_watch_next_section")) media.watchNext() else emptyList()
                if (!Thread.currentThread().isInterrupted) {
                    val next = if (changeBackdrop) media.nextBackdrop() else null
                    loaded = feed to next
                }
            } catch (_: Exception) {
                if (!Thread.currentThread().isInterrupted) Log.w("Backdrop", "Media refresh unavailable; retained cached data")
            }
            val result = loaded
            handler.post {
                if (!resumed || request != generation) return@post
                mediaBusy = false
                result?.let { (feed, next) ->
                    watchNext = feed
                    if (next != null) {
                        backdrop = next
                        if (source == sourceRevision) sourceDirty = false
                    }
                    render()
                    home?.scene?.watchNext?.getOrNull(selection.resumeIndex)?.let { preparePair(it) }
                }
                scheduleRotation()
                if (pendingMedia) {
                    val rotateNext = pendingRotation
                    pendingMedia = false; pendingRotation = false
                    refreshMedia(rotateNext)
                }
            }
        }
    }

    private fun scheduleRotation() {
        handler.removeCallbacks(rotate)
        val minutes = prefs.int("backdrop_interval_minutes", 10).takeIf { it in listOf(5, 10, 30, 60) } ?: 10
        if (resumed) handler.postDelayed(rotate, minutes * 60_000L)
    }

    override fun selectResume(item: ResumeItem) {
        home?.scene?.let { scene -> scene.watchNext.indexOfFirst { sameItem(it, item) }.takeIf { it >= 0 }?.let { selection.selectResume(scene, it) } }
        preparePair(item)
    }
    override fun showBackdrop(backdrop: Backdrop) {
        this.backdrop = backdrop
        render()
        scheduleRotation()
    }
    override fun showWatchNext(items: List<ResumeItem>) {
        watchNext = items
        render()
        home?.scene?.watchNext?.getOrNull(selection.resumeIndex)?.let(::preparePair)
    }
    private fun preparePair(item: ResumeItem) {
        if (!resumed) return
        pairTask?.cancel(true)
        val request = generation
        val version = ++pairVersion
        val index = watchNext.indexOfFirst { sameItem(it, item) }
        if (index < 0) return
        val pair = watchNext.drop(index).take(2)
        pairTask = mediaWorker.submit {
            media.selectWatchNext(item)
            val prepared = pair.map {
                if (Thread.currentThread().isInterrupted) return@submit
                media.prepareWatchNextImage(it)
            }
            handler.post {
                if (!resumed || request != generation || version != pairVersion) return@post
                watchNext = watchNext.map { current -> prepared.firstOrNull { sameItem(it, current) } ?: current }
                render()
            }
        }
    }
    private fun sameItem(a: ResumeItem, b: ResumeItem) = if (a.id != 0L && b.id != 0L) a.id == b.id else a.contentId == b.contentId && a.packageName == b.packageName && a.title == b.title

    override fun launch(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName) ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Settings.ACTION_SETTINGS).takeIf { it.resolveActivity(packageManager)?.packageName == packageName }
        if (intent == null) message("Application is unavailable") else if (open(intent)) dataWorker.execute { library.recordLaunch(packageName) }
    }
    override fun play(item: ResumeItem) {
        try {
            val target = requireNotNull(item.packageName)
            val intent = Intent.parseUri(requireNotNull(item.intentUri), Intent.URI_INTENT_SCHEME)
            require(intent.component == null || intent.component!!.packageName == target)
            intent.`package` = target
            intent.selector = null
            intent.clipData = null
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (open(intent, false)) dataWorker.execute { library.recordLaunch(target) } else launch(target)
        } catch (_: Exception) { item.packageName?.let(::launch) ?: message("Playback target is unavailable") }
    }
    override fun appInfo(packageName: String) { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    override fun uninstall(packageName: String) { open(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))) }
    override fun systemSettings() { open(Intent(Settings.ACTION_SETTINGS)) }
    override fun homeBack(action: String) {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val isHome = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
        if (!isHome && !BuildConfig.DEBUG && !BuildConfig.BENCHMARK) finish()
        else when (action) { "CLOCK" -> showClock(); "SCREENSAVER" -> startScreensaver() }
    }
    override fun openNetworkSettings() {
        if (!open(Intent("android.settings.NETWORK_OPERATOR_SETTINGS"), false) && !open(Intent(Settings.ACTION_WIFI_SETTINGS), false)) systemSettings()
    }
    override fun showClock() { open(Intent(this, ClockActivity::class.java).putExtra("backdrop", home?.scene?.backdrop)) }
    override fun screensaverSettings() {
        val tv = Intent(Intent.ACTION_MAIN).setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity")
        if (!open(tv, false) && !open(Intent(Settings.ACTION_DREAM_SETTINGS), false) && !open(Intent(Settings.ACTION_DISPLAY_SETTINGS), false)) systemSettings()
    }
    override fun startScreensaver() {
        val ambient = Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
        if (!open(ambient, false) && !open(Intent("android.intent.action.DREAM"), false)) screensaverSettings()
    }
    override fun requestWatchNextPermission() {
        if (Build.VERSION.SDK_INT >= 26 && checkSelfPermission("android.permission.READ_TV_LISTINGS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf("android.permission.READ_TV_LISTINGS"), 1001)
        else scheduleProviderRefresh()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) scheduleProviderRefresh()
    }
    override fun installApk(file: File) {
        if (!BuildConfig.SELF_UPDATER || !file.canonicalPath.startsWith(cacheDir.canonicalPath + File.separator)) return
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingInstall = file
            open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        open(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    private fun open(intent: Intent, notify: Boolean = true): Boolean = try { startActivity(intent); true }
        catch (_: Exception) { if (notify) message("Application could not be opened"); false }
    override fun message(text: String) { handler.post { if (!isDestroyed) Toast.makeText(this, text, Toast.LENGTH_SHORT).show() } }
    override fun onDestroy() {
        unsubscribe?.invoke()
        unregisterReceiver(packages)
        handler.removeCallbacksAndMessages(null)
        artwork.close()
        dataWorker.shutdownNow(); mediaWorker.shutdownNow()
        // Avoid racing a still-finishing SQLite snapshot with close().
        Thread { synchronized(library) { library.close() } }.start()
        super.onDestroy()
    }
}
