package nl.neerdael.backdrop_launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.launch

internal data class SettingsEntry(
    val id: String, val title: String, val subtitle: String = "", val value: String = "",
    val checked: Boolean? = null, val enabled: Boolean = true, val artwork: String? = null,
    val localize: Boolean = true,
    val onHold: (() -> Unit)? = null, val onKey: ((KeyEvent) -> Boolean)? = null,
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
internal fun SettingsPanel(title: String, entries: List<SettingsEntry>, back: () -> Unit,
                           artwork: ComposeArtwork, active: Boolean, revision: Int,
                           busy: String?, error: String?, translateTitle: Boolean = true, footer: @Composable () -> Unit = {}) {
    val labels = LocalUiLanguage.current
    val first = remember { FocusRequester() }
    val viewport = remember { LayoutBoundsHolder() }
    val list = rememberLazyListState()
    LaunchedEffect(title) { list.scrollToItem(0); withFrameNanos { }; first.requestFocus() }
    Column(Modifier.fillMaxHeight().width(540.dp).background(Color(0xf51b1b1f)).padding(24.dp)) {
        UiButton(back, Modifier.focusRequester(first), enabled = busy == null) { Text("‹  " + if (translateTitle) labels[title] ?: title else title, Modifier.padding(14.dp), fontSize = 24.sp) }
        Spacer(Modifier.height(12.dp))
        busy?.let { Text(it, color = Color(0xffffd180), modifier = Modifier.padding(8.dp), fontSize = 16.sp) }
        error?.let { Text(it, color = Color(0xffff8a80), modifier = Modifier.padding(8.dp), fontSize = 16.sp) }
        LazyColumn(state = list, modifier = Modifier.weight(1f).layoutBounds(viewport),
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(entries, key = { _, entry -> entry.id }, contentType = { _, _ -> "setting" }) { _, entry ->
                UiButton(entry.onClick, enabled = entry.enabled && busy == null, onLongClick = entry.onHold,
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { entry.onKey?.invoke(it) == true }
                        .semantics { contentDescription = listOf(entry.title, entry.value, entry.subtitle).filter { it.isNotBlank() }.joinToString(", ") }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (entry.artwork != null) {
                            Artwork(entry.artwork, artwork, active, revision, Modifier.size(88.dp, 50.dp), ContentScale.Fit, viewport = viewport)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text((entry.checked?.let { if (it) "☑  " else "☐  " } ?: "") + (if (entry.localize) labels[entry.title] ?: entry.title else entry.title),
                                color = if (entry.enabled) Color.White else Color.LightGray, fontSize = 18.sp)
                            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, fontSize = 13.sp, color = Color(0xffcccccc))
                        }
                        if (entry.value.isNotBlank()) Text(entry.value, Modifier.padding(start = 10.dp), fontSize = 15.sp)
                    }
                }
            }
        }
        footer()
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
        Column(Modifier.width(500.dp).background(Color(0xff202024), RoundedCornerShape(12.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(prompt.title, fontSize = 24.sp)
            UiButton({ editing = true; field.requestFocus(); keyboard?.show() }, Modifier.fillMaxWidth()) {
                BasicTextField(text, { text = it }, textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                    singleLine = true, cursorBrush = SolidColor(Color.White), readOnly = !editing,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); editing = false; save.requestFocus() }),
                    modifier = Modifier.padding(16.dp).fillMaxWidth().focusRequester(field).onPreviewKeyEvent {
                        if (editing && it.type == KeyEventType.KeyDown && (it.key == Key.Back || it.key == Key.Escape)) {
                            editing = false; keyboard?.hide(); save.requestFocus(); true
                        } else false
                    })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                UiButton(close) { Text(LocalUiLanguage.current["Cancel"] ?: "Cancel", Modifier.padding(12.dp)) }
                UiButton(::submit, Modifier.focusRequester(save), enabled = text.isNotBlank()) { Text(labels["Save"] ?: "Save", Modifier.padding(12.dp)) }
            }
            LaunchedEffect(prompt) { withFrameNanos { }; field.requestFocus(); keyboard?.show() }
        }
    }
}

@Composable
internal fun ConfirmationDialog(value: Confirmation, close: () -> Unit) {
    val confirm = remember { FocusRequester() }
    Dialog(close) {
        Column(Modifier.width(500.dp).background(Color(0xff202024), RoundedCornerShape(12.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(value.title, fontSize = 24.sp)
            Text(value.body, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                UiButton(close, Modifier.focusRequester(confirm)) { Text(LocalUiLanguage.current["Cancel"] ?: "Cancel", Modifier.padding(12.dp)) }
                UiButton({ value.confirm(); close() }) { Text(value.confirmLabel, Modifier.padding(12.dp)) }
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
        .background(if (focused) Color(0xff45454c) else Color(0xff29292f), RoundedCornerShape(8.dp))
        .onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && ((it.key == Key.DirectionDown && list.canScrollForward) ||
                    (it.key == Key.DirectionUp && list.canScrollBackward))) {
                scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex + if (it.key == Key.DirectionDown) 1 else -1).coerceAtLeast(0)) }; true
            } else false
        }.focusable(), contentPadding = PaddingValues(14.dp)) {
        val lines = readableNoteLines(text)
        itemsIndexed(lines, key = { index, _ -> index }, contentType = { _, _ -> "note-line" }) { _, line ->
            Text(line, fontSize = 15.sp)
        }
    }
}

@Composable
fun LoadingHome(error: String?) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(32.dp)) {
        Text(error ?: "Loading Backdrop…", color = Color.White, fontSize = 20.sp)
    }
}
