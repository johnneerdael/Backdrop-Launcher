package nl.neerdael.backdrop_launcher.ui

import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import kotlin.math.roundToInt

internal data class HomeAppearance(
    val accent: Long, val opacity: Float, val borders: Boolean, val borderDp: Float,
    val animateBorder: Boolean, val clickSound: Boolean, val rowTitles: Boolean,
    val appNames: Boolean, val autoHideBar: Boolean, val showDate: Boolean,
    val showTime: Boolean, val showNetwork: Boolean, val dateFormat: String,
    val timeFormat: String, val backAction: String,
) {
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
                prefs.string("date_format", "EEEE d"), prefs.string("time_format", "H:mm"), prefs.string("back_button_action"))
        }
    }
}

internal fun homeRowExtent(viewportHeightDp: Float, rowHeight: Int, showTitles: Boolean, showNames: Boolean): Float =
    maxOf(viewportHeightDp * .72f, rowHeight.coerceIn(50, 500) + 26f + (if (showTitles) 26f else 0f) + (if (showNames) 28f else 0f))
