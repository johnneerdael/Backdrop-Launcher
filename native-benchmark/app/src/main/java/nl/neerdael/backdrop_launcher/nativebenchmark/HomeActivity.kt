package nl.neerdael.backdrop_launcher.nativebenchmark

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/** Engine-free Home prototype. No HOME intent filter and no production preference/database access. */
class HomeActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var artwork: ArtworkLoader
    private val dataWorker = Executors.newSingleThreadExecutor()
    private var epoch = 0
    private var resumeIndex = 0
    private var appFocus: AppFocus? = null
    private enum class FocusArea { APPS, WATCH_NEXT, SETTINGS }
    private var focusArea = FocusArea.APPS
    private var active = false
    private var home: HomeData? = null
    private var font: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        artwork = ArtworkLoader(applicationContext)
        font = try { Typeface.createFromAsset(assets, "fonts/Inter.ttf") } catch (_: Exception) { Typeface.DEFAULT }
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        active = true
        val request = ++epoch
        dataWorker.execute {
            try {
                val loaded = loadHome(this)
                runOnUiThread {
                    if (!active || request != epoch) return@runOnUiThread
                    home = loaded
                    render(loaded)
                }
            } catch (e: Exception) {
                Log.e("BackdropBenchmark", "Scene rejected", e)
                runOnUiThread {
                    if (active && request == epoch) {
                        root.removeAllViews()
                        root.addView(text("Scene could not be loaded: ${e.message}", 20f))
                    }
                }
            }
        }
    }

    private fun text(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); typeface = font
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }

    private fun render(data: HomeData) {
        root.removeAllViews()
        val scene = data.scene
        val restoreArea = focusArea
        val restoreFocus = scene.restoreAppFocus(appFocus).takeIf { restoreArea == FocusArea.APPS }
        val background = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        root.post {
            if (!active || background.parent == null) return@post
            if (!scene.bigPicture || scene.watchNext.isEmpty()) {
                artwork.load(background, scene.backdrop, root.width, root.height, retain = false)
            }
        }
        val scrim = CinematicScrim(this).apply { visibility = if (scene.scrim) View.VISIBLE else View.GONE }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val settings = ImageButton(this).apply {
            contentDescription = "Benchmark details"
            setImageResource(android.R.drawable.ic_menu_preferences)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            this.background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0x33000000) }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) focusArea = FocusArea.SETTINGS }
            setOnClickListener {
                AlertDialog.Builder(this@HomeActivity).setTitle("Native Home benchmark")
                    .setMessage("${scene.name}\n\n${data.providerStatus}\n\nThis prototype does not edit Backdrop settings or rows.")
                    .setPositiveButton("Close", null)
                    .setNeutralButton("TV settings") { _, _ -> safeLaunch(Intent(Settings.ACTION_SETTINGS)) }.show()
            }
        }
        toolbar.addView(settings, LinearLayout.LayoutParams(root.dp(36), root.dp(36)))
        toolbar.addView(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 22f; typeface = font
            setTextColor(Color.WHITE); setPadding(dp(12), 0, 0, 0); setShadowLayer(3f, 0f, 1f, Color.BLACK)
        })
        root.addView(toolbar, FrameLayout.LayoutParams(-2, root.dp(40), Gravity.TOP or Gravity.RIGHT).apply {
            topMargin = root.dp(8); rightMargin = root.dp(32)
        })

        val rows = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            clipToPadding = false; clipChildren = false; itemAnimator = null
            adapter = AppRowsAdapter(scene, artwork, ::launchApp, {
                appFocus = it; focusArea = FocusArea.APPS
            }, restoreFocus)
        }
        root.addView(rows, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = (resources.displayMetrics.heightPixels * .62f).toInt()
        })
        val resume = if (scene.watchNext.isNotEmpty()) addResume(scene, background, scrim, rows) else null
        val restoreRow = restoreFocus?.row ?: -1
        if (restoreRow >= 0) rows.scrollToPosition(restoreRow)
        root.post {
            if (!active) return@post
            when (restoreArea) {
                FocusArea.SETTINGS -> settings.requestFocus()
                FocusArea.WATCH_NEXT -> if (resume?.requestFocus() != true) rows.requestFocus()
                FocusArea.APPS -> if (!rows.requestFocus()) settings.requestFocus()
            }
            Log.i("BackdropBenchmark", "HOME_READY scene=${scene.name}; rows=${scene.rows.size}; watchNext=${scene.watchNext.size}")
        }
    }

    private fun addResume(scene: Scene, background: ImageView, scrim: View, rows: RecyclerView): View {
        resumeIndex = resumeIndex.coerceIn(0, scene.watchNext.lastIndex)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            this.background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0x55000000) }
            clipToOutline = true
        }
        val title = text("", 14f).apply { maxLines = 1; typeface = Typeface.create(font, Typeface.BOLD) }
        val subtitle = text("Continue Watching", 12f)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(scene.accent)
        }
        val watch = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        if (!scene.bigPicture) watch.addView(image, LinearLayout.LayoutParams(root.dp(296), root.dp(164)))
        watch.addView(title)
        watch.addView(subtitle)
        watch.addView(progress, LinearLayout.LayoutParams(-1, root.dp(3)))
        watch.setOnFocusChangeListener { view, focused ->
            if (focused) focusArea = FocusArea.WATCH_NEXT
            view.background = GradientDrawable().apply {
                cornerRadius = root.dp(9).toFloat(); setColor(Color.TRANSPARENT)
                if (focused) setStroke(root.dp(2), Color.WHITE)
            }
        }
        val description = text("", 13f).apply { maxLines = 3 }
        if (scene.bigPicture) {
            panel.addView(text("CONTINUE WATCHING", 11f))
            panel.addView(title.apply { textSize = 28f }.also { watch.removeView(it) })
            panel.addView(description, LinearLayout.LayoutParams(root.dp(560), -2))
        }
        panel.addView(watch, LinearLayout.LayoutParams(if (scene.bigPicture) root.dp(200) else root.dp(300), -2))
        root.addView(panel, FrameLayout.LayoutParams(-2, -2).apply {
            leftMargin = root.dp(32); topMargin = root.dp(if (scene.bigPicture) 90 else 103)
        })
        fun show() {
            val item = scene.watchNext[resumeIndex]
            title.text = item.title
            description.text = item.description
            subtitle.text = if (scene.bigPicture) "▶  Continue watching" else "Continue Watching"
            watch.contentDescription = "Continue watching ${item.title}"
            progress.progress = item.progress
            progress.visibility = if (item.progress > 0) View.VISIBLE else View.GONE
            val target = if (scene.bigPicture) background else image
            target.tag = null
            target.setImageDrawable(null)
            val path = if (scene.bigPicture) scene.backdropFor(resumeIndex) else item.image
            path?.let { artwork.load(target, it,
                if (scene.bigPicture) resources.displayMetrics.widthPixels else root.dp(296),
                if (scene.bigPicture) resources.displayMetrics.heightPixels else root.dp(164), retain = !scene.bigPicture) }
            scrim.visibility = if (scene.bigPicture || scene.scrim) View.VISIBLE else View.GONE
        }
        watch.setOnKeyListener { _, code, event ->
            if (event.action != KeyEvent.ACTION_DOWN) false else when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    resumeIndex = (resumeIndex + if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else scene.watchNext.size - 1) % scene.watchNext.size
                    show(); true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> { rows.requestFocus(); true }
                else -> false
            }
        }
        watch.setOnClickListener {
            val item = scene.watchNext[resumeIndex]
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
        show()
        return watch
    }

    private fun launchApp(app: AppTile) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) toast("${app.label} is unavailable") else safeLaunch(intent)
    }
    private fun safeLaunch(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { toast("Application could not be opened") }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    override fun onStop() {
        active = false; epoch++
        artwork.clear()
        root.removeAllViews()
        super.onStop()
    }
    override fun onDestroy() { artwork.close(); dataWorker.shutdownNow(); super.onDestroy() }
}

private class CinematicScrim(context: android.content.Context) : View(context) {
    private val vertical = Paint()
    private val horizontal = Paint()
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        vertical.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x59000000, 0x00000000, 0x80000000.toInt(), 0xe0000000.toInt()),
            floatArrayOf(0f, .25f, .65f, 1f), Shader.TileMode.CLAMP)
        horizontal.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(0xa6000000.toInt(), 0x40000000, 0x00000000), floatArrayOf(0f, .35f, .7f), Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vertical)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), horizontal)
    }
}
