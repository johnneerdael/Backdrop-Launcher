package nl.neerdael.backdrop_launcher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import nl.neerdael.backdrop_launcher.data.LegacyPreferences
import nl.neerdael.backdrop_launcher.core.ArtworkDecoder
import nl.neerdael.backdrop_launcher.core.needsSecondTicks
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Back-button clock, separate from Android's selected Dream service. */
class ClockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var imageView: ImageView
    private lateinit var decoder: ArtworkDecoder
    private val worker = Executors.newSingleThreadExecutor()
    private var active = false
    private val tick = object : Runnable {
        override fun run() {
            val prefs = LegacyPreferences(this@ClockActivity)
            fun format(key: String, fallback: String): String = try {
                SimpleDateFormat(prefs.string(key, fallback), Locale.getDefault()).format(Date())
            } catch (_: IllegalArgumentException) { SimpleDateFormat(fallback, Locale.getDefault()).format(Date()) }
            time.text = format("time_format", "H:mm")
            date.text = format("date_format", "EEEE d")
            val interval = if (needsSecondTicks(prefs.string("time_format", "H:mm"), prefs.string("date_format", "EEEE d"))) 1000L else 60_000L
            handler.postDelayed(this, interval - System.currentTimeMillis() % interval)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        time = TextView(this).apply { textSize = 80f; setTextColor(-1); gravity = Gravity.CENTER }
        date = TextView(this).apply { textSize = 28f; setTextColor(-1); gravity = Gravity.CENTER }
        val style = LegacyPreferences(this).string("screensaver_clock_style", "minimal")
        val family = when (style) { "retro", "pixel", "digital" -> "monospace"; "elegant" -> "serif"; else -> "sans-serif-thin" }
        time.typeface = Typeface.create(family, if (style in listOf("bold", "pixel")) Typeface.BOLD else Typeface.NORMAL)
        time.letterSpacing = if (style in listOf("retro", "neon", "pixel")) .08f else .02f
        imageView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        decoder = ArtworkDecoder(applicationContext)
        val clock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xff000000.toInt())
            setBackgroundColor(0x55000000)
            addView(time); addView(date)
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xff000000.toInt())
            addView(imageView, FrameLayout.LayoutParams(-1, -1)); addView(clock, FrameLayout.LayoutParams(-1, -1))
        })
    }
    override fun onResume() {
        super.onResume(); active = true; handler.post(tick)
        val path = intent.getStringExtra("backdrop") ?: return
        val epoch = decoder.epoch
        worker.execute {
            val image = try { decoder.decode(path, 1920, 1080, epoch) } catch (_: Exception) { null }
            handler.post { if (active && epoch == decoder.epoch) imageView.setImageBitmap(image) }
        }
    }
    override fun onPause() { active = false; decoder.cancel(); imageView.setImageDrawable(null); handler.removeCallbacksAndMessages(null); super.onPause() }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
