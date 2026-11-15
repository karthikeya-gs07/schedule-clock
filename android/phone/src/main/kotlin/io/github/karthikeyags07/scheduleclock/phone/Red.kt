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
    @Composable fun darker(c: Color): Color = shade(c, .45f)
    /** Frosted glass (nav bar, round buttons, pill) in red mode: darker too, but still lifted off the page. */
    @Composable fun glass(c: Color): Color = shade(c, .55f)
    private fun shade(c: Color, k: Float) = if (!on) c else Color(c.red * k, c.green * k, c.blue * k, c.alpha)
}

/** True on Today and Watchface: in red mode their text and icons are red (Palette reads it). */
val LocalRedText = compositionLocalOf { false }

/** [face] is how much of the red face shows over the face in use on Today and Watchface (it flickers in). */
class RedLevels(val face: State<Float>)

val LocalRed = staticCompositionLocalOf<RedLevels?> { null }
