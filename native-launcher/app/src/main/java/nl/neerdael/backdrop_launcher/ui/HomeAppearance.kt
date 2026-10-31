package nl.neerdael.backdrop_launcher.ui

import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import kotlin.math.roundToInt

internal data class HomeAppearance(
    val accent: Long, val opacity: Float, val borders: Boolean, val borderDp: Float,
    val animateBorder: Boolean, val clickSound: Boolean, val rowTitles: Boolean,
    val appNames: Boolean, val autoHideBar: Boolean, val showDate: Boolean,
    val showTime: Boolean, val showNetwork: Boolean, val dateFormat: String,
    val timeFormat: String, val backAction: String, val rows: HomeRowLayout,
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
                    RowControl.APP_GAP.read(prefs), RowControl.ROW_GAP.read(prefs), RowControl.BELOW.read(prefs)))
        }
    }
}

/** One Left/Right setting; values saved by another build are clamped, never trusted. */
internal enum class RowControl(val key: String, val title: String, val subtitle: String, val unit: String,
                               val default: Int, val min: Int, val max: Int, val step: Int) {
    POSITION("native.home_row_position", "Focused row height", "How far up the screen the active row sits", "%", 42, 14, 80, 2),
    START("native.home_row_start", "Row position", "Distance from the left edge of the screen", " dp", 32, 0, 320, 8),
    SIZE("native.home_row_app_size", "App size", "Scales the size chosen for each row", "%", 100, 50, 200, 5),
    APP_GAP("native.home_row_app_gap", "Space between apps", "Distance between apps in a row", " dp", 24, 0, 96, 4),
    ROW_GAP("native.home_row_gap", "Space between rows", "Distance from one row of apps to the next", " dp", 52, 8, 160, 4),
    BELOW("native.home_row_below_opacity", "Row below opacity", "Visibility of the rows under the active row", "%", 20, 0, 100, 5);

    fun read(prefs: PreferenceAccess) = prefs.int(key, default).coerceIn(min, max)
    fun stepped(value: Int, direction: Int, top: Int = max) = (value + direction * step).coerceIn(min, top)
    fun cycled(value: Int, top: Int = max) = if (value >= top) min else stepped(value, 1, top)
}

internal data class HomeRowLayout(val position: Int, val start: Int, val size: Int, val appGap: Int,
                                  val rowGap: Int, val belowOpacity: Int) {
    fun tileHeight(rowHeight: Int, limit: Int = 500) = (rowHeight.coerceIn(50, 500) * size / 100).coerceAtMost(limit).coerceAtLeast(40)
}

/** Continue Watching's lowest edge in either layout; app rows never rise past it. */
internal const val WATCH_NEXT_BOTTOM = 90f + WATCH_CARD_HEIGHT

/** Row size's fine step; presets stay on Select, and a size outside this range only moves back into it. */
internal fun homeRowSize(height: Int, direction: Int) = (height + direction * 5).coerceIn(50, 300)

// Space LazyRow keeps around tiles so the focus scale is not clipped.
private const val ROW_INSET_TOP = 10f
private const val ROW_INSET_BOTTOM = 8f

/** A row's slot; [rowGap] is measured between tiles, so it can be smaller than the insets. */
internal fun homeRowExtent(tileHeight: Int, showTitles: Boolean, showNames: Boolean, rowGap: Int): Float =
    tileHeight + rowGap + (if (showTitles) 26f else 0f) + (if (showNames) 28f else 0f)

private fun homeRowFrame(showTitles: Boolean, showNames: Boolean) =
    homeRowExtent(0, showTitles, showNames, 0) + ROW_INSET_TOP + ROW_INSET_BOTTOM

/** Where every active row starts, raised only when the tallest row would leave the screen. */
internal fun homeRowTop(screenHeightDp: Float, position: Int, tallestTile: Int, showTitles: Boolean, showNames: Boolean, ceiling: Float = 0f): Float =
    minOf(screenHeightDp * (1f - position / 100f), screenHeightDp - tallestTile - homeRowFrame(showTitles, showNames)).coerceAtLeast(ceiling)

/** The tallest tile that fits between [ceiling] and the bottom of the screen. */
internal fun homeTileLimit(screenHeightDp: Float, ceiling: Float, showTitles: Boolean, showNames: Boolean) =
    (screenHeightDp - ceiling - homeRowFrame(showTitles, showNames)).toInt()

/** The highest Focused row height, on the control's own steps, that keeps the row below [ceiling]. */
internal fun homeRowPositionLimit(screenHeightDp: Float, ceiling: Float): Int {
    val control = RowControl.POSITION
    val highest = ((1f - ceiling / screenHeightDp) * 100).toInt()
    return (control.min + (highest - control.min) / control.step * control.step).coerceIn(control.min, control.max)
}

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
