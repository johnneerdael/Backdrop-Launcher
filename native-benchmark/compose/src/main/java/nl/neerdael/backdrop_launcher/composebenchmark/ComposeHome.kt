package nl.neerdael.backdrop_launcher.composebenchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import android.util.Log
import androidx.collection.LongSparseArray
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import nl.neerdael.backdrop_launcher.nativebenchmark.*
import java.util.Date

@Composable
fun LoadingHome(error: String?) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(32.dp)) {
        Text(error ?: "Loading Home…", color = Color.White)
    }
}

@Composable
fun ComposeHome(
    data: HomeData, selection: HomeSelection, artwork: ComposeArtwork,
    resumed: Boolean, artworkRevision: Int, launch: (AppTile) -> Unit,
    play: (ResumeItem) -> Unit, settings: () -> Unit,
) {
    val scene = data.scene
    val context = LocalContext.current
    val font = remember(context) { FontFamily(Font("fonts/Inter.ttf", context.assets)) }
    val density = LocalDensity.current
    val appImageSize = with(density) { IntSize(146.dp.roundToPx(), 82.dp.roundToPx()) }
    val itemKeys = remember(scene.rows) { HomeItemKeys(scene.rows) }
    val appsViewport = remember { LayoutBoundsHolder() }
    val appRequesters = remember { LongSparseArray<FocusRequester>() }
    val settingsFocus = remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selection.app?.row ?: 0)
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf(false) }
    var resumeIndex by remember(selection) { mutableIntStateOf(selection.resumeIndex) }

    fun focusApp() {
        val app = selection.app ?: return
        appRequesters.get(packInts(app.row, app.tile))?.requestFocus()
    }
    LaunchedEffect(resumed, details) {
        if (!resumed || details) return@LaunchedEffect
        selection.app?.let { rowState.scrollToItem(it.row) }
        withFrameNanos { }
        when (selection.area) {
            HomeSelection.Area.SETTINGS -> settingsFocus.requestFocus()
            HomeSelection.Area.WATCH_NEXT -> watchFocus.requestFocus()
            HomeSelection.Area.APPS -> focusApp()
        }
        Log.i("BackdropCompose", "HOME_READY scene=${scene.name}; rows=${scene.rows.size}; watchNext=${scene.watchNext.size}")
    }
    CompositionLocalProvider(LocalTextStyle provides TextStyle(
        color = Color.White, fontFamily = font, shadow = Shadow(Color.Black, blurRadius = 3f))) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Artwork(scene.backdropFor(resumeIndex), artwork, resumed, artworkRevision,
                Modifier.fillMaxSize().cinematicScrim(scene.scrim || (scene.bigPicture && scene.watchNext.isNotEmpty())),
                ContentScale.Crop, retain = false)
            Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 32.dp).height(40.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HomeSurface({ details = true }, Modifier.size(36.dp).focusRequester(settingsFocus)
                    .onFocusChanged { if (it.isFocused) selection.area = HomeSelection.Area.SETTINGS }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                            if (scene.watchNext.isNotEmpty()) watchFocus.requestFocus() else focusApp()
                            true
                        } else false
                    }.semantics { contentDescription = "Benchmark details" }) {
                    Text("⚙", Modifier.align(Alignment.Center), fontSize = 24.sp)
                }
                Clock(resumed, Modifier.padding(start = 12.dp))
            }
            if (scene.watchNext.isNotEmpty()) {
                val item = scene.watchNext[resumeIndex]
                Column(Modifier.offset(x = 32.dp, y = if (scene.bigPicture) 90.dp else 103.dp)
                    .width(if (scene.bigPicture) 560.dp else 300.dp)) {
                    if (scene.bigPicture) {
                        Text("CONTINUE WATCHING", fontSize = 11.sp)
                        Text(item.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(item.description, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    HomeSurface({ play(item) }, Modifier.width(if (scene.bigPicture) 200.dp else 300.dp)
                        .focusRequester(watchFocus)
                        .onFocusChanged { if (it.isFocused) selection.area = HomeSelection.Area.WATCH_NEXT }
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    selection.cycle(scene.watchNext.size, if (it.key == Key.DirectionRight) 1 else -1)
                                    resumeIndex = selection.resumeIndex
                                    true
                                }
                                Key.DirectionDown -> { focusApp(); true }
                                Key.DirectionUp -> { settingsFocus.requestFocus(); true }
                                else -> false
                            }
                        }.semantics { contentDescription = "Continue watching ${item.title}" },
                        color = Color.Transparent, scale = 1f) {
                        Column(Modifier.padding(2.dp)) {
                            if (!scene.bigPicture) {
                                Artwork(item.image, artwork, resumed, artworkRevision,
                                    Modifier.size(296.dp, 164.dp).clip(RoundedCornerShape(8.dp)), ContentScale.Crop)
                                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Text(if (scene.bigPicture) "▶  Continue watching" else "Continue Watching", fontSize = 12.sp)
                            if (item.progress > 0) Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xff333333))) {
                                Box(Modifier.fillMaxWidth(item.progress / 100f).fillMaxHeight().background(Color(scene.accent)))
                            }
                        }
                    }
                }
            }
            LazyColumn(state = rowState, modifier = Modifier.fillMaxWidth().fillMaxHeight(.38f)
                .align(Alignment.BottomStart).layoutBounds(appsViewport)) {
                itemsIndexed(scene.rows, key = { index, _ -> itemKeys.rows[index] }, contentType = { _, _ -> "app-row" }) { rowIndex, row ->
                    val tileState = rememberLazyListState(initialFirstVisibleItemIndex =
                        selection.app?.takeIf { it.row == rowIndex }?.tile ?: 0)
                    LazyRow(state = tileState, contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.height(150.dp).semantics { contentDescription = row.name }) {
                        itemsIndexed(row.apps, key = { index, _ -> itemKeys.apps[rowIndex][index] },
                            contentType = { _, _ -> "app-tile" }) { tileIndex, app ->
                            val focus = remember { FocusRequester() }
                            val slot = packInts(rowIndex, tileIndex)
                            DisposableEffect(appRequesters, slot, focus) {
                                appRequesters.put(slot, focus)
                                onDispose { if (appRequesters.get(slot) === focus) appRequesters.remove(slot) }
                            }
                            Column(Modifier.width(146.dp)) {
                                AppCardSurface({ launch(app) }, Modifier.size(146.dp, 82.dp).focusRequester(focus)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            selection.area = HomeSelection.Area.APPS
                                            selection.app = AppFocus(rowIndex, tileIndex, app.packageName)
                                        }
                                    }.onPreviewKeyEvent {
                                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp && rowIndex == 0) {
                                            if (scene.watchNext.isNotEmpty()) watchFocus.requestFocus() else settingsFocus.requestFocus()
                                            true
                                        } else false
                                    }.semantics { contentDescription = app.label },
                                    Color(scene.accent).copy(alpha = scene.tileOpacity)) {
                                    Artwork(app.artwork ?: "package:${app.packageName}", artwork, resumed, artworkRevision,
                                        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), ContentScale.Fit,
                                        decodeSize = appImageSize, viewport = appsViewport)
                                }
                                if (scene.showAppNames) Text(app.label, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        if (details) Dialog(onDismissRequest = { details = false }) {
            Column(Modifier.width(540.dp).background(Color(0xff202020), RoundedCornerShape(12.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Compose Home benchmark", fontSize = 24.sp)
                Text("${scene.name}\n\n${data.providerStatus}\n\nThis prototype does not edit Backdrop settings or rows.", fontSize = 16.sp)
                val close = remember { FocusRequester() }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeSurface({ details = false }, Modifier.focusRequester(close)) { Text("Close", Modifier.padding(12.dp)) }
                    HomeSurface(settings) { Text("TV settings", Modifier.padding(12.dp)) }
                }
                LaunchedEffect(close) { withFrameNanos { }; close.requestFocus() }
            }
        }
        BackHandler(!details) {
            scope.launch {
                selection.app?.let { rowState.scrollToItem(it.row) }
                withFrameNanos { }
                focusApp()
            }
        }
    }
}

@Composable
private fun HomeSurface(
    onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Color(0x33000000),
    scale: Float = 1.06f, content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(onClick = onClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, contentColor = Color.White,
            focusedContainerColor = color, focusedContentColor = Color.White),
        scale = ClickableSurfaceDefaults.scale(focusedScale = scale),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        content = content)
}

@Composable
private fun Clock(active: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val clock = remember(context) { HomeClock(context) }
    var time by remember(clock) { mutableStateOf(clock.text()) }
    DisposableEffect(context, clock, active) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                time = clock.text(refresh = intent.action != Intent.ACTION_TIME_TICK)
            }
        }
        if (active) {
            time = clock.text(refresh = true)
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_LOCALE_CHANGED)
            })
        }
        onDispose { if (active) context.unregisterReceiver(receiver) }
    }
    Text(time, modifier, fontSize = 22.sp)
}

private class HomeClock(private val context: Context) {
    private var formatter = DateFormat.getTimeFormat(context)
    private val now = Date()
    fun text(refresh: Boolean = false): String {
        if (refresh) formatter = DateFormat.getTimeFormat(context)
        now.time = System.currentTimeMillis()
        return formatter.format(now)
    }
}

private fun Modifier.cinematicScrim(enabled: Boolean): Modifier = if (!enabled) this else drawWithCache {
    val vertical = Brush.verticalGradient(
        0f to Color(0x59000000), .25f to Color.Transparent,
        .65f to Color(0x80000000), 1f to Color(0xe0000000))
    val horizontal = Brush.horizontalGradient(
        0f to Color(0xa6000000), .35f to Color(0x40000000), .7f to Color.Transparent)
    onDrawWithContent {
        drawContent()
        drawRect(vertical)
        drawRect(horizontal)
    }
}
