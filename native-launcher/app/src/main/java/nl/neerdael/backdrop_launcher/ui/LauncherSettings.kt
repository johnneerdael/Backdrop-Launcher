package nl.neerdael.backdrop_launcher.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.neerdael.backdrop_launcher.core.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

internal data class SettingsRoute(val section: String, val rowId: Long? = null, val packageName: String? = null)

@Composable
internal fun LauncherSettings(initial: SettingsRoute, library: LibrarySnapshot, prefs: PreferenceAccess,
    media: MediaAccess, updates: UpdateAccess, actions: UiActions, artwork: ComposeArtwork,
    active: Boolean, artworkRevision: Int, close: () -> Unit, reorder: (Long, String) -> Unit,
    maxRowPosition: Int = RowControl.POSITION.max) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val locale = if (Build.VERSION.SDK_INT >= 24) configuration.locales[0] else configuration.locale
    val scope = rememberCoroutineScope()
    var stack by remember(initial) { mutableStateOf(listOf(initial)) }
    val page = stack.last()
    var model by remember(library) { mutableStateOf(library) }
    var version by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf<TextPrompt?>(null) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var movingRow by remember { mutableStateOf<Long?>(null) }
    var assets by remember { mutableStateOf<List<String>>(emptyList()) }
    var memberships by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    var catalogs by remember { mutableStateOf<List<CatalogOption>>(emptyList()) }
    var selectedCatalogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var auth by remember { mutableStateOf<DeviceAuthorization?>(null) }
    var authStatus by remember { mutableStateOf("") }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var appVersion by remember { mutableStateOf("") }
    var appCode by remember { mutableLongStateOf(0) }
    val currentAuth by rememberUpdatedState(auth)
    val rowLayoutWrites = remember { Dispatchers.IO.limitedParallelism(1) }

    fun go(section: String, rowId: Long? = null, packageName: String? = null) {
        error = null; stack = stack + SettingsRoute(section, rowId, packageName)
    }
    fun back() {
        if (page.section == "auth" && auth != null) {
            auth = null
            busy = "Cancelling login…"
            scope.launch {
                try { withContext(Dispatchers.IO) { media.cancelAuthorization() } }
                finally { busy = null }
            }
        }
        if (stack.size > 1) { error = null; stack = stack.dropLast(1) } else close()
    }
    fun <T> execute(label: String, reload: Boolean = false, refresh: Boolean = true,
                    task: () -> T, done: (T) -> Unit = {}) {
        if (busy != null) return
        scope.launch {
            busy = label; error = null
            try {
                val result = withContext(Dispatchers.IO) { task() }
                if (reload) model = withContext(Dispatchers.IO) { actions.library.snapshot(false) }
                done(result)
                version++
                if (refresh) actions.refresh()
            } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
            catch (failure: Exception) { error = failure.message ?: "Unable to complete this action. Try again." }
            finally { busy = null }
        }
    }
    fun change(task: () -> Unit) = execute("Saving…", task = task)
    // Held Left/Right saves in order without the busy state, which would swallow the next repeat.
    fun adjust(control: RowControl, value: Int) {
        scope.launch(Dispatchers.Main.immediate) {
            try { withContext(rowLayoutWrites) { prefs.putInt(control.key, value) }; version++ }
            catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
            catch (failure: Exception) { error = failure.message ?: "Unable to complete this action. Try again." }
        }
    }
    fun mutate(task: () -> Unit) = execute("Saving applications…", reload = true, task = task)
    fun toggle(key: String, title: String, default: Boolean = false, enabled: Boolean = true): SettingsEntry =
        SettingsEntry(key, title, checked = prefs.bool(key, default), enabled = enabled, control = SettingsControl.SWITCH) {
            change {
                val next = !prefs.bool(key, default)
                prefs.putBool(key, next)
                if (key == "show_focus_borders" && !next) prefs.putBool("app_highlight_animation_enabled", false)
            }
        }
    fun choice(id: String, title: String, value: String, action: () -> Unit) = SettingsEntry(id, title, value = value, onClick = action)
    fun checkUpdates() = execute("Checking for updates…", refresh = false, task = { updates.check() }) {
        update = it; downloaded = null
        updateStatus = if (it == null) "No newer published release is available." else "Latest release: ${it.version}"
        go("update")
    }

    LaunchedEffect(context) {
        val info = withContext(Dispatchers.IO) { context.packageManager.getPackageInfo(context.packageName, 0) }
        appVersion = info.versionName.orEmpty()
        @Suppress("DEPRECATION")
        appCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    LaunchedEffect(page, model, version) {
        try {
        if (page.section in setOf("row", "memberships")) memberships = withContext(Dispatchers.IO) {
            model.rows.associate { it.id to actions.library.rowPackages(it.id) }
        }
        if (page.section == "artwork" || page.section == "bundled") {
            val folder = if (page.section == "artwork") "appicons" else "backdrops"
            assets = withContext(Dispatchers.IO) {
                context.assets.list(folder).orEmpty().filter { it.matches(Regex("(?i).+\\.(png|jpe?g|webp)")) }.sorted().map { "$folder/$it" }
            }
        }
        if (page.section == "catalogs") {
            val loaded = withContext(Dispatchers.IO) { media.catalogs() to media.selectedCatalogs() }
            catalogs = loaded.first; selectedCatalogs = loaded.second
        }
        } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
        catch (failure: Exception) { error = failure.message ?: "Unable to load these settings" }
    }
    LaunchedEffect(auth, active) {
        val pending = auth ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        try {
            while (System.currentTimeMillis() < pending.expiresAtMillis) {
                delay(pending.intervalSeconds.coerceIn(1, 600) * 1000L)
                when (val result = withContext(Dispatchers.IO) { media.pollAuthorization(pending) }) {
                    AuthorizationResult.PENDING, AuthorizationResult.SLOW_DOWN -> authStatus = "Waiting for approval…"
                    AuthorizationResult.APPROVED -> {
                        withContext(Dispatchers.IO) { prefs.putBool("trakt_backdrops_enabled", true) }
                        authStatus = "Trakt connected"; auth = null; version++; actions.refresh(); break
                    }
                    else -> { authStatus = result.name.lowercase().replace('_', ' '); auth = null; break }
                }
            }
            if (auth === pending && System.currentTimeMillis() >= pending.expiresAtMillis) { auth = null; authStatus = "Activation code expired. Connect again." }
        } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
        catch (failure: Exception) { authStatus = failure.message ?: "Unable to contact Trakt"; auth = null }
    }
    DisposableEffect(media) {
        onDispose { if (currentAuth != null) scope.launch(NonCancellable + Dispatchers.IO) { media.cancelAuthorization() } }
    }

    // Read the saved values once for each successful local change as well as parent updates.
    @Suppress("UNUSED_VARIABLE") val savedRevision = version
    val row = model.rows.firstOrNull { it.id == page.rowId }
    val app = model.apps.firstOrNull { it.packageName == page.packageName }
    val entries = mutableListOf<SettingsEntry>()
    var title = "Settings"
    var note: String? = null
    when (page.section) {
        "settings" -> {
            entries += SettingsEntry("general", "General settings", section = true) {}
            entries += SettingsEntry("apps", "Applications", icon = SettingsIcon.APPS) { go("rows") }
            entries += SettingsEntry("interface", "Interface", icon = SettingsIcon.INTERFACE) { go("interface") }
            entries += SettingsEntry("system", "System", icon = SettingsIcon.SYSTEM) { go("system") }
            entries += SettingsEntry("android", "System settings", icon = SettingsIcon.SETTINGS) { close(); actions.systemSettings() }
            if (updates.enabled) entries += SettingsEntry("check", "Check for updates", icon = SettingsIcon.UPDATE, onClick = ::checkUpdates)
            entries += SettingsEntry("about", "About Backdrop Launcher", icon = SettingsIcon.INFO) { go("about") }
        }
        "interface" -> {
            title = "Interface"
            for ((id, label) in listOf("watch" to "Continue Watching", "backdrops" to "Backdrops", "status" to "Status bar", "accent" to "Accent color", "appearance" to "Appearance", "rowlayout" to "App row layout", "misc" to "Miscellaneous"))
                entries += SettingsEntry(id, label) { go(id) }
        }
        "rows" -> {
            title = "Applications"
            model.rows.sortedBy { it.order }.forEach { item ->
                entries += SettingsEntry("row:${item.id}", item.name, localize = false,
                    subtitle = if (movingRow == item.id) "Up/Down to move · Select to finish" else "${item.apps.size} apps · ${if (item.hidden) "Hidden · " else ""}Select to move · Hold/Right to edit",
                    onHold = { movingRow = null; go("row", rowId = item.id) }, onKey = { event ->
                        if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) false
                        else if (event.key == androidx.compose.ui.input.key.Key.DirectionRight) { movingRow = null; go("row", rowId = item.id); true }
                        else if (movingRow == item.id && (event.key == androidx.compose.ui.input.key.Key.DirectionUp || event.key == androidx.compose.ui.input.key.Key.DirectionDown)) {
                            mutate { actions.library.moveRow(item.id, if (event.key == androidx.compose.ui.input.key.Key.DirectionUp) -1 else 1) }; true
                        } else false
                    }) { movingRow = if (movingRow == item.id) null else item.id }
            }
            entries += SettingsEntry("add", "Add row") { prompt = TextPrompt("Add row") { name -> execute("Creating row…", reload = true, task = { actions.library.createRow(name) }) { go("row", rowId = it) } } }
            entries += SettingsEntry("inventory", "Installed apps", subtitle = "Show, hide and manage TV and Android applications") { go("inventory") }
        }
        "row" -> {
            title = row?.name ?: "Row no longer exists"
            if (row != null) {
                entries += choice("rename", "Row name", row.name) { prompt = TextPrompt("Row name", row.name) { name -> mutate { actions.library.renameRow(row.id, name) } } }
                entries += choice("sort", "App order", listOf("Manual", "Alphabetical", "Last used").getOrElse(row.sort) { "Manual" }) {
                    mutate { actions.library.updateRow(row.id, row.hidden, (row.sort + 1) % 3, row.height) }
                }
                entries += SettingsEntry("size", "Row size", subtitle = "Left/Right for fine steps · Select for 82, 110 or 140", value = row.height.toString(), onKey = { event ->
                    if (event.type != KeyEventType.KeyDown || (event.key != Key.DirectionLeft && event.key != Key.DirectionRight)) false
                    else { mutate { actions.library.updateRow(row.id, row.hidden, row.sort, homeRowSize(row.height, if (event.key == Key.DirectionRight) 1 else -1)) }; true }
                }) {
                    val sizes = listOf(82, 110, 140); val next = sizes.firstOrNull { it > row.height } ?: sizes.first()
                    mutate { actions.library.updateRow(row.id, row.hidden, row.sort, next) }
                }
                entries += SettingsEntry("visible", "Show on home screen", checked = !row.hidden, control = SettingsControl.SWITCH) { mutate { actions.library.updateRow(row.id, !row.hidden, row.sort, row.height) } }
                entries += SettingsEntry("up", "Move row up") { mutate { actions.library.moveRow(row.id, -1) } }
                entries += SettingsEntry("down", "Move row down") { mutate { actions.library.moveRow(row.id, 1) } }
                entries += SettingsEntry("delete", "Delete row") { confirmation = Confirmation("Delete ${row.name}?", "Applications stay installed. Only this row and its membership are removed.", "Delete") {
                    execute("Deleting row…", reload = true, task = { actions.library.deleteRow(row.id) }) { back() }
                } }
                model.apps.forEach { member -> entries += SettingsEntry("member:${member.packageName}", member.label, localize = false,
                    subtitle = (if (member.androidApp) "Android app" else "TV app") + if (member.hidden) " · Hidden on home screen" else "",
                    artwork = member.artwork ?: "package:${member.packageName}", checked = member.packageName in memberships[row.id].orEmpty(),
                    enabled = row.id in memberships) { mutate { toggleRowMembership(actions.library, row.id, member.packageName) } } }
            }
        }
        "inventory" -> {
            title = "Installed apps"
            model.apps.forEach { item -> entries += SettingsEntry(item.packageName, item.label, localize = false,
                subtitle = "${if (item.androidApp) "Android" else "TV"} · ${if (item.hidden) "Hidden" else "Shown"}",
                artwork = item.artwork ?: "package:${item.packageName}") { go("app", packageName = item.packageName) } }
            if (model.apps.isEmpty()) note = "No installed apps."
        }
        "app" -> {
            title = app?.label ?: "App no longer installed"
            if (app != null) {
                note = "${app.packageName} · v${app.version}"
                entries += SettingsEntry("open", "Open") { close(); actions.launch(app.packageName) }
                entries += SettingsEntry("hidden", if (app.hidden) "Show on home screen" else "Hide from home screen") { mutate { actions.library.setHidden(app.packageName, !app.hidden) } }
                entries += SettingsEntry("memberships", "Add to / remove from rows") { go("memberships", packageName = app.packageName) }
                if (row != null) {
                    entries += SettingsEntry("remove", "Remove from ${row.name}") { mutate { val members = actions.library.rowPackages(row.id); actions.library.setRowApps(row.id, members.filter { it != app.packageName }) } }
                    if (row.sort == 0) entries += SettingsEntry("reorder", "Reorder in ${row.name}", subtitle = "Left/Right to move · Select to finish") { reorder(row.id, app.packageName) }
                }
                entries += SettingsEntry("artwork", "Choose packaged icon / banner") { go("artwork", packageName = app.packageName) }
                if (app.artwork != null) entries += SettingsEntry("reset", "Restore application artwork") { mutate { actions.library.setArtwork(app.packageName, null) } }
                entries += SettingsEntry("info", "App info") { actions.appInfo(app.packageName) }
                entries += SettingsEntry("uninstall", "Uninstall") { actions.uninstall(app.packageName) }
            }
        }
        "memberships" -> {
            title = "Rows for ${app?.label.orEmpty()}"
            if (app != null) model.rows.forEach { item -> entries += SettingsEntry("membership:${item.id}", item.name, localize = false,
                checked = app.packageName in memberships[item.id].orEmpty(), enabled = item.id in memberships) { mutate { toggleRowMembership(actions.library, item.id, app.packageName) } } }
        }
        "artwork", "bundled" -> {
            title = if (page.section == "artwork") "Choose app icon / banner" else "Included backdrops"
            assets.forEach { asset -> entries += SettingsEntry(asset, asset.substringAfterLast('/').substringBeforeLast('.').replace('-', ' '), artwork = asset, localize = false) {
                if (page.section == "artwork" && app != null) execute("Saving artwork…", reload = true, task = { actions.library.setArtwork(app.packageName, asset) }) { back() }
                else execute("Setting backdrop…", task = { media.chooseBundledBackdrop(asset) }) { actions.showBackdrop(it); back() }
            } }
            if (assets.isEmpty()) note = "Loading included artwork…"
        }
        "appearance" -> {
            title = "Appearance"
            entries += toggle("show_focus_borders", "Show focus borders", true)
            entries += choice("border", "Focus border thickness", "${prefs.double("focus_border_thickness", 1.5)} dp") { go("border") }
            entries += choice("opacity", "App icon background opacity", "${(prefs.double("app_icon_background_opacity", .1) * 100).toInt()}%") { go("opacity") }
        }
        "rowlayout" -> {
            title = "App row layout"
            note = "Left and Right change the selected setting. Home updates as you go."
            RowControl.entries.forEach { control ->
                val top = if (control == RowControl.POSITION) maxRowPosition else control.max
                fun current() = control.read(prefs).coerceAtMost(top)
                entries += SettingsEntry(control.key, control.title, value = "${current()}${control.unit}",
                    subtitle = if (top < control.max) "Stops just below Continue Watching" else control.subtitle, onKey = { event ->
                    if (event.type != KeyEventType.KeyDown || (event.key != Key.DirectionLeft && event.key != Key.DirectionRight)) false
                    else { adjust(control, control.stepped(current(), if (event.key == Key.DirectionRight) 1 else -1, top)); true }
                }) { adjust(control, control.cycled(current(), top)) }
            }
            entries += SettingsEntry("reset", "Reset app row layout") { change { RowControl.entries.forEach { prefs.remove(it.key) } } }
        }
        "border" -> {
            title = "Focus border thickness"
            listOf(1.0, 1.5, 2.0, 3.0).forEach { size -> entries += SettingsEntry("border:$size", "$size dp", control = SettingsControl.RADIO, checked = prefs.double("focus_border_thickness", 1.5) == size) { change { prefs.putDouble("focus_border_thickness", size) } } }
        }
        "opacity" -> {
            title = "App icon background opacity"
            (0..10).forEach { value -> entries += SettingsEntry("opacity:$value", "${value * 10}%", control = SettingsControl.RADIO, checked = kotlin.math.abs(prefs.double("app_icon_background_opacity", .1) - value / 10.0) < .05) { change { prefs.putDouble("app_icon_background_opacity", value / 10.0) } } }
        }
        "misc" -> {
            title = "Miscellaneous"
            entries += toggle("app_highlight_animation_enabled", "Animated focus border", enabled = prefs.bool("show_focus_borders", true))
            entries += toggle("app_key_click_enabled", "App key click sound", true)
            entries += toggle("show_category_titles", "Show category titles")
            entries += toggle("show_app_names_below_icons", "Show app names below icons")
            entries += toggle("show_focus_borders", "Show focus borders", true)
        }
        "accent" -> {
            title = "Accent color"
            val colors = listOf("FFFFFF" to "White", "9E9E9E" to "Grey", "000000" to "Black", "7C4DFF" to "Purple", "00BFA5" to "Teal", "2979FF" to "Blue", "FF6D00" to "Orange", "F50057" to "Pink", "00C853" to "Green", "FFD600" to "Yellow", "D50000" to "Red", "00E5FF" to "Cyan", "536DFE" to "Indigo", "AEEA00" to "Lime", "FFAB00" to "Amber", "FF4081" to "Rose", "80D8FF" to "Ice blue")
            colors.forEach { (color, label) -> entries += SettingsEntry(color, label, control = SettingsControl.RADIO, checked = prefs.string("accent_color", "FFFFFF") == color) { change { prefs.putString("accent_color", color) } } }
            entries += SettingsEntry("custom", "Custom hex color", subtitle = "Six hexadecimal digits, for example A78BFA") { prompt = TextPrompt("Accent color", prefs.string("accent_color", "FFFFFF")) { hex -> change {
                val value = hex.removePrefix("#"); require(value.matches(Regex("[0-9a-fA-F]{6}"))) { "Enter exactly six hexadecimal digits" }; prefs.putString("accent_color", value.uppercase())
            } } }
        }
        "status" -> {
            title = "Status bar"
            entries += toggle("auto_hide_app_bar", "Auto-hide status bar")
            entries += toggle("show_date_in_status_bar", "Show date")
            entries += toggle("show_time_in_status_bar", "Show time", true)
            entries += toggle("show_network_indicator_in_status_bar", "Show network indicator", true)
        }
        "system" -> {
            title = "System"
            entries += SettingsEntry("saver", "Android screensaver settings", onClick = actions::screensaverSettings)
            entries += SettingsEntry("style", "Screensaver clock style") { go("clockstyle") }
            entries += SettingsEntry("preview", "Preview clock", onClick = actions::showClock)
            entries += SettingsEntry("formats", "Date and time formats") { go("formats") }
            entries += SettingsEntry("back", "Back button action") { go("back") }
            entries += SettingsEntry("network", "Network settings", onClick = actions::openNetworkSettings)
        }
        "clockstyle" -> {
            title = "Screensaver clock style"
            listOf("minimal", "bold", "retro", "elegant", "neon", "pixel", "digital").forEach { value -> entries += SettingsEntry(value, value.replaceFirstChar { it.uppercase() }, control = SettingsControl.RADIO, checked = prefs.string("screensaver_clock_style", "minimal") == value) { change { prefs.putString("screensaver_clock_style", value) } } }
        }
        "back" -> {
            title = "Back button action"
            listOf("" to "Do nothing", "CLOCK" to "Show clock", "SCREENSAVER" to "Start screensaver").forEach { (value, label) -> entries += SettingsEntry("back:$value", label, control = SettingsControl.RADIO, checked = prefs.string("back_button_action") == value) { change { prefs.putString("back_button_action", value) } } }
        }
        "formats" -> {
            title = "Date and time formats"
            val dateOptions = listOf("EEEE d", "E d", "dd/MM/y", "MMM d, y", "d MMMM", "M/d/y")
            val timeOptions = listOf("H:mm", "hh:mm", "h:mm a", "hh:mm a", "HH:mm")
            listOf("date_format" to dateOptions, "time_format" to timeOptions).forEach { (key, formats) ->
                val current = prefs.string(key, if (key == "date_format") "EEEE d" else "H:mm")
                formats.forEach { format -> entries += SettingsEntry("$key:$format", format, control = SettingsControl.RADIO, checked = current == format,
                    subtitle = SimpleDateFormat(format, locale).format(java.util.Date())) { change { prefs.putString(key, format) } } }
                entries += SettingsEntry("custom:$key", if (key == "date_format") "Custom date format" else "Custom time format", value = current) { prompt = TextPrompt("Format pattern", current) { format -> change { SimpleDateFormat(format, locale); prefs.putString(key, format) } } }
            }
        }
        "watch" -> {
            title = "Continue Watching"
            entries += toggle("show_watch_next_section", "Show Continue Watching")
            entries += SettingsEntry("permission", "Grant TV listings permission", subtitle = "Allows access to the TV's public Continue Watching provider", onClick = actions::requestWatchNextPermission)
            entries += toggle("watch_next_big_picture_mode", "Big picture / hero mode")
            entries += toggle("watch_next_hide_programs_without_images", "Hide items without images")
            entries += choice("count", "Maximum items", prefs.int("watch_next_max_items").let { if (it == 0) "Unlimited" else it.toString() }) { go("watchcount") }
            entries += SettingsEntry("refresh", "Refresh Continue Watching") { execute("Refreshing Continue Watching…", task = { media.watchNext() }) { actions.showWatchNext(it); actions.message("Continue Watching refreshed") } }
            val excluded = prefs.strings("watch_next_excluded_packages").toSet()
            model.apps.forEach { item -> entries += SettingsEntry("filter:${item.packageName}", item.label, subtitle = "Include this app in Continue Watching", checked = item.packageName !in excluded) { change {
                val next = prefs.strings("watch_next_excluded_packages").toMutableSet(); if (!next.add(item.packageName)) next.remove(item.packageName); prefs.putStrings("watch_next_excluded_packages", next.toList())
            } } }
        }
        "watchcount" -> {
            title = "Maximum Continue Watching items"
            listOf(0, 5, 10, 15, 20, 25, 30, 50).forEach { value -> entries += SettingsEntry("count:$value", if (value == 0) "Unlimited" else value.toString(), control = SettingsControl.RADIO, checked = prefs.int("watch_next_max_items") == value) { change { prefs.putInt("watch_next_max_items", value) } } }
        }
        "backdrops" -> {
            title = "Backdrops"
            entries += SettingsEntry("next", "Next backdrop") { execute("Loading backdrop…", task = { media.nextBackdrop() }) { actions.showBackdrop(it) } }
            entries += SettingsEntry("bundled", "Choose included backdrop", subtitle = "10 included cinematic backdrops") { go("bundled") }
            entries += choice("interval", "Change backdrop every", "${prefs.int("backdrop_interval_minutes", 10)} min") { go("interval") }
            entries += toggle("backdrop_refresh_on_resume", "Refresh backdrop on return", true)
            entries += SettingsEntry("connect", if (media.connected) "Trakt connected" else "Connect Trakt", enabled = media.configured && !media.connected,
                subtitle = if (media.configured) "Choose movie and TV backdrops from Trakt" else "Trakt is not configured in this build") { execute("Requesting Trakt code…", refresh = false, task = { media.beginAuthorization() }) { auth = it; authStatus = "Waiting for approval…"; go("auth") } }
            entries += toggle("trakt_backdrops_enabled", "Use Trakt backdrops", enabled = media.connected)
            entries += SettingsEntry("catalogs", "Catalogs & popular lists", enabled = media.connected) { go("catalogs") }
            if (media.connected) entries += SettingsEntry("disconnect", "Disconnect Trakt") { change { prefs.putBool("trakt_backdrops_enabled", false); media.disconnect() } }
            note = "Backdrops provided by TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB."
        }
        "interval" -> {
            title = "Change backdrop every"
            listOf(5, 10, 30, 60).forEach { value -> entries += SettingsEntry("interval:$value", "$value minutes", control = SettingsControl.RADIO, checked = prefs.int("backdrop_interval_minutes", 10) == value) { change { prefs.putInt("backdrop_interval_minutes", value) } } }
        }
        "auth" -> {
            title = "Connect Trakt"
            if (auth != null) entries += SettingsEntry("cancel", "Cancel device login") { execute("Cancelling login…", refresh = false, task = { media.cancelAuthorization() }) { auth = null; back() } }
            else {
                if (!media.connected) entries += SettingsEntry("retry", "Try again") { execute("Requesting Trakt code…", refresh = false, task = { media.beginAuthorization() }) { auth = it; authStatus = "Waiting for approval…" } }
                entries += SettingsEntry("done", "Back to backdrops") { back() }
            }
        }
        "catalogs" -> {
            title = "Trakt catalogs & lists"
            entries += SettingsEntry("popular", "Fetch popular Trakt lists", enabled = media.connected) { execute("Fetching popular lists…", refresh = false, task = { media.refreshPopularLists(); media.catalogs() to media.selectedCatalogs() }) { catalogs = it.first; selectedCatalogs = it.second } }
            catalogs.forEach { catalog -> entries += SettingsEntry(catalog.key, catalog.title, localize = false,
                subtitle = if (catalog.itemCount > 0) "${catalog.itemCount} items" else "", checked = catalog.key in selectedCatalogs) {
                val next = selectedCatalogs.toMutableSet(); if (!next.add(catalog.key)) next.remove(catalog.key)
                execute("Saving catalogs…", task = { media.selectCatalogs(next.toList()) }) { selectedCatalogs = next.toList() }
            } }
            if (selectedCatalogs.isEmpty()) note = "Select at least one catalog or list to use Trakt backdrops."
        }
        "update" -> {
            title = "Updates"
            note = updateStatus
            entries += SettingsEntry("check", "Check again", enabled = updates.enabled, onClick = ::checkUpdates)
            update?.let { latest ->
                if (latest.code > appCode) entries += SettingsEntry("download", "Download ${latest.version}", subtitle = "Read the release notes below before downloading") {
                    execute("Downloading and verifying update…", refresh = false, task = { updates.download(latest) }) { file ->
                        downloaded = file
                        confirmation = Confirmation("Install ${latest.version}?", "Android will ask for any required permission and installation confirmation. Your launcher data stays in place.", "Continue to Android installer") { actions.installApk(file) }
                    }
                }
                downloaded?.let { file -> entries += SettingsEntry("install", "Open Android installer") { confirmation = Confirmation("Install update?", "Continue to Android's installer to review and confirm this update.", "Continue") { actions.installApk(file) } } }
            }
        }
        "about" -> {
            title = "About Backdrop Launcher"
            note = "Backdrop Launcher v$appVersion ($appCode)\n\nYour TV. Your way.\nCustomized by John Neerdael\nBased on Arc Launcher by Badis Meddouri and LTvLauncher © 2026 LeanBitLab; FLauncher © Étienne Fesser.\n\nGNU General Public License version 3 or later.\nhttps://github.com/johnneerdael/Backdrop-Launcher"
            if (updates.enabled) entries += SettingsEntry("check", "Check for updates", icon = SettingsIcon.UPDATE, onClick = ::checkUpdates)
        }
    }
    Dialog(onDismissRequest = { if (busy == null) back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Paint one measured scrim; the platform Dialog must not apply another dim layer.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        // App row layout previews on Home, so that page leaves it undimmed.
        Box(Modifier.fillMaxSize().background(if (page.section == "rowlayout") Color.Transparent else NativeSettingsStyle.scrim)) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                SettingsPanel(title, entries, artwork, active, artworkRevision, busy, error, translateTitle = page.section !in setOf("row", "app", "memberships"),
                    artworkGrid = page.section == "artwork",
                    lead = if (page.section == "auth") { { TraktActivation(auth, authStatus) } } else null) {
                    if (!note.isNullOrBlank()) Text(note, Modifier.padding(top = 10.dp), style = NativeSettingsStyle.summaryStyle)
                    if (page.section == "update" && !update?.notes.isNullOrBlank()) ReadableText(update!!.notes, Modifier.padding(top = 10.dp))
                }
            }
        }
        BackHandler { if (busy == null) back() }
    }
    prompt?.let { value -> PromptDialog(value) { prompt = null } }
    confirmation?.let { value -> ConfirmationDialog(value) { confirmation = null } }
}

/** Read raw ordered membership before changing it; globally hidden entries must survive edits. */
internal fun toggleRowMembership(library: LibraryAccess, rowId: Long, packageName: String) {
    val members = library.rowPackages(rowId).toMutableList()
    if (!members.remove(packageName)) members.add(packageName)
    library.setRowApps(rowId, members)
}
