package nl.neerdael.backdrop_launcher.nativebenchmark

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppRowsAdapter(
    private val scene: Scene, private val artwork: ArtworkLoader,
    private val launch: (AppTile) -> Unit, private val focused: (AppFocus) -> Unit,
    private val restoreFocus: AppFocus?,
) : RecyclerView.Adapter<AppRowsAdapter.RowHolder>() {
    private var restorePending = restoreFocus != null
    class RowHolder(val list: RecyclerView) : RecyclerView.ViewHolder(list)
    override fun getItemCount() = scene.rows.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val list = RecyclerView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, dp(150))
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            clipToPadding = false
            clipChildren = false
            setPadding(dp(32), dp(8), dp(32), dp(18))
            itemAnimator = null
            setItemViewCacheSize(2)
            recycledViewPool.setMaxRecycledViews(0, 4)
        }
        return RowHolder(list)
    }
    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.list.contentDescription = scene.rows[position].name
        holder.list.adapter = TilesAdapter(scene.rows[position].apps, scene, artwork, launch) { tile, packageName ->
            focused(AppFocus(position, tile, packageName))
        }
        val index = restoreFocus?.tile ?: -1
        if (restorePending && restoreFocus?.row == position && index >= 0) {
            restorePending = false
            holder.list.scrollToPosition(index)
            holder.list.post {
                holder.list.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
            }
        }
    }
}

private class TilesAdapter(
    private val apps: List<AppTile>, private val scene: Scene, private val artwork: ArtworkLoader,
    private val launch: (AppTile) -> Unit, private val focused: (Int, String) -> Unit,
) : RecyclerView.Adapter<TilesAdapter.TileHolder>() {
    class TileHolder(val card: LinearLayout, val picture: ImageView, val label: TextView) : RecyclerView.ViewHolder(card)
    override fun getItemCount() = apps.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder {
        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(dp(146), -2).apply { rightMargin = dp(24) }
            isFocusable = true
            isClickable = true
            clipChildren = false
        }
        val frame = FrameLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, card.dp(82))
            clipToOutline = true
        }
        val image = ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        val label = TextView(parent.context).apply {
            setTextColor(Color.WHITE); textSize = 12f; maxLines = 1; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        card.addView(frame)
        card.addView(label, LinearLayout.LayoutParams(-1, card.dp(26)))
        fun background(focus: Boolean) = GradientDrawable().apply {
            cornerRadius = card.dp(12).toFloat()
            setColor((scene.accent and 0xffffff) or ((scene.tileOpacity * 255).toInt() shl 24))
            if (focus) setStroke(card.dp(2), Color.WHITE)
        }
        frame.background = background(false)
        card.setOnFocusChangeListener { view, focus ->
            frame.background = background(focus)
            view.animate().scaleX(if (focus) 1.06f else 1f).scaleY(if (focus) 1.06f else 1f).setDuration(120).start()
            view.elevation = if (focus) card.dp(4).toFloat() else 0f
            val position = (view.parent as? RecyclerView)?.getChildAdapterPosition(view) ?: RecyclerView.NO_POSITION
            if (focus && position != RecyclerView.NO_POSITION) focused(position, view.tag as String)
        }
        return TileHolder(card, image, label)
    }
    override fun onBindViewHolder(holder: TileHolder, position: Int) {
        val app = apps[position]
        holder.card.tag = app.packageName
        holder.card.contentDescription = app.label
        holder.label.text = app.label
        holder.label.visibility = if (scene.showAppNames) View.VISIBLE else View.GONE
        holder.card.setOnClickListener { launch(app) }
        holder.picture.setImageDrawable(null)
        artwork.load(holder.picture, app.artwork ?: "package:${app.packageName}", holder.card.dp(146), holder.card.dp(82))
    }
    override fun onViewRecycled(holder: TileHolder) {
        holder.picture.tag = null
        holder.picture.setImageDrawable(null)
        holder.card.animate().cancel()
        holder.card.scaleX = 1f; holder.card.scaleY = 1f
    }
}

fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
