package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.pow
import kotlin.random.Random

/*
 * Smooth gradients on dark screens. A soft glow spans only a few of the 256 brightness levels, so a plain
 * gradient shows as visible steps (banding), and a 3-stop linear ramp adds a crease at each stop. These spread
 * the fade over many eased stops and add a faint, fixed grain that breaks the steps up (dithering).
 */

/**
 * Stops for a glow of [color] fading from [peak] alpha to nothing: (1 − t)^1.7, the same shape as the old
 * peak → ~31 % at the middle → 0 ramps, without their creases.
 */
fun glowStops(color: Color, peak: Float, steps: Int = 16): Array<Pair<Float, Color>> =
    Array(steps + 1) { i -> val t = i / steps.toFloat(); t to color.copy(alpha = peak * (1f - t).pow(1.7f)) }

/** Eased in and out from 0 to 1: for fading an image's edges without a visible line where the fade ends. */
fun easeStops(from: Float, to: Float, fadeIn: Boolean, steps: Int = 8): List<Pair<Float, Color>> = List(steps + 1) { i ->
    val t = i / steps.toFloat()
    val s = t * t * (3 - 2 * t)
    (from + (to - from) * t) to Color.Black.copy(alpha = if (fadeIn) s else 1f - s)
}

// 64 × 64 tile of random light and dark specks, one or two brightness levels at most: invisible as texture,
// but enough to hide gradient steps. Fixed seed, so it never shimmers.
private val grain: ImageBitmap by lazy {
    val n = 64
    val r = Random(7)
    val px = IntArray(n * n) {
        val v = r.nextInt(-3, 4)
        if (v >= 0) (v shl 24) or 0xFFFFFF else ((-v) shl 24)
    }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Draws the faint grain over this element, so the glows under it don't band. */
fun Modifier.dither() = drawWithContent {
    drawContent()
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
}
