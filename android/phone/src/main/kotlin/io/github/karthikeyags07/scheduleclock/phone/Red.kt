package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
 * Red mode (RULES A25): a hidden switch. Pushing the nav bar's Home icon up and letting go turns the whole app red,
 * like a watch's night (tactical) mode; doing it again turns it back. Every colour becomes a red of the same
 * brightness, so black stays black and the face's arcs, ticks and text glow red. On the way in, the face on Today
 * flickers between its own colours and red a few times before it settles.
 *
 * [app] is how red the app is (0 to 1, eases), [face] how red the face on Today is (it flickers).
 */
class RedLevels(val app: State<Float>, val face: State<Float>)

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

/** The app's own red: for every page, bar and panel. */
@Composable
fun Modifier.appRed(): Modifier = LocalRed.current?.let { r -> this.red { r.app.value } } ?: this

/** The face's red on Today, which flickers on the way in. */
@Composable
fun Modifier.faceRed(): Modifier = LocalRed.current?.let { r -> this.red { r.face.value } } ?: this
