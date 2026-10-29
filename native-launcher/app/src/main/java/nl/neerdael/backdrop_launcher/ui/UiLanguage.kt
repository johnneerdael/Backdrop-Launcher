package nl.neerdael.backdrop_launcher.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject

internal val LocalUiLanguage = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** Reuse the shipped language packs; never translate application/row/catalogue names. */
internal fun loadUiLanguage(context: Context, language: String): Map<String, String> {
    if (language !in setOf("es", "tr", "vi")) return emptyMap()
    return runCatching {
        val english = context.assets.open("l10n/app_en.arb").bufferedReader().use { JSONObject(it.readText()) }
        val target = context.assets.open("l10n/app_$language.arb").bufferedReader().use { JSONObject(it.readText()) }
        buildMap {
            english.keys().forEach { key ->
                if (!key.startsWith("@") && english.opt(key) is String && target.opt(key) is String) {
                    val source = english.getString(key)
                    if ('{' !in source) put(source, target.getString(key))
                }
            }
            val aliases = mapOf("Continue Watching" to "watchNextSectionTitle", "Show Continue Watching" to "showWatchNextSection",
                "Big picture / hero mode" to "watchNextBigPictureMode", "Hide items without images" to "watchNextHideWithoutImages",
                "Maximum items" to "watchNextMaxItems", "Maximum Continue Watching items" to "watchNextMaxItems",
                "Grant TV listings permission" to "watchNextGrantPermission", "Refresh Continue Watching" to "watchNextRefresh",
                "App info" to "appInfo", "App key click sound" to "appKeyClick", "Animated focus border" to "appCardHighlightAnimation",
                "Show app names below icons" to "showAppNamesBelowIcons", "Auto-hide status bar" to "autoHideAppBar",
                "Show date" to "date", "Show time" to "time", "Show network indicator" to "networkIndicator",
                "Date and time formats" to "dateAndTimeFormat", "Screensaver clock style" to "screensaverClockStyle",
                "Android screensaver settings" to "screensaverSettings", "Start screensaver" to "dialogOptionBackButtonActionShowScreensaver",
                "Choose packaged icon / banner" to "setCustomBanner", "Restore application artwork" to "clearCustomBanner")
            aliases.forEach { (label, key) -> if (target.opt(key) is String) put(label, target.getString(key)) }
        }
    }.getOrDefault(emptyMap())
}
