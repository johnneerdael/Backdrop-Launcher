package nl.neerdael.backdrop_launcher.ui

import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class HomeAppearance(
    val accent: Long, val opacity: Float, val borders: Boolean, val borderDp: Float,
    val animateBorder: Boolean, val clickSound: Boolean, val rowTitles: Boolean,
    val appNames: Boolean, val autoHideBar: Boolean, val showDate: Boolean,
    val showTime: Boolean, val showNetwork: Boolean, val dateFormat: String,
    val timeFormat: String, val backAction: String, val rows: HomeRowLayout, val watch: WatchLayout,
) {
    /** Focus and progress must stay legible on dark backdrops, so a near-black accent only tints cards. */
    val indicator: Long get() = if (maxOf(accent shr 16 and 0xff, accent shr 8 and 0xff, accent and 0xff) < 0x30) 0xffffffffL else accent

    companion object {
        fun read(prefs: PreferenceAccess): HomeAppearance {
            val borders = prefs.bool("show_focus_borders", true)
            val color = prefs.string("accent_color", "FFFFFF").removePrefix("#").toLongOrNull(16) ?: 0xffffff
            return HomeAppearance(0xff000000L or (color and 0xffffff),
                (prefs.double("app_icon_background_opacity", .1).coerceIn(0.0, 1.0) * 10).roundToInt() / 10f,
                borders, prefs.double("focus_border_thickness", 1.5).toFloat().takeIf { it.isFinite() && it > 0f } ?: 1.5f,
                borders && prefs.bool("app_highlight_animation_enabled"), prefs.bool("app_key_click_enabled", true),
                prefs.bool("show_category_titles"), prefs.bool("show_app_names_below_icons"),
                prefs.bool("auto_hide_app_bar"), prefs.bool("show_date_in_status_bar"),
                prefs.bool("show_time_in_status_bar", true), prefs.bool("show_network_indicator_in_status_bar", true),
                prefs.string("date_format", "EEEE d"), prefs.string("time_format", "H:mm"), prefs.string("back_button_action"),
                HomeRowLayout(RowControl.POSITION.read(prefs), RowControl.START.read(prefs), RowControl.SIZE.read(prefs),
                    RowControl.APP_GAP.read(prefs), RowControl.ROW_GAP.read(prefs), RowControl.BELOW.read(prefs)),
                WatchLayout(WatchControl.HEIGHT.read(prefs), WatchControl.SIZE.read(prefs), WatchControl.NEXT_SIZE.read(prefs),
                    WatchControl.NEXT_OPACITY.read(prefs), WatchControl.GAP.read(prefs)))
        }
    }
}

/** One Left/Right setting; values saved by another build are clamped, never trusted. */
internal class LayoutControl(val key: String, val title: String, val subtitle: String, val unit: String,
                             val default: Int, val min: Int, val max: Int, val step: Int) {
    fun read(prefs: PreferenceAccess) = prefs.int(key, default).coerceIn(min, max)
    fun stepped(value: Int, direction: Int, top: Int = max, low: Int = min) = (value + direction * step).coerceIn(low, top)
    fun cycled(value: Int, top: Int = max, low: Int = min) = if (value >= top) low else stepped(value, 1, top, low)
    fun onStep(value: Int) = (min + (value - min) / step * step).coerceIn(min, max)
    /** Slider position within the limits that apply right now. */
    fun fraction(value: Int, top: Int = max, low: Int = min) = if (top > low) (value - low).toFloat() / (top - low) else 1f
}

internal object RowControl {
    val POSITION = LayoutControl("native.home_row_position", "Focused row height", "How far up the screen the active row sits", "%", 42, 14, 80, 2)
    val START = LayoutControl("native.home_row_start", "Row position", "Distance from the left edge of the screen", " dp", 32, 0, 320, 8)
    val SIZE = LayoutControl("native.home_row_app_size", "App size", "Size of the apps in every row", "%", 100, 50, 200, 5)
    val APP_GAP = LayoutControl("native.home_row_app_gap", "Space between apps", "Distance between apps in a row", " dp", 24, 0, 96, 4)
    val ROW_GAP = LayoutControl("native.home_row_gap", "Space between rows", "Distance from one row of apps to the next", " dp", 52, 8, 160, 4)
    val BELOW = LayoutControl("native.home_row_below_opacity", "Row below opacity", "Visibility of the rows under the active row", "%", 20, 0, 100, 5)
    val entries = listOf(POSITION, START, SIZE, APP_GAP, ROW_GAP, BELOW)
}

internal object WatchControl {
    val HEIGHT = LayoutControl("native.watch_height", "Height", "How far up the screen the cards end", "%", 43, 14, 85, 1)
    val SIZE = LayoutControl("native.watch_size", "Card size", "Size of the selected card", "%", 100, 40, 150, 5)
    val NEXT_SIZE = LayoutControl("native.watch_next_size", "Second card size", "Compared with the selected card", "%", 33, 18, 99, 3)
    val NEXT_OPACITY = LayoutControl("native.watch_next_opacity", "Second card opacity", "Visibility of the card next in line", "%", 20, 0, 100, 5)
    val GAP = LayoutControl("native.watch_gap", "Space between cards", "Distance between the two cards", " dp", 100, 0, 240, 4)
    val entries = listOf(HEIGHT, SIZE, NEXT_SIZE, NEXT_OPACITY, GAP)
}

internal data class HomeRowLayout(val position: Int, val start: Int, val size: Int, val appGap: Int,
                                  val rowGap: Int, val belowOpacity: Int) {
    fun tileHeight(limit: Int = 500) = (HOME_TILE_HEIGHT * size / 100).coerceAtMost(limit).coerceAtLeast(40)
}

internal data class WatchLayout(val height: Int, val size: Int, val nextSize: Int, val nextOpacity: Int, val gap: Int) {
    val scale get() = size / 100f
    val nextScale get() = scale * nextSize / 100f
    val nextAlpha get() = nextOpacity / 100f
    val cardWidth get() = WATCH_CARD_WIDTH * scale
    val cardHeight get() = WATCH_CARD_HEIGHT * scale
}

/** How far Height and Card size can go before Continue Watching meets the app row or the top of the screen. */
internal data class WatchLimits(val minHeight: Int = WatchControl.HEIGHT.min, val maxHeight: Int = WatchControl.HEIGHT.max,
                                val maxSize: Int = WatchControl.SIZE.max)

private const val HOME_TILE_HEIGHT = 110
private const val WATCH_TOP_MARGIN = 8f

/** The big picture layout's lowest edge; app rows never rise past it. */
internal const val WATCH_NEXT_BOTTOM = 90f + WATCH_CARD_HEIGHT

private fun homeWatchLine(screenHeightDp: Float, watch: WatchLayout) = screenHeightDp * (1f - watch.height / 100f)

/** Height anchors the bottom edge, so resizing the cards keeps their distance from the app row. */
internal fun homeWatchBottom(screenHeightDp: Float, watch: WatchLayout) =
    maxOf(homeWatchLine(screenHeightDp, watch), watch.cardHeight + WATCH_TOP_MARGIN)

/** [rowTop] is where the row sits with no ceiling, so lowering the cards never moves or shrinks it. */
internal fun homeWatchLimits(screenHeightDp: Float, watch: WatchLayout, rowTop: Float): WatchLimits {
    val height = WatchControl.HEIGHT
    val lowest = ceil((screenHeightDp - rowTop) * 100 / screenHeightDp).toInt().coerceIn(height.min, height.max)
    val highest = floor((screenHeightDp - watch.cardHeight - WATCH_TOP_MARGIN) * 100 / screenHeightDp).toInt().coerceIn(lowest, height.max)
    val room = (homeWatchLine(screenHeightDp, watch) - WATCH_TOP_MARGIN) / WATCH_CARD_HEIGHT * 100
    return WatchLimits(lowest, highest, WatchControl.SIZE.onStep(room.toInt()))
}

// Space LazyRow keeps around tiles so the focus scale is not clipped.
private const val ROW_INSET_TOP = 10f
private const val ROW_INSET_BOTTOM = 8f

/** A row's slot; [rowGap] is measured between tiles, so it can be smaller than the insets. */
internal fun homeRowExtent(tileHeight: Int, showTitles: Boolean, showNames: Boolean, rowGap: Int): Float =
    tileHeight + rowGap + (if (showTitles) 26f else 0f) + (if (showNames) 28f else 0f)

private fun homeRowFrame(showTitles: Boolean, showNames: Boolean) =
    homeRowExtent(0, showTitles, showNames, 0) + ROW_INSET_TOP + ROW_INSET_BOTTOM

/** Where every active row starts, raised only when the row would leave the screen. */
internal fun homeRowTop(screenHeightDp: Float, position: Int, tile: Int, showTitles: Boolean, showNames: Boolean, ceiling: Float = 0f): Float =
    minOf(screenHeightDp * (1f - position / 100f), screenHeightDp - tile - homeRowFrame(showTitles, showNames)).coerceAtLeast(ceiling)

/** The tallest tile that fits between [ceiling] and the bottom of the screen. */
internal fun homeTileLimit(screenHeightDp: Float, ceiling: Float, showTitles: Boolean, showNames: Boolean) =
    (screenHeightDp - ceiling - homeRowFrame(showTitles, showNames)).toInt()

/** The highest Focused row height, on the control's own steps, that keeps the row below [ceiling]. */
internal fun homeRowPositionLimit(screenHeightDp: Float, ceiling: Float) =
    RowControl.POSITION.onStep(((1f - ceiling / screenHeightDp) * 100).toInt())

/** Distance between row slots, negative when moving back up. */
internal fun homeRowTravel(extents: List<Float>, from: Int, to: Int): Float {
    fun span(first: Int, end: Int) = (first until end).sumOf { extents.getOrElse(it) { 0f }.toDouble() }.toFloat()
    return if (to >= from) span(from, to) else -span(to, from)
}

/** Fractional active row: 1.5 is half way from the second row to the third. */
internal fun homeRowPage(extents: List<Float>, firstIndex: Int, firstOffset: Float): Float {
    val extent = extents.getOrElse(firstIndex) { 0f }
    return firstIndex + if (extent > 0f) (firstOffset / extent).coerceIn(0f, 1f) else 0f
}

/** Rows fade out completely as they leave upwards and settle at [below] underneath. */
internal fun homeRowAlpha(distance: Float, below: Float): Float = when {
    distance <= -1f -> 0f
    distance < 0f -> 1f + distance
    else -> 1f + (below - 1f) * distance.coerceAtMost(1f)
}

/** Decode buckets keep the current bitmap while App size moves by small steps. */
internal fun homeDecodeHeight(tileHeight: Int) = (tileHeight + 39) / 40 * 40
