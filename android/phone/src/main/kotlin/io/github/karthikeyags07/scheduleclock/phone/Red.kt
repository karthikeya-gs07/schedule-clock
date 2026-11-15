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
 * Red mode and focus mode, hidden switches. Pushing the nav bar's Home icon up and letting go turns on red mode
 * (RULES A25): on Today and Watchface the text and icons turn red; everywhere the accent (switches, selected tab,
 * glows) does; the greys get a little darker (never black); the face shows the red face itself and the watch wears
 * it too. Settings, Calendars and About keep white text. Pulling it down turns on focus mode (RULES A26): the
 * face, on the phone and the watch, becomes the Focus face (the countdown to the meeting's end or the next one's
 * start); the app keeps its colours. The same gesture again turns it off; the other one switches. On the way in,
 * the face flickers quickly into it.
 */
object RedTheme {
    /** "", "red" or "focus". */
    var mode by mutableStateOf("")
    /** Red mode: the app's colours turn red. */
    val on get() = mode == "red"
    val red: Color @Composable get() = Color(0xFFFF2020)
    val text: Color @Composable get() = red
    val body: Color @Composable get() = Color(0xFFE01B1B)
    val muted: Color @Composable get() = Color(0xFFA81515)
    val faint: Color @Composable get() = Color(0xFF5E0E0E)
    /** The mode's face name on the Watchface page. */
    val name get() = if (mode == "focus") "Focus mode" else "Red mode"

    /** A grey a little darker in red or green mode (backgrounds, glass, buttons): never black. */
    @Composable fun darker(c: Color): Color = shade(c, .45f)
    /** Frosted glass (nav bar, round buttons, pill) in red or green mode: darker too, but still lifted off the page. */
    @Composable fun glass(c: Color): Color = shade(c, .55f)
    private fun shade(c: Color, k: Float) = if (!on) c else Color(c.red * k, c.green * k, c.blue * k, c.alpha)
}

/** True on Today and Watchface: in red mode their text and icons are red (Palette reads it). */
val LocalRedText = compositionLocalOf { false }

/** [face] is how much of the red face shows over the face in use on Today and Watchface (it flickers in). */
class RedLevels(val face: State<Float>)

val LocalRed = staticCompositionLocalOf<RedLevels?> { null }
