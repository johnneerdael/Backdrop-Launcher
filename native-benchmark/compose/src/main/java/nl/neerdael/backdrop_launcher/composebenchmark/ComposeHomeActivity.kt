package nl.neerdael.backdrop_launcher.composebenchmark

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.tv.material3.MaterialTheme
import nl.neerdael.backdrop_launcher.nativebenchmark.*
import java.io.File
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.Executors

/** Separately installed evaluation Home; no HOME filter or production data access. */
class ComposeHomeActivity : ComponentActivity() {
    private lateinit var artwork: ComposeArtwork
    private lateinit var sources: HomeSources
    private val dataWorker = Executors.newSingleThreadExecutor()
    private val refresh = HomeRefreshState()
    private val selection = HomeSelection()
    private var home by mutableStateOf<HomeData?>(null)
    private var error by mutableStateOf<String?>(null)
    private var resumed by mutableStateOf(false)
    private var artworkRevision by mutableIntStateOf(0)
    private var active = false
    private var loading = false
    private var packagesDirty = true
    private var sourceStamp: String? = null
    private var artworkStamp: String? = null
    private var dataLoads = 0
    private var renders = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        artwork = ComposeArtwork(applicationContext)
        sources = HomeSources(this) { artworkChanged ->
            if (!isDestroyed) {
                refresh.invalidate()
                if (artworkChanged) { packagesDirty = true; invalidateArtwork() }
                refreshHome()
            }
        }
        setContent {
            MaterialTheme {
                val data = home
                if (data == null) LoadingHome(error)
                else key(data.scene) {
                    ComposeHome(data, selection, artwork, resumed, artworkRevision,
                        ::launchApp, ::launchResume, { safeLaunch(Intent(Settings.ACTION_SETTINGS)) })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
        val file = File(getExternalFilesDir(null), "scene.json")
        val stamp = "${file.lastModified()}:${file.length()}:${checkSelfPermission("android.permission.READ_TV_LISTINGS")}"
        if (sourceStamp != stamp) { sourceStamp = stamp; refresh.invalidate() }
        home?.let {
            val current = sources.artworkStamp(it.scene)
            if (artworkStamp != current) { artworkStamp = current; invalidateArtwork() }
        }
        refreshHome()
    }

    override fun onResume() {
        super.onResume()
        artwork.setActive(true)
        // A rapid pause/resume can be coalesced by recomposition. Renew subscriptions even then.
        artworkRevision++
        resumed = true
    }

    private fun invalidateArtwork() { artwork.invalidate(); artworkRevision++ }

    private fun refreshHome() {
        if (!active || !refresh.dirty || loading || isDestroyed) return
        loading = true
        dataLoads++
        val request = refresh.version
        val knownRows = if (packagesDirty) null else home?.scene?.rows
        Log.i("BackdropCompose", "HOME_DATA_LOAD")
        dataWorker.execute {
            try {
                val loaded = loadHome(this, knownRows)
                runOnUiThread {
                    loading = false
                    if (isDestroyed) return@runOnUiThread
                    if (!refresh.accept(request)) { refreshHome(); return@runOnUiThread }
                    packagesDirty = false
                    selection.reconcile(loaded.scene)
                    sources.watchArtwork(loaded.scene)
                    artworkStamp = sources.artworkStamp(loaded.scene)
                    if (home?.scene != loaded.scene) { renders++; Log.i("BackdropCompose", "HOME_RENDER") }
                    home = loaded
                    error = null
                }
            } catch (e: Exception) {
                Log.e("BackdropCompose", "Scene rejected", e)
                runOnUiThread {
                    loading = false
                    if (isDestroyed) return@runOnUiThread
                    if (request != refresh.version) { refreshHome(); return@runOnUiThread }
                    home = null
                    error = "Scene could not be loaded: ${e.message}"
                }
            }
        }
    }

    private fun launchApp(app: AppTile) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) toast("${app.label} is unavailable") else safeLaunch(intent)
    }
    private fun launchResume(item: ResumeItem) {
        try {
            val intent = item.intentUri?.let { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }
            if (intent == null || item.packageName == null) toast("No playback target in this scene")
            else {
                intent.setPackage(item.packageName)
                intent.component?.let { require(it.packageName == item.packageName) }
                safeLaunch(intent)
            }
        } catch (_: Exception) { toast("Playback target unavailable") }
    }
    private fun safeLaunch(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { toast("Application could not be opened") }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    override fun onPause() {
        resumed = false
        artwork.setActive(false)
        super.onPause()
    }
    override fun onStop() { active = false; super.onStop() }
    override fun onDestroy() { sources.close(); artwork.close(); dataWorker.shutdownNow(); super.onDestroy() }

    override fun dump(prefix: String, fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        super.dump(prefix, fd, writer, args)
        writer.println("${prefix}backdropBenchmark: loads=$dataLoads renders=$renders version=${refresh.version} active=$active focus=${selection.area}:${selection.app} resume=${selection.resumeIndex}")
    }
}
