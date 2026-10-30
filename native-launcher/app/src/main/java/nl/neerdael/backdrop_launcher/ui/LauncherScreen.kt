package nl.neerdael.backdrop_launcher.ui

import android.view.SoundEffectConstants
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.collection.LongSparseArray
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalConfiguration
import android.os.Build
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.packInts
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.neerdael.backdrop_launcher.core.*

internal data class MovingApp(val rowId: Long, val packageName: String)

@Composable
fun LauncherScreen(home: HomeData, library: LibrarySnapshot, prefs: PreferenceAccess, media: MediaAccess,
                   updates: UpdateAccess, actions: UiActions, selection: HomeSelection,
                   artwork: ComposeArtwork, active: Boolean, artworkRevision: Int) {
    val scene = home.scene
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val locale = if (Build.VERSION.SDK_INT >= 24) configuration.locales[0] else configuration.locale
    val language by produceState<Map<String, String>>(emptyMap(), context, locale.language) {
        value = withContext(Dispatchers.IO) { loadUiLanguage(context, locale.language) }
    }
    val scope = rememberCoroutineScope()
    var preferenceVersion by remember(prefs) { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val unsubscribe = prefs.listen { scope.launch { preferenceVersion++ } }
        onDispose { unsubscribe() }
    }
    val style = remember(prefs, preferenceVersion) { HomeAppearance.read(prefs) }
    val font = remember(context) { FontFamily(Font("fonts/Inter.ttf", context.assets)) }
    val density = LocalDensity.current
    val paneHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() } * .42f
    val requesters = remember { LongSparseArray<FocusRequester>() }
    val rowStates = remember { LongSparseArray<LazyListState>() }
    val rowList = rememberLazyListState(initialFirstVisibleItemIndex = selection.app?.row ?: 0)
    val viewport = remember { LayoutBoundsHolder() }
    val toolbar = remember { FocusRequester() }
    val resumeFocus = remember { FocusRequester() }
    var settings by remember { mutableStateOf<SettingsRoute?>(null) }
    var moving by remember { mutableStateOf<MovingApp?>(null) }
    var activeRow by remember { mutableIntStateOf(selection.app?.row ?: 0) }
    var toolbarFocused by remember { mutableStateOf(selection.area == HomeSelection.Area.SETTINGS) }
    var resumeIndex by remember(selection) { mutableIntStateOf(selection.resumeIndex) }
    var focusGeneration by remember { mutableIntStateOf(0) }
    var moveBusy by remember { mutableStateOf(false) }

    suspend fun focusRow(index: Int, animate: Boolean = false) {
        if (scene.rows.isEmpty()) { toolbar.requestFocus(); return }
        val row = index.coerceIn(0, scene.rows.lastIndex)
        val generation = ++focusGeneration
        activeRow = row
        if (animate) rowList.animateScrollToItem(row) else rowList.scrollToItem(row)
        withFrameNanos { }
        val target = selection.focusForRow(scene, row)
        if (target != null) rowStates.get(scene.rows[row].id)?.scrollToItem(target.tile)
        withFrameNanos { }
        if (generation == focusGeneration && settings == null && active) {
            requesters.get(packInts(row, target?.tile ?: -1))?.requestFocus()
        }
    }
    fun appsFocus() { scope.launch { focusRow(selection.app?.row ?: activeRow) } }
    fun openApp(app: AppTile, row: AppRow) { settings = SettingsRoute("app", rowId = row.id, packageName = app.packageName) }
    fun moveApp(direction: Int) {
        val target = moving ?: return
        if (moveBusy) return
        scope.launch {
            moveBusy = true
            try { withContext(Dispatchers.IO) { actions.library.moveApp(target.rowId, target.packageName, direction) }; actions.refresh() }
            catch (failure: Exception) { actions.message(failure.message ?: "Unable to reorder app") }
            finally { moveBusy = false }
        }
    }
    LaunchedEffect(scene.rows, scene.watchNext, active, settings) {
        selection.reconcile(scene)
        resumeIndex = selection.resumeIndex
        if (!active || settings != null) return@LaunchedEffect
        withFrameNanos { }
        when (selection.area) {
            HomeSelection.Area.SETTINGS -> toolbar.requestFocus()
            HomeSelection.Area.WATCH_NEXT -> if (scene.watchNext.isNotEmpty()) resumeFocus.requestFocus() else focusRow(selection.app?.row ?: 0)
            HomeSelection.Area.APPS -> focusRow(selection.app?.row ?: activeRow)
        }
    }
    MaterialTheme {
        CompositionLocalProvider(LocalUiLanguage provides language, LocalTextStyle provides TextStyle(color = Color.White, fontFamily = font,
            shadow = Shadow(Color.Black, blurRadius = 3f))) {
            Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent {
                // Keep background input inert, but let the original confirm UP
                // (including Android's cancelled UP) clear its pressed state.
                if (settings != null) !(it.type == KeyEventType.KeyUp && when (it.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_SPACE,
                    AndroidKeyEvent.KEYCODE_BUTTON_A -> true
                    else -> false
                })
                else if (it.type == KeyEventType.KeyDown && it.key == Key.Menu) { settings = SettingsRoute("settings"); true } else false
            }) {
                Artwork(scene.backdropFor(resumeIndex), artwork, active, artworkRevision,
                    Modifier.fillMaxSize().cinematicScrim(scene.scrim || (scene.bigPicture && scene.watchNext.isNotEmpty())),
                    ContentScale.Crop, retain = false)
                Row(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 32.dp).height(44.dp)
                    .graphicsLayer { alpha = if (!style.autoHideBar || toolbarFocused || settings != null) 1f else 0f },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    UiButton({ if (settings == null) settings = SettingsRoute("settings") }, Modifier.size(40.dp).focusRequester(toolbar)
                        .onFocusChanged { if (it.isFocused && settings == null) { toolbarFocused = true; selection.area = HomeSelection.Area.SETTINGS } }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                                toolbarFocused = false
                                if (scene.watchNext.isNotEmpty()) resumeFocus.requestFocus() else appsFocus()
                                true
                            } else false
                        }.semantics { contentDescription = "Settings" }) { Text("⚙", Modifier.align(Alignment.Center), fontSize = 26.sp) }
                    StatusClock(style, active)
                    if (style.showNetwork) NetworkStatus(active) { if (settings == null) actions.openNetworkSettings() }
                }
                if (scene.watchNext.isNotEmpty()) {
                    val item = scene.watchNext[resumeIndex.coerceIn(0, scene.watchNext.lastIndex)]
                    Column(Modifier.offset(x = 32.dp, y = if (scene.bigPicture) 88.dp else 90.dp)
                        .width(if (scene.bigPicture) 560.dp else 300.dp)) {
                        if (scene.bigPicture) {
                            Text((language["Continue Watching"] ?: "Continue Watching").uppercase(locale), fontSize = 11.sp)
                            Text(item.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.description, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        UiButton({ if (settings == null) actions.play(item) }, Modifier.width(if (scene.bigPicture) 220.dp else 300.dp)
                            .focusRequester(resumeFocus).onFocusChanged {
                                if (it.isFocused && settings == null) { toolbarFocused = false; selection.area = HomeSelection.Area.WATCH_NEXT }
                            }.onPreviewKeyEvent {
                                if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                                    Key.DirectionLeft, Key.DirectionRight -> {
                                        selection.cycle(scene.watchNext.size, if (it.key == Key.DirectionRight) 1 else -1, wrap = scene.bigPicture)
                                        selection.selectResume(scene, selection.resumeIndex)
                                        resumeIndex = selection.resumeIndex
                                        actions.selectResume(scene.watchNext[resumeIndex]); true
                                    }
                                    Key.DirectionDown -> { appsFocus(); true }
                                    Key.DirectionUp -> { toolbar.requestFocus(); true }
                                    else -> false
                                }
                            }.semantics { contentDescription = "Continue watching ${item.title}" }) {
                            Column(Modifier.padding(2.dp)) {
                                if (!scene.bigPicture) {
                                    Artwork(item.image, artwork, active, artworkRevision, Modifier.size(296.dp, 164.dp), ContentScale.Crop)
                                    Text(item.title, Modifier.padding(4.dp), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("▶  " + (language["Continue Watching"] ?: "Continue Watching"), Modifier.padding(4.dp), fontSize = 13.sp)
                                if (item.progress > 0) Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xff333333))) {
                                    Box(Modifier.fillMaxWidth(item.progress.coerceIn(0, 100) / 100f).fillMaxHeight().background(Color(style.accent)))
                                }
                            }
                        }
                        if (scene.bigPicture && scene.watchNext.size > 1) Text("${resumeIndex + 1} / ${scene.watchNext.size}", Modifier.padding(top = 8.dp), fontSize = 13.sp)
                    }
                    if (!scene.bigPicture) scene.watchNext.getOrNull(resumeIndex + 1)?.let { next ->
                        Column(Modifier.offset(x = 432.dp, y = 234.dp).width(100.dp).graphicsLayer { alpha = .1f }) {
                            Artwork(next.image, artwork, active, artworkRevision, Modifier.size(100.dp, 55.dp), ContentScale.Crop)
                            Text(next.title, fontSize = 5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(language["Continue Watching"] ?: "Continue Watching", fontSize = 4.sp)
                            if (next.progress > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(style.accent)))
                        }
                    }
                }
                LazyColumn(state = rowList, userScrollEnabled = false,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(.42f).layoutBounds(viewport)) {
                    itemsIndexed(scene.rows, key = { _, row -> row.id }, contentType = { _, _ -> "home-row" }) { rowIndex, row ->
                        val horizontal = rememberLazyListState(initialFirstVisibleItemIndex = selection.focusForRow(scene, rowIndex)?.tile ?: 0)
                        DisposableEffect(row.id, horizontal) { rowStates.put(row.id, horizontal); onDispose { rowStates.remove(row.id) } }
                        val height = row.height.coerceIn(50, 500).dp
                        val width = height * (16f / 9f)
                        val imageSize = with(density) { IntSize(width.roundToPx(), height.roundToPx()) }
                        Column(Modifier.height(homeRowExtent(paneHeight.value, row.height, style.rowTitles, style.appNames).dp).graphicsLayer { alpha = if (rowIndex == activeRow) 1f else .18f }) {
                            if (style.rowTitles) Text(row.name, Modifier.padding(start = 32.dp, bottom = 8.dp), fontSize = 15.sp)
                            LazyRow(state = horizontal, modifier = Modifier.fillMaxWidth().focusRestorer(),
                                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 10.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                if (row.apps.isEmpty()) item(key = "empty", contentType = "empty-row") {
                                    val focus = remember { FocusRequester() }
                                    DisposableEffect(rowIndex, focus) {
                                        val key = packInts(rowIndex, -1); requesters.put(key, focus)
                                        onDispose { if (requesters.get(key) === focus) requesters.remove(key) }
                                    }
                                    UiButton({ if (settings == null) settings = SettingsRoute("row", rowId = row.id) },
                                        Modifier.focusRequester(focus).focusProperties { canFocus = rowIndex == activeRow }
                                            .onPreviewKeyEvent {
                                                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionUp || it.key == Key.DirectionDown)) {
                                                    if (it.key == Key.DirectionUp && rowIndex == 0) toolbar.requestFocus()
                                                    else scope.launch { focusRow(rowIndex + if (it.key == Key.DirectionDown) 1 else -1, true) }; true
                                                } else false
                                            }) { Text("${row.name} · Add apps", Modifier.padding(20.dp)) }
                                }
                                itemsIndexed(row.apps, key = { _, app -> app.packageName }, contentType = { _, _ -> "app" }) { tileIndex, app ->
                                    val focus = remember { FocusRequester() }
                                    val slot = packInts(rowIndex, tileIndex)
                                    DisposableEffect(slot, focus) { requesters.put(slot, focus); onDispose { if (requesters.get(slot) === focus) requesters.remove(slot) } }
                                    Column(Modifier.width(width)) {
                                        AppCardSurface(onClick = {
                                            if (settings == null) {
                                                if (moving != null) moving = null else { if (style.clickSound) view.playSoundEffect(SoundEffectConstants.CLICK); actions.launch(app.packageName) }
                                            }
                                        }, onLongClick = { if (settings == null) { if (moving != null) moving = null else openApp(app, row) } },
                                            color = Color(style.accent).copy(alpha = style.opacity), borderColor = Color(style.accent),
                                            borderWidth = style.borderDp.dp, showBorder = style.borders || moving?.packageName == app.packageName,
                                            animateBorder = style.animateBorder, active = active,
                                            modifier = Modifier.size(width, height).focusRequester(focus)
                                                // A separate dialog window owns modal focus. Keep
                                                // its origin eligible so dismiss restores this app.
                                                .focusProperties { canFocus = rowIndex == activeRow }
                                                .onFocusChanged { if (it.isFocused && settings == null) { toolbarFocused = false; selection.remember(scene, rowIndex, tileIndex) } }
                                                .onPreviewKeyEvent {
                                                    if (it.type != KeyEventType.KeyDown) false else when {
                                                        moving?.packageName == app.packageName && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight) -> { moveApp(if (it.key == Key.DirectionLeft) -1 else 1); true }
                                                        it.key == Key.DirectionUp && rowIndex == 0 -> {
                                                            if (scene.watchNext.isNotEmpty()) resumeFocus.requestFocus() else toolbar.requestFocus(); true
                                                        }
                                                        it.key == Key.DirectionUp || it.key == Key.DirectionDown -> {
                                                            val next = (rowIndex + if (it.key == Key.DirectionDown) 1 else -1).coerceIn(0, scene.rows.lastIndex)
                                                            if (next != rowIndex) scope.launch { focusRow(next, true) }; true
                                                        }
                                                        else -> false
                                                    }
                                                }.semantics { contentDescription = app.label }) {
                                            Artwork(app.artwork ?: "package:${app.packageName}", artwork, active, artworkRevision,
                                                Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), ContentScale.Fit,
                                                decodeSize = imageSize, viewport = viewport)
                                        }
                                        if (style.appNames) Text(app.label, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                if (scene.rows.isEmpty()) UiButton({ if (settings == null) settings = SettingsRoute("rows") }, Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                    Text("Add an application row", Modifier.padding(16.dp))
                }
                moving?.let { Text("Reorder · Left/Right to move · Select or Back to finish", Modifier.align(Alignment.BottomCenter).padding(12.dp), fontSize = 15.sp) }
            }
            settings?.let { route -> LauncherSettings(route, library, prefs, media, updates, actions, artwork, active, artworkRevision,
                close = { settings = null }, reorder = { rowId, packageName -> moving = MovingApp(rowId, packageName); settings = null }) }
            BackHandler(settings == null) {
                if (moving != null) moving = null else actions.homeBack(style.backAction)
            }
        }
    }
}

private fun Modifier.cinematicScrim(enabled: Boolean): Modifier = if (!enabled) this else drawWithCache {
    val vertical = Brush.verticalGradient(0f to Color(0x59000000), .25f to Color.Transparent, .65f to Color(0x80000000), 1f to Color(0xe0000000))
    val horizontal = Brush.horizontalGradient(0f to Color(0xa6000000), .35f to Color(0x40000000), .7f to Color.Transparent)
    onDrawWithContent { drawContent(); drawRect(vertical); drawRect(horizontal) }
}
