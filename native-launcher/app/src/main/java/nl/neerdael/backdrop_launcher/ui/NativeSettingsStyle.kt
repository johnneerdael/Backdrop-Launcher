package nl.neerdael.backdrop_launcher.ui

import android.graphics.Typeface
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Port of the measured settings_theme.dart contract; independent of the Home theme. */
internal object NativeSettingsStyle {
    val panelWidth = 360.dp
    val headerHeight = 91.dp
    val body = Color(0xff202124)
    val header = Color(0xff1b1c1e)
    val focus = Color(0xff2e2f32)
    val accent = Color(0xff51aaff)
    val primary = Color(0xffeeeeee)
    val secondary = Color(0xb3eeeeee)
    val disabled = Color(0x4deeeeee)
    val control = Color(0xb3ffffff)
    val scrim = Color(0xcc000000)
    val titleStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp,
        fontWeight = FontWeight.Medium, lineHeight = 26.75.sp, color = Color.White)
    val primaryStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp,
        fontWeight = FontWeight.Normal, lineHeight = 19.sp, color = primary)
    val summaryStyle = TextStyle(fontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)),
        fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.25.sp, color = secondary)
    val sectionStyle = primaryStyle.copy(fontSize = 12.sp, lineHeight = 16.25.sp,
        fontWeight = FontWeight.Medium, color = accent)
}

internal enum class SettingsControl { CHECKBOX, SWITCH, RADIO }
internal enum class SettingsIcon { APPS, INTERFACE, SYSTEM, SETTINGS, UPDATE, INFO }

/** One activation/focus target per preference; no TV Material card scale, border or clipping. */
@Composable
internal fun NativeSettingsSurface(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, blocked: Boolean = false, onLongClick: (() -> Unit)? = null,
    shape: Shape = RectangleShape, content: @Composable BoxScope.() -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Box(modifier.background(if (focused) NativeSettingsStyle.focus else Color.Transparent, shape)
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            // A held Select can move focus here when it opens an app/row menu. Its
            // repeats belong to the old press, not a new activation of this row.
            focused && key.action == AndroidKeyEvent.ACTION_DOWN && (key.repeatCount > 0 || key.isLongPress) &&
                when (key.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_SPACE,
                    AndroidKeyEvent.KEYCODE_BUTTON_A -> true
                    else -> false
                }
        }
        .combinedClickable(interactionSource = interactions, indication = null, enabled = enabled,
            onClick = { if (!blocked) onClick() }, onLongClick = onLongClick?.let { { if (!blocked) it() } })
        .semantics { if (blocked) disabled() }, content = content)
}

/** Painted controls intentionally have no second remote focus target. */
@Composable
internal fun NativeSettingsControl(checked: Boolean, kind: SettingsControl, enabled: Boolean) {
    val blue = if (enabled) NativeSettingsStyle.accent else NativeSettingsStyle.disabled
    val normal = if (enabled) NativeSettingsStyle.control else NativeSettingsStyle.disabled
    val width = if (kind == SettingsControl.SWITCH) 48.dp else 32.dp
    val height = if (kind == SettingsControl.SWITCH) 27.25.dp else 32.dp
    Canvas(Modifier.size(width, height)) {
        when (kind) {
            SettingsControl.SWITCH -> {
                val top = (size.height - 14.dp.toPx()) / 2
                drawRoundRect((if (checked) blue else Color.White).copy(alpha = if (enabled) .3f else .1f),
                    Offset(10.5.dp.toPx(), top), Size(27.dp.toPx(), 14.dp.toPx()),
                    androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
                val centre = Offset((13.625f + if (checked) 20.75f else 0f).dp.toPx(), size.height / 2)
                drawCircle(Color(0x20000000), 11.dp.toPx(), centre + Offset(0f, .5.dp.toPx()))
                drawCircle(if (checked) blue else if (enabled) Color(0xffb9b9b9) else normal, 10.dp.toPx(), centre)
            }
            SettingsControl.RADIO -> {
                drawCircle(if (checked) blue else normal, 9.dp.toPx(), style = Stroke(2.dp.toPx()))
                if (checked) drawCircle(blue, 5.dp.toPx())
            }
            SettingsControl.CHECKBOX -> {
                val origin = Offset((size.width - 18.dp.toPx()) / 2, (size.height - 18.dp.toPx()) / 2)
                drawRect(if (checked) blue else normal, if (checked) origin else origin + Offset(1.dp.toPx(), 1.dp.toPx()),
                    Size((if (checked) 18 else 16).dp.toPx(), (if (checked) 18 else 16).dp.toPx()),
                    style = if (checked) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.dp.toPx()))
                if (checked) {
                    val path = Path().apply {
                        moveTo(origin.x + 3.dp.toPx(), origin.y + 9.dp.toPx())
                        lineTo(origin.x + 7.dp.toPx(), origin.y + 13.dp.toPx())
                        lineTo(origin.x + 15.dp.toPx(), origin.y + 5.dp.toPx())
                    }
                    drawPath(path, NativeSettingsStyle.body, style = Stroke(2.dp.toPx(), cap = StrokeCap.Square))
                }
            }
        }
    }
}

/** Painted across the row; Left and Right on the row move it, so it is not a second focus target. */
@Composable
internal fun NativeSettingsSlider(fraction: Float, enabled: Boolean, modifier: Modifier = Modifier) {
    val blue = if (enabled) NativeSettingsStyle.accent else NativeSettingsStyle.disabled
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val radius = 7.dp.toPx()
        val y = size.height / 2
        val end = size.width - radius
        val thumb = radius + (end - radius) * fraction
        drawLine(Color.White.copy(alpha = .3f), Offset(radius, y), Offset(end, y), 3.dp.toPx(), StrokeCap.Round)
        drawLine(blue, Offset(radius, y), Offset(thumb, y), 3.dp.toPx(), StrokeCap.Round)
        drawCircle(blue, radius, Offset(thumb, y))
    }
}

/** Small monochrome preference icons, drawn as vectors rather than cached bitmaps. */
@Composable
internal fun NativeSettingsIcon(icon: SettingsIcon) {
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24
        val color = Color.White
        val stroke = Stroke(1.8f * unit)
        fun line(x: Float, y: Float, endX: Float, endY: Float) =
            drawLine(color, Offset(x * unit, y * unit), Offset(endX * unit, endY * unit), 1.8f * unit)
        when (icon) {
            SettingsIcon.APPS -> for (x in listOf(3f, 10f, 17f)) for (y in listOf(3f, 10f, 17f))
                drawRect(color, Offset(x * unit, y * unit), Size(4 * unit, 4 * unit))
            SettingsIcon.INTERFACE -> {
                drawRect(color, Offset(3 * unit, 3 * unit), Size(7 * unit, 18 * unit), style = stroke)
                drawRect(color, Offset(13 * unit, 3 * unit), Size(8 * unit, 7 * unit), style = stroke)
                drawRect(color, Offset(13 * unit, 13 * unit), Size(8 * unit, 8 * unit), style = stroke)
            }
            SettingsIcon.SETTINGS, SettingsIcon.SYSTEM -> {
                for (angle in 0 until 360 step 45) rotate(angle.toFloat()) {
                    drawRect(color, Offset(10.5f * unit, unit), Size(3 * unit, 5 * unit))
                }
                drawCircle(color, 7 * unit, style = stroke)
                drawCircle(color, 2.5f * unit, style = stroke)
            }
            SettingsIcon.UPDATE -> {
                line(12f, 3f, 12f, 15f); line(7f, 10f, 12f, 15f); line(17f, 10f, 12f, 15f)
                line(4f, 16f, 4f, 21f); line(4f, 21f, 20f, 21f); line(20f, 21f, 20f, 16f)
            }
            SettingsIcon.INFO -> {
                drawCircle(color, 9 * unit, style = stroke)
                drawCircle(color, unit, Offset(12 * unit, 7 * unit)); line(12f, 11f, 12f, 17f)
            }
        }
    }
}
