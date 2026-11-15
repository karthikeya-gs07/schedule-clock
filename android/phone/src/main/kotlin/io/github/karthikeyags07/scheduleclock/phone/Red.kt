package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Red mode (RULES A25): a hidden switch. Pushing the nav bar's Home icon up and letting go puts the app's text,
 * icons, accent, switches and frosted glass in soft reds, like a watch's night (tactical) mode, while the
 * backgrounds stay as they are; the face previews turn red on their black dials and the watch gets the red face.
 * Doing it again turns it all back. On the way in, the face on Today flickers quickly into red.
 */
object RedTheme {
    var on by mutableStateOf(false)
    val accent = Color(0xFFFF5A4E)
    val text = Color(0xFFFF9C93)
    val body = Color(0xFFE88A82)
    val muted = Color(0xFFA9605A)
    val faint = Color(0xFF6E3C38)

    /** A frosted-glass tint in red mode: the same darkness, warmed towards red. */
    @Composable fun blur(c: Color): Color = if (!on) c else Color(
        (c.red * 1.45f).coerceAtMost(1f), c.green * .62f, c.blue * .6f, c.alpha)
}

/** [face] is how red the face on Today is (0 to 1; it flickers on the way in). */
class RedLevels(val face: State<Float>)

val LocalRed = staticCompositionLocalOf<RedLevels?> { null }

/** Each colour to a red of its brightness (whites go full red), mixed with the colour as it is by [s]. */
private fun redMatrix(s: Float): ColorMatrix {
    val k = 1f - s
    return ColorMatrix(floatArrayOf(
        k + s * .42f, s * .50f, s * .23f, 0f, 0f,
        0f, k, 0f, 0f, 0f,
        0f, 0f, k, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
}

/** Draws this and everything in it through the red filter, by [level] (read at draw time, so it never recomposes). */
fun Modifier.red(level: () -> Float): Modifier = drawWithContent {
    val s = level()
    if (s <= .001f) { drawContent(); return@drawWithContent }
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(redMatrix(s.coerceAtMost(1f))) }
    drawIntoCanvas { c ->
        c.saveLayer(Rect(Offset.Zero, size).inflate(160f), paint)   // room for things drawn just outside (a pushed-up icon)
        drawContent()
        c.restore()
    }
}

/** The face's red on Today, which flickers on the way in. */
@Composable
fun Modifier.faceRed(): Modifier = LocalRed.current?.let { r -> this.red { r.face.value } } ?: this
