package nl.neerdael.backdrop_launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.LocalTextStyle
import kotlinx.coroutines.launch

internal data class SettingsEntry(
    val id: String, val title: String, val subtitle: String = "", val value: String = "",
    val checked: Boolean? = null, val enabled: Boolean = true, val artwork: String? = null,
    val localize: Boolean = true,
    val control: SettingsControl = SettingsControl.CHECKBOX, val icon: SettingsIcon? = null, val section: Boolean = false,
    val onHold: (() -> Unit)? = null, val onKey: ((KeyEvent) -> Boolean)? = null, val slider: Float? = null,
    val onClick: () -> Unit,
)

@Composable
internal fun UiButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                      onLongClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    Surface(onClick = onClick, onLongClick = onLongClick, modifier = modifier, enabled = enabled,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x55383838), contentColor = Color.White,
            focusedContainerColor = Color(0xff454545), focusedContentColor = Color.White),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        content = content)
}

@Composable
internal fun SettingsPanel(title: String, entries: List<SettingsEntry>,
                           artwork: ComposeArtwork, active: Boolean, revision: Int,
                           busy: String?, error: String?, translateTitle: Boolean = true, artworkGrid: Boolean = false,
                           lead: (@Composable () -> Unit)? = null, footer: @Composable () -> Unit = {}) {
    val labels = LocalUiLanguage.current
    val first = remember { FocusRequester() }
    val firstId = entries.firstOrNull { !it.section && it.enabled }?.id
    val viewport = remember { LayoutBoundsHolder() }
    val list = rememberLazyListState()
    val grid = rememberLazyGridState()
    LaunchedEffect(title, firstId, active) {
        if (active && firstId != null) {
            // Scrolling waits for first layout, so only the attached state can be used here.
            if (artworkGrid) grid.scrollToItem(0) else list.scrollToItem(0)
            withFrameNanos { }; first.requestFocus()
        }
    }
    CompositionLocalProvider(LocalTextStyle provides NativeSettingsStyle.primaryStyle) {
        Column(Modifier.fillMaxHeight().width(NativeSettingsStyle.panelWidth).shadow(6.dp, RectangleShape, clip = false).background(NativeSettingsStyle.body)) {
            Box(Modifier.fillMaxWidth().height(NativeSettingsStyle.headerHeight).background(NativeSettingsStyle.header)
                .padding(start = 24.dp, end = 56.dp, top = 27.dp), contentAlignment = Alignment.CenterStart) {
                Text(if (translateTitle) labels[title] ?: title else title, style = NativeSettingsStyle.titleStyle,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            busy?.let { Text(it, modifier = Modifier.padding(start = 24.dp, end = 56.dp, top = 8.dp), style = NativeSettingsStyle.summaryStyle) }
            error?.let { Text(it, modifier = Modifier.padding(start = 24.dp, end = 56.dp, top = 8.dp), style = NativeSettingsStyle.summaryStyle.copy(color = Color(0xffff8a80))) }
            if (artworkGrid) LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f).layoutBounds(viewport), grid,
                PaddingValues(start = 24.dp, end = 56.dp, top = 8.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }, contentType = { "artwork" }) { entry ->
                    ArtworkTile(entry, artwork, active, revision, viewport, busy != null,
                        if (entry.id == firstId) Modifier.focusRequester(first) else Modifier)
                }
            } else LazyColumn(state = list, modifier = Modifier.weight(1f).layoutBounds(viewport), contentPadding = PaddingValues(bottom = 34.dp)) {
                lead?.let { item(key = "lead", contentType = "lead") { it() } }
                itemsIndexed(entries, key = { _, entry -> entry.id }, contentType = { _, entry -> if (entry.section) "section" else "setting" }) { _, entry ->
                    if (entry.section) {
                        Box(Modifier.fillMaxWidth().height(38.dp).padding(start = 24.dp, end = 56.dp), contentAlignment = Alignment.CenterStart) {
                            Text(labels[entry.title] ?: entry.title, style = NativeSettingsStyle.sectionStyle)
                        }
                    } else {
                        val color = if (entry.enabled) NativeSettingsStyle.primary else NativeSettingsStyle.disabled
                        val focus = if (entry.id == firstId) Modifier.focusRequester(first) else Modifier
                        NativeSettingsSurface(entry.onClick, enabled = entry.enabled, blocked = busy != null, onLongClick = entry.onHold,
                            modifier = Modifier.fillMaxWidth().then(focus).onPreviewKeyEvent {
                                busy != null || entry.onKey?.invoke(it) == true
                            }.semantics(mergeDescendants = true) {
                                contentDescription = listOf(entry.title, entry.value, entry.subtitle).filter { it.isNotBlank() }.joinToString(", ")
                                entry.slider?.let { progressBarRangeInfo = ProgressBarRangeInfo(it, 0f..1f) }
                                entry.checked?.let { checked ->
                                    role = when (entry.control) { SettingsControl.SWITCH -> Role.Switch; SettingsControl.RADIO -> Role.RadioButton; else -> Role.Checkbox }
                                    if (entry.control == SettingsControl.RADIO) selected = checked
                                    else toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                                }
                            }) {
                            Column {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 24.dp, end = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (entry.icon != null) { NativeSettingsIcon(entry.icon); Spacer(Modifier.width(16.dp)) }
                                    else if (entry.checked != null && entry.control != SettingsControl.SWITCH && entry.artwork == null) {
                                        NativeSettingsControl(entry.checked, entry.control, entry.enabled); Spacer(Modifier.width(16.dp))
                                    } else if (entry.artwork != null) {
                                        Artwork(entry.artwork, artwork, active, revision, Modifier.size(40.dp, 24.dp), ContentScale.Fit, viewport = viewport)
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Column(Modifier.weight(1f).padding(top = 14.dp, bottom = if (entry.slider != null) 6.dp else 13.dp)) {
                                        Text(if (entry.localize) labels[entry.title] ?: entry.title else entry.title,
                                            style = NativeSettingsStyle.primaryStyle.copy(color = color))
                                        if (entry.subtitle.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(entry.subtitle, style = NativeSettingsStyle.summaryStyle.copy(color = if (entry.enabled) NativeSettingsStyle.secondary else NativeSettingsStyle.disabled),
                                                maxLines = 4, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (entry.checked != null && (entry.control == SettingsControl.SWITCH || entry.artwork != null)) {
                                        Spacer(Modifier.width(16.dp)); NativeSettingsControl(entry.checked, entry.control, entry.enabled)
                                    } else if (entry.value.isNotBlank()) Text(entry.value, Modifier.padding(start = 16.dp).widthIn(max = 112.dp), style = NativeSettingsStyle.primaryStyle.copy(color = color), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                entry.slider?.let { NativeSettingsSlider(it, entry.enabled, Modifier.padding(start = 24.dp, end = 56.dp, bottom = 12.dp)) }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 56.dp, bottom = 16.dp)) { footer() }
        }
    }
}

/** Image-only target; its name stays available to accessibility services. */
@Composable
private fun ArtworkTile(entry: SettingsEntry, artwork: ComposeArtwork, active: Boolean, revision: Int,
                        viewport: LayoutBoundsHolder, busy: Boolean, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    NativeSettingsSurface(entry.onClick, blocked = busy, shape = shape,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape).onPreviewKeyEvent { busy }
            .semantics(mergeDescendants = true) { contentDescription = entry.title }) {
        Artwork(entry.artwork, artwork, active, revision, Modifier.fillMaxSize(), ContentScale.Fit, viewport = viewport)
    }
}

internal data class TextPrompt(val title: String, val initial: String = "", val save: (String) -> Unit)
internal data class Confirmation(val title: String, val body: String, val confirmLabel: String = "Confirm", val confirm: () -> Unit)

@Composable
internal fun PromptDialog(prompt: TextPrompt, close: () -> Unit) {
    val labels = LocalUiLanguage.current
    var text by remember(prompt) { mutableStateOf(prompt.initial) }
    var editing by remember { mutableStateOf(true) }
    val field = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() { if (text.isNotBlank()) { prompt.save(text.trim()); close() } }
    Dialog(close) {
        Column(Modifier.width(NativeSettingsStyle.panelWidth).background(NativeSettingsStyle.body, RoundedCornerShape(2.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(prompt.title, style = NativeSettingsStyle.titleStyle)
            NativeSettingsSurface({ editing = true; field.requestFocus(); keyboard?.show() }, Modifier.fillMaxWidth()) {
                BasicTextField(text, { text = it }, textStyle = NativeSettingsStyle.primaryStyle,
                    singleLine = true, cursorBrush = SolidColor(NativeSettingsStyle.accent), readOnly = !editing,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); editing = false; save.requestFocus() }),
                    modifier = Modifier.padding(16.dp).fillMaxWidth().focusRequester(field).onPreviewKeyEvent {
                        if (editing && it.type == KeyEventType.KeyDown && (it.key == Key.Back || it.key == Key.Escape)) {
                            editing = false; keyboard?.hide(); save.requestFocus(); true
                        } else false
                    })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NativeSettingsSurface(close) { Text(LocalUiLanguage.current["Cancel"] ?: "Cancel", Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 14.dp), style = NativeSettingsStyle.primaryStyle.copy(color = NativeSettingsStyle.accent)) }
                NativeSettingsSurface(::submit, Modifier.focusRequester(save), enabled = text.isNotBlank()) { Text(labels["Save"] ?: "Save", Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 14.dp), style = NativeSettingsStyle.primaryStyle.copy(color = NativeSettingsStyle.accent)) }
            }
            LaunchedEffect(prompt) { withFrameNanos { }; field.requestFocus(); keyboard?.show() }
        }
    }
}

@Composable
internal fun ConfirmationDialog(value: Confirmation, close: () -> Unit) {
    val confirm = remember { FocusRequester() }
    Dialog(close) {
        Column(Modifier.width(NativeSettingsStyle.panelWidth).background(NativeSettingsStyle.body, RoundedCornerShape(2.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(value.title, style = NativeSettingsStyle.titleStyle)
            Text(value.body, style = NativeSettingsStyle.primaryStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NativeSettingsSurface(close, Modifier.focusRequester(confirm)) { Text(LocalUiLanguage.current["Cancel"] ?: "Cancel", Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 14.dp), style = NativeSettingsStyle.primaryStyle.copy(color = NativeSettingsStyle.accent)) }
                NativeSettingsSurface({ value.confirm(); close() }) { Text(value.confirmLabel, Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 14.dp), style = NativeSettingsStyle.primaryStyle.copy(color = NativeSettingsStyle.accent)) }
            }
            LaunchedEffect(value) { withFrameNanos { }; confirm.requestFocus() }
        }
    }
}

/** D-pad scrolling for long update notes and legal text, with escape at either end to buttons. */
@Composable
internal fun ReadableText(text: String, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    LazyColumn(state = list, modifier = modifier.heightIn(max = 240.dp).fillMaxWidth()
        .background(if (focused) NativeSettingsStyle.focus else Color.Transparent)
        .onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && ((it.key == Key.DirectionDown && list.canScrollForward) ||
                    (it.key == Key.DirectionUp && list.canScrollBackward))) {
                scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex + if (it.key == Key.DirectionDown) 1 else -1).coerceAtLeast(0)) }; true
            } else false
        }.focusable(), contentPadding = PaddingValues(14.dp)) {
        val lines = readableNoteLines(text)
        itemsIndexed(lines, key = { index, _ -> index }, contentType = { _, _ -> "note-line" }) { _, line ->
            Text(line, style = NativeSettingsStyle.primaryStyle)
        }
    }
}

@Composable
fun LoadingHome(error: String?) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(32.dp)) {
        Text(error ?: "Loading Backdrop…", color = Color.White, fontSize = 20.sp)
    }
}
