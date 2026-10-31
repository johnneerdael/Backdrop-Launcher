package nl.neerdael.backdrop_launcher.ui

import android.view.SoundEffectConstants
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.collection.LongSparseArray
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
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

private const val ROW_MOTION_MILLIS = 280

/** Focus never scrolls the row list: Android TV's pivot would pull rows off their shared line. */
@OptIn(ExperimentalFoundationApi::class)
private object FixedHomeRows : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

/** Android TV's 30% pivot, moved in when the row starts further right so focus cannot undo Row position. */
@OptIn(ExperimentalFoundationApi::class)
private class HomeRowPivot(private val start: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) =
        offset - maxOf(containerSize * .3f, start)
}

@OptIn(ExperimentalFoundationApi::class)
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
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val rowLayout = style.rows
    val rowCeiling = if (scene.watchNext.isNotEmpty()) WATCH_NEXT_BOTTOM else 0f
    val tileLimit = homeTileLimit(screenHeight.value, rowCeiling, style.rowTitles, style.appNames)
    val rowExtents = remember(scene.rows, style, tileLimit) {
        scene.rows.map { homeRowExtent(rowLayout.tileHeight(it.height, tileLimit), style.rowTitles, style.appNames, rowLayout.rowGap) }
    }
    val rowTop = homeRowTop(screenHeight.value, rowLayout.position, rowLayout.tileHeight(scene.rows.maxOfOrNull { it.height } ?: 0, tileLimit),
        style.rowTitles, style.appNames, rowCeiling).dp
    val rowPivot = remember(rowLayout.start, density) { HomeRowPivot(with(density) { rowLayout.start.dp.toPx() }) }
    val requesters = remember { LongSparseArray<FocusRequester>() }
    val rowStates = remember { LongSparseArray<LazyListState>() }
    val rowList = rememberLazyListState(initialFirstVisibleItemIndex = selection.app?.row ?: 0)
    val viewport = remember { LayoutBoundsHolder() }
    val toolbar = remember { FocusRequester() }
    val resumeFocus = remember { FocusRequester() }
    var settings by remember { mutableStateOf<SettingsRoute?>(null) }
    var moving by remember { mutableStateOf<MovingApp?>(null) }
    var activeRow by remember { mutableIntStateOf(selection.app?.row ?: 0) }
    // The row keeping focus during a slide; dropping it early would send focus to the toolbar.
    var leavingRow by remember { mutableIntStateOf(-1) }
    var toolbarFocused by remember { mutableStateOf(selection.area == HomeSelection.Area.SETTINGS) }
    var resumeIndex by remember(selection) { mutableIntStateOf(selection.resumeIndex) }
    var focusGeneration by remember { mutableIntStateOf(0) }
    var moveBusy by remember { mutableStateOf(false) }

    suspend fun focusRow(index: Int, animate: Boolean = false) {
        if (scene.rows.isEmpty()) { toolbar.requestFocus(); return }
        val row = index.coerceIn(0, scene.rows.lastIndex)
        val generation = ++focusGeneration
        if (row != activeRow && leavingRow < 0) leavingRow = activeRow
        activeRow = row
        if (animate) rowList.animateScrollBy(
            with(density) { homeRowTravel(rowExtents, rowList.firstVisibleItemIndex, row).dp.toPx() } - rowList.firstVisibleItemScrollOffset,
            tween(ROW_MOTION_MILLIS, easing = carouselEasing))
        rowList.scrollToItem(row)
        withFrameNanos { }
        val target = selection.focusForRow(scene, row)
        if (target != null) rowStates.get(scene.rows[row].id)?.scrollToItem(target.tile)
        withFrameNanos { }
        if (generation == focusGeneration) {
            if (settings == null && active) requesters.get(packInts(row, target?.tile ?: -1))?.requestFocus()
            leavingRow = -1
        }
    }
    /** Up from the first row leaves the rows, abandoning a slide that would reclaim focus. */
    fun leaveRows() {
        focusGeneration++
        leavingRow = -1
        if (scene.watchNext.isNotEmpty()) resumeFocus.requestFocus() else toolbar.requestFocus()
    }
    fun stepRow(direction: Int) {
        val next = (activeRow + direction).coerceIn(0, scene.rows.lastIndex)
        if (next != activeRow) scope.launch { focusRow(next, true) }
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
                if (scene.watchNext.isNotEmpty() && scene.bigPicture) {
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
                                Text("▶  " + (language["Continue Watching"] ?: "Continue Watching"), Modifier.padding(4.dp), fontSize = 13.sp)
                                if (item.progress > 0) Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xff333333))) {
                                    Box(Modifier.fillMaxWidth(item.progress.coerceIn(0, 100) / 100f).fillMaxHeight().background(Color(style.indicator)))
                                }
                            }
                        }
                        if (scene.bigPicture && scene.watchNext.size > 1) Text("${resumeIndex + 1} / ${scene.watchNext.size}", Modifier.padding(top = 8.dp), fontSize = 13.sp)
                    }
                }
                if (scene.watchNext.isNotEmpty() && !scene.bigPicture) {
                    WatchNextCarousel(scene.watchNext, selection.resumeIndex, artwork, active,
                        active && settings == null, artworkRevision, resumeFocus, Color(style.indicator),
                        style.borders, style.borderDp, language["Continue Watching"] ?: "Continue Watching",
                        onFocus = { if (it && settings == null) { toolbarFocused = false; selection.area = HomeSelection.Area.WATCH_NEXT } },
                        onStep = { index ->
                            selection.selectResume(scene, index); resumeIndex = selection.resumeIndex
                            actions.selectResume(scene.watchNext[resumeIndex])
                        }, onPlay = { if (settings == null) actions.play(it) },
                        onUp = { toolbar.requestFocus() }, onDown = ::appsFocus,
                        modifier = Modifier.offset(y = 90.dp))
                }
                // Spans the screen so a leaving row fades above rowTop, and the bottom padding
                // gives the last row the same travel as every other row.
                CompositionLocalProvider(LocalBringIntoViewSpec provides FixedHomeRows) { LazyColumn(state = rowList, userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize().layoutBounds(viewport),
                    contentPadding = PaddingValues(top = rowTop, bottom = screenHeight - rowTop)) {
                    itemsIndexed(scene.rows, key = { _, row -> row.id }, contentType = { _, _ -> "home-row" }) { rowIndex, row ->
                        val horizontal = rememberLazyListState(initialFirstVisibleItemIndex = selection.focusForRow(scene, rowIndex)?.tile ?: 0)
                        DisposableEffect(row.id, horizontal) { rowStates.put(row.id, horizontal); onDispose { rowStates.remove(row.id) } }
                        val tileHeight = rowLayout.tileHeight(row.height, tileLimit)
                        val height = tileHeight.dp
                        val width = height * (16f / 9f)
                        val decodeHeight = homeDecodeHeight(tileHeight).dp
                        val imageSize = with(density) { IntSize((decodeHeight * (16f / 9f)).roundToPx(), decodeHeight.roundToPx()) }
                        Column(Modifier.height(rowExtents[rowIndex].dp).graphicsLayer {
                            // Scroll position, read here, drives opacity without recomposing each frame.
                            val page = homeRowPage(rowExtents, rowList.firstVisibleItemIndex, rowList.firstVisibleItemScrollOffset / density.density)
                            alpha = homeRowAlpha(rowIndex - page, rowLayout.belowOpacity / 100f)
                        }) {
                            if (style.rowTitles) Text(row.name, Modifier.padding(start = rowLayout.start.dp, bottom = 8.dp), fontSize = 15.sp)
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowPivot) { LazyRow(state = horizontal,
                                // A small Space between rows may overlap the next slot; tiles keep their size.
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).focusRestorer(),
                                contentPadding = PaddingValues(start = rowLayout.start.dp, end = 32.dp, top = 10.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(rowLayout.appGap.dp)) {
                                if (row.apps.isEmpty()) item(key = "empty", contentType = "empty-row") {
                                    val focus = remember { FocusRequester() }
                                    DisposableEffect(rowIndex, focus) {
                                        val key = packInts(rowIndex, -1); requesters.put(key, focus)
                                        onDispose { if (requesters.get(key) === focus) requesters.remove(key) }
                                    }
                                    UiButton({ if (settings == null) settings = SettingsRoute("row", rowId = row.id) },
                                        Modifier.focusRequester(focus).focusProperties { canFocus = rowIndex == activeRow || rowIndex == leavingRow }
                                            .onPreviewKeyEvent {
                                                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionUp || it.key == Key.DirectionDown)) {
                                                    if (it.key == Key.DirectionUp && activeRow == 0) leaveRows()
                                                    else stepRow(if (it.key == Key.DirectionDown) 1 else -1); true
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
                                            color = Color(style.accent).copy(alpha = style.opacity), borderColor = Color(style.indicator),
                                            borderWidth = style.borderDp.dp, showBorder = style.borders || moving?.packageName == app.packageName,
                                            animateBorder = style.animateBorder, active = active,
                                            modifier = Modifier.size(width, height).focusRequester(focus)
                                                // A separate dialog window owns modal focus. Keep
                                                // its origin eligible so dismiss restores this app.
                                                .focusProperties { canFocus = rowIndex == activeRow || rowIndex == leavingRow }
                                                .onFocusChanged { if (it.isFocused && settings == null) { toolbarFocused = false; selection.remember(scene, rowIndex, tileIndex) } }
                                                .onPreviewKeyEvent {
                                                    if (it.type != KeyEventType.KeyDown) false else when {
                                                        moving?.packageName == app.packageName && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight) -> { moveApp(if (it.key == Key.DirectionLeft) -1 else 1); true }
                                                        it.key == Key.DirectionUp && activeRow == 0 -> { leaveRows(); true }
                                                        it.key == Key.DirectionUp || it.key == Key.DirectionDown -> {
                                                            stepRow(if (it.key == Key.DirectionDown) 1 else -1); true
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
                            } }
                        }
                    }
                } }
                if (scene.rows.isEmpty()) UiButton({ if (settings == null) settings = SettingsRoute("rows") }, Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                    Text("Add an application row", Modifier.padding(16.dp))
                }
                moving?.let { Text("Reorder · Left/Right to move · Select or Back to finish", Modifier.align(Alignment.BottomCenter).padding(12.dp), fontSize = 15.sp) }
            }
            settings?.let { route -> LauncherSettings(route, library, prefs, media, updates, actions, artwork, active, artworkRevision,
                close = { settings = null }, reorder = { rowId, packageName -> moving = MovingApp(rowId, packageName); settings = null },
                maxRowPosition = homeRowPositionLimit(screenHeight.value, rowCeiling)) }
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
