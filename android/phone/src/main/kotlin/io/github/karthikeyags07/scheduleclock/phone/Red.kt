package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Red mode (RULES A25): a hidden switch. Pushing the nav bar's Home icon up and letting go turns on red mode:
 * on Today and Watchface the text and icons turn red; everywhere the accent (switches, selected tab, glows) turns
 * red; the greys get a little darker (never black); the face previews turn red on their black dials and the watch
 * gets the red face. Settings, Calendars and About keep white text. Doing it again turns it all back. On the way
 * in, the face on Today flickers quickly into red.
 */
object RedTheme {
    var on by mutableStateOf(false)
    val red = Color(0xFFFF2020)
    val text = red
    val body = Color(0xFFE01B1B)
    val muted = Color(0xFFA81515)
    val faint = Color(0xFF5E0E0E)

    /** A grey a little darker in red mode (backgrounds, glass, buttons): never black. */
    @Composable fun darker(c: Color): Color = if (!on) c else Color(c.red * .62f, c.green * .62f, c.blue * .62f, c.alpha)
    @Composable fun glass(c: Color): Color = darker(c)
}

/** True on Today and Watchface: in red mode their text and icons are red (Palette reads it). */
val LocalRedText = compositionLocalOf { false }

/** [face] is how red the face on Today is (0 to 1; it flickers on the way in). */
class RedLevels(val face: State<Float>)

val LocalRed = staticCompositionLocalOf<RedLevels?> { null }

/**
 * Each colour to a pure red of its brightness, mixed with the colour as it is by [s]. The face's version keeps every
 * shade (whites go full red), as the watch's red face does. The app's version is tactical: a black point, so the
 * dark greys of pages, cards and glass go black, and the rest a clear red.
 */
private fun redMatrix(s: Float, tactical: Boolean): ColorMatrix {
    val k = 1f - s
    // Tactical leans on the red channel, so the colourway's glow (and anything already warm) stays a bright red.
    val (w, cut) = if (tactical) floatArrayOf(.78f, .65f, .26f) to -48f else floatArrayOf(.42f, .50f, .23f) to 0f
    return ColorMatrix(floatArrayOf(
        k + s * w[0], s * w[1], s * w[2], 0f, s * cut,
        0f, k, 0f, 0f, 0f,
        0f, 0f, k, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
}

/** Draws this and everything in it through the red filter, by [level] (read at draw time, so it never recomposes). */
fun Modifier.red(tactical: Boolean = true, level: () -> Float): Modifier = drawWithContent {
    val s = level()
    if (s <= .001f) { drawContent(); return@drawWithContent }
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(redMatrix(s.coerceAtMost(1f), tactical)) }
    drawIntoCanvas { c ->
        c.saveLayer(Rect(Offset.Zero, size).inflate(160f), paint)   // room for things drawn just outside (a pushed-up icon)
        drawContent()
        c.restore()
    }
}

/** The face's red on Today, which flickers on the way in (the same reds as the watch's red face). */
@Composable
fun Modifier.faceRed(): Modifier = LocalRed.current?.let { r -> this.red(tactical = false) { r.face.value } } ?: this
