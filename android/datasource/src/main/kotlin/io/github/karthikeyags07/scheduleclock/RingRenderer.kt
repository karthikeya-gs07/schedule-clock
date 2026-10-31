package io.github.karthikeyags07.scheduleclock

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import java.time.LocalDate
import java.time.ZoneId
import java.io.ByteArrayOutputStream

/**
 * Draws the whole ring into one transparent PNG, sent to the face as a SMALL_IMAGE (PHOTO style). The face
 * draws the track and trail underneath. Geometry and colors follow RULES §2.
 *
 * Colour themes (RULES §9): the face multiplies an image by a tint, and the theme is picked on the watch,
 * where this app can't see it. So the image is three 450 px panels side by side, and the face draws it
 * three times, shifted so one panel lands on the screen each time (the rest is off-screen):
 * - panel 0, tinted with the theme's meeting colour: main-ring meetings in white, elapsed parts grey;
 * - panel 1, tinted with the theme's lane colour: the later lane in white;
 * - panel 2, untinted: fixed colours (private stripes, double bookings, coral lane on an all-day event);
 * - panel 3, untinted: the Solaris face's main-ring meetings (RULES §10), coloured by the light at each
 *   minute, or a Palette theme's, running through its colours over the 12 hours ahead. Those faces show
 *   panel 3 instead of panel 0.
 * The untinted panels are drawn in the colours of [Theme] (the face on the watch), and dim toward its background.
 */
object RingRenderer {
    const val SIZE = 450
    const val PANELS = 4
    private const val C = SIZE / 2f
    private const val R = 219f              // band 213–225, flush with the edge (R1)
    private const val R_LATER = R - 10f     // later lane (R5)
    private const val BAND = 12f
    private const val GAP_DEG = 1f          // back-to-back gap (R2)

    private const val THEMED = Color.WHITE                 // × the theme colour on the face
    private const val PAST_FACTOR = 0.45f
    private val THEMED_PAST = Color.rgb(115, 115, 115)      // × 0.45: the elapsed part (amber → #73511f ≈ #6e4f22)
    // Fixed colours (panel 2) come from the theme: private stripes, double bookings and the dark band between
    // the two clash stripes, never empty (R6a). Elapsed and dimmed copies blend toward the face's background.
    private var PRIVATE = 0
    private var PRIVATE_PAST = 0
    private var CLASH = 0
    private var CLASH_PAST = 0
    private var CLASH_MASK = 0
    private var ground = Color.BLACK
    private const val DIM = 0.35f           // everything but the selected meeting, while one is selected (R9)

    /**
     * @param selectedKey dims everything else to 35 % and draws this meeting on top, unchanged (R9).
     * @param hiddenKey left out entirely (a meeting the settle arc is carrying, M3).
     */
    @Synchronized
    fun renderPng(list: List<Placed>, now: Int, selectedKey: String?, hiddenKey: String? = null, theme: Theme = Themes.of(null)): ByteArray {
        ground = theme.background
        PRIVATE = theme.private; PRIVATE_PAST = blend(theme.private, 0.5f)
        CLASH = theme.clash; CLASH_PAST = blend(theme.clash, 0.48f); CLASH_MASK = blend(theme.clash, 0.25f)
        val bitmap = Bitmap.createBitmap(SIZE * PANELS, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
        val shown = list.filter { it.meeting.key != hiddenKey }
        val dim = selectedKey != null
        val selected = shown.firstOrNull { it.meeting.key == selectedKey }
        fun fixed(p: Placed) = p.meeting.private || (p.lane == Lane.LATER && p.allDay.isNotEmpty())
        fun panel(i: Int, draw: () -> Unit) { canvas.save(); canvas.translate(SIZE * i.toFloat(), 0f); draw(); canvas.restore() }

        // Panel 0: main-ring meetings (R3), elapsed part dimmer. Round caps (R1a).
        // The selected meeting is drawn last on its panel, above anything it overlaps (R9).
        fun last(ps: List<Placed>) = ps.sortedBy { it === selected }
        panel(0) { for (p in last(shown.filter { it.lane == Lane.MAIN && !fixed(it) })) drawMain(canvas, stroke, p, now, dim && p !== selected) }

        // Panel 1: the later lane (R5), thin and rounded.
        panel(1) { for (p in last(shown.filter { it.lane == Lane.LATER && !fixed(it) })) drawLater(canvas, stroke, p, dim && p !== selected) }

        // Panel 3: Solaris (S2) or a Palette theme, the same meetings as panel 0, drawn through the colour sweep.
        val sweep = Sweep(shown, now, theme)
        panel(3) { for (p in last(shown.filter { it.lane == Lane.MAIN && !fixed(it) })) drawShift(canvas, stroke, p, now, sweep, dim && p !== selected) }

        panel(2) {
            // Private meetings as caution stripes (R10), dashed on the lane; coral lane arcs on an all-day event (W9).
            for (p in shown.filter { fixed(it) && it !== selected }) {
                if (p.lane == Lane.MAIN) drawMain(canvas, stroke, p, now, dim) else drawLater(canvas, stroke, p, dim)
            }
            // Double bookings (R4): blank the band, then two 5 px coral stripes at R ± 4.5. Never over the selected
            // meeting (R9: it shows above them), which here means leaving its stretch out, since it's on panel 0.
            val keepOut = selected?.takeIf { it.lane == Lane.MAIN && !fixed(it) }?.let { it.cs to it.ce }
            for ((a, b) in ScheduleRules.clashRanges(shown).flatMap { minus(it, keepOut) }) {
                for ((from, to, color) in listOf(Triple(a, minOf(b, maxOf(a, now)), CLASH_PAST), Triple(maxOf(a, now), b, CLASH))) {
                    if (to <= from) continue
                    stroke.shader = null
                    stroke.pathEffect = null
                    stroke.strokeCap = Paint.Cap.BUTT
                    stroke.color = dimmed(CLASH_MASK, dim); stroke.strokeWidth = BAND + 2
                    arc(canvas, from, to, R, stroke)
                    stroke.strokeCap = Paint.Cap.ROUND
                    stroke.color = dimmed(color, dim); stroke.strokeWidth = 5f
                    arc(canvas, from, to, R - 4.5f, stroke)
                    arc(canvas, from, to, R + 4.5f, stroke)
                }
            }
            // R4a: where 3 meetings run at once the inner stripe splits into two thin halves; at 4 (or more) the outer
            // one splits too. Drawn over the two stripes: the half to split is blanked, then two 2 px stripes.
            val clashEnds = ScheduleRules.clashRanges(shown).flatMap { listOf(it.first, it.second) }.toSet()
            for ((a0, b0, depth) in ScheduleRules.clashDepths(shown)) {
                for ((a, b) in minus(a0 to b0, keepOut)) {
                    for ((from, to, color) in listOf(Triple(a, minOf(b, maxOf(a, now)), CLASH_PAST), Triple(maxOf(a, now), b, CLASH))) {
                        if (to <= from) continue
                        for (side in if (depth >= 4) listOf(-1f, 1f) else listOf(-1f)) {
                            stroke.shader = null; stroke.pathEffect = null; stroke.strokeCap = Paint.Cap.BUTT
                            stroke.color = dimmed(CLASH_MASK, dim); stroke.strokeWidth = 6f
                            // Where the piece meets the clash's own end, stop where the round stripe ends do.
                            val inStart = from in clashEnds; val inEnd = to in clashEnds
                            arcFlat(canvas, from, to, R + side * 4.5f, stroke, inStart, inEnd)
                            stroke.color = dimmed(color, dim); stroke.strokeWidth = 2f
                            arcFlat(canvas, from, to, R + side * 3f, stroke, inStart, inEnd)
                            arcFlat(canvas, from, to, R + side * 6f, stroke, inStart, inEnd)
                        }
                    }
                }
            }
            // R9: a selected private (or coral lane) meeting at full strength, above the clash stripes.
            selected?.takeIf { fixed(it) }?.let {
                if (it.lane == Lane.MAIN) drawMain(canvas, stroke, it, now, dim = false) else drawLater(canvas, stroke, it, dim = false)
            }
        }

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun drawShift(canvas: Canvas, stroke: Paint, p: Placed, now: Int, sweep: Sweep, dim: Boolean) {
        val split = if (ScheduleRules.isLive(p, now)) now else p.cs
        stroke.pathEffect = null
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeWidth = BAND
        stroke.color = Color.WHITE
        val d = if (dim) DIM else 1f
        for ((from, to, f) in listOf(Triple(p.cs, split, PAST_FACTOR * d), Triple(split, p.ce, d))) {
            if (to <= from) continue
            stroke.shader = sweep.shader(f)
            arc(canvas, from, to, R, stroke)
        }
        stroke.shader = null
    }

    /**
     * The colours around the dial. Solaris (S2): each degree is coloured for the time it shows inside the visible
     * 12 h window (from now, or a live meeting's start, W2), so morning and evening never mix. A Palette theme:
     * its colours in turn from the window's start to its end. Darker copies for elapsed parts and dimmed meetings.
     */
    private class Sweep(shown: List<Placed>, now: Int, theme: Theme) {
        private val colors: IntArray
        private val cache = HashMap<Float, Shader>()

        init {
            val winStart = minOf(now, shown.filter { ScheduleRules.isLive(it, now) }.minOfOrNull { it.meeting.startMin } ?: now)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val place = SunColors.place(zone)
            val days = HashMap<Int, List<Pair<Double, DoubleArray>>>()
            val palette = theme.palette
            colors = IntArray(361) { i ->
                val m = i * 2                                                   // dial minute (720 per turn)
                val ahead = Math.floorMod(m - winStart, ScheduleRules.DIAL_MIN)
                if (palette != null) return@IntArray along(palette, ahead / ScheduleRules.DIAL_MIN.toFloat())
                val t = winStart + ahead                                        // the time this angle shows
                val day = Math.floorDiv(t, 1440)
                val stops = days.getOrPut(day) { SunColors.stops(SunColors.sunTimes(today.plusDays(day.toLong()), place, zone)) }
                SunColors.colorAt(stops, Math.floorMod(t, 1440).toDouble())
            }
        }

        fun shader(factor: Float): Shader = cache.getOrPut(factor) {
            val c = IntArray(colors.size) { i -> blend(colors[i], factor) }
            SweepGradient(C, C, c, FloatArray(c.size) { it / 360f }).apply {
                setLocalMatrix(Matrix().apply { setRotate(-90f, C, C) })   // 0 at 12 o'clock
            }
        }
    }

    /** The colour [f] (0..1) of the way through [palette], blended evenly between neighbours. */
    private fun along(palette: IntArray, f: Float): Int {
        val x = f * (palette.size - 1)
        val i = x.toInt().coerceIn(0, palette.size - 2)
        val t = x - i
        val a = palette[i]; val b = palette[i + 1]
        fun ch(ca: Int, cb: Int) = (ca + (cb - ca) * t).toInt()
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    /** [f] of [color] over the face's background (black on the first five colourways). */
    private fun blend(color: Int, f: Float): Int {
        fun ch(c: Int, g: Int) = (c * f + g * (1 - f)).toInt()
        return Color.rgb(ch(Color.red(color), Color.red(ground)), ch(Color.green(color), Color.green(ground)), ch(Color.blue(color), Color.blue(ground)))
    }

    /** A minute range with another one taken out (0, 1 or 2 pieces). */
    private fun minus(r: Pair<Int, Int>, cut: Pair<Int, Int>?): List<Pair<Int, Int>> {
        if (cut == null || cut.second <= r.first || cut.first >= r.second) return listOf(r)
        return listOfNotNull((r.first to cut.first).takeIf { cut.first > r.first }, (cut.second to r.second).takeIf { cut.second < r.second })
    }

    private fun drawMain(canvas: Canvas, stroke: Paint, p: Placed, now: Int, dim: Boolean) {
        val m = p.meeting
        val split = if (ScheduleRules.isLive(p, now)) now else p.cs
        stroke.pathEffect = null
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeWidth = BAND
        paintArc(canvas, stroke, p.cs, split, R, dimmed(if (m.private) PRIVATE_PAST else THEMED_PAST, dim), m.private)
        paintArc(canvas, stroke, split, p.ce, R, dimmed(if (m.private) PRIVATE else THEMED, dim), m.private)
    }

    private fun drawLater(canvas: Canvas, stroke: Paint, p: Placed, dim: Boolean) {
        stroke.shader = null
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeWidth = 4f
        val color = if (p.allDay.isNotEmpty()) CLASH else if (p.meeting.private) PRIVATE else THEMED   // coral on an all-day event (W9)
        stroke.color = dimmed(color, dim)
        stroke.pathEffect = if (p.meeting.private) DashPathEffect(floatArrayOf(4f, 3f), 0f) else null
        arc(canvas, p.cs, p.ce, R_LATER, stroke)
        stroke.pathEffect = null
    }

    /**
     * 35 % of the color over the background: what the prototype's 35 % opacity ring looks like. The tinted
     * panels' white and grey dim toward black, since the face multiplies them by the theme colour.
     */
    private fun dimmed(color: Int, dim: Boolean) = when {
        !dim -> color
        color == THEMED || color == THEMED_PAST ->
            Color.rgb((Color.red(color) * DIM).toInt(), (Color.green(color) * DIM).toInt(), (Color.blue(color) * DIM).toInt())
        else -> blend(color, DIM)
    }

    private fun paintArc(canvas: Canvas, paint: Paint, from: Int, to: Int, r: Float, color: Int, private: Boolean) {
        if (to <= from) return
        paint.color = color
        paint.shader = if (private) stripes(color, midDeg(from, to)) else null
        arc(canvas, from, to, r, paint)
        paint.shader = null
    }

    /**
     * Round caps (R1a) reach half the stroke width past the path's ends, so a round-capped arc is shortened
     * by that angle: its visible ends stay at the meeting's start and end, and a very short meeting is a dot.
     */
    private fun arc(canvas: Canvas, from: Int, to: Int, r: Float, paint: Paint) {
        val capDeg = if (paint.strokeCap == Paint.Cap.ROUND) Math.toDegrees((paint.strokeWidth / 2f / r).toDouble()).toFloat() else 0f
        val inset = GAP_DEG + capDeg
        val start = (from % ScheduleRules.DIAL_MIN) / ScheduleRules.DIAL_MIN.toFloat() * 360f - 90f + inset
        val sweep = (to - from) / ScheduleRules.DIAL_MIN.toFloat() * 360f - 2 * inset
        canvas.drawArc(RectF(C - r, C - r, C + r, C + r), start, sweep.coerceAtLeast(if (capDeg > 0f) 0.01f else 0.5f), false, paint)
    }

    /** A butt-capped arc from [from] to [to] with no gap at the ends: for pieces inside a stripe (R4a). */
    private fun arcFlat(canvas: Canvas, from: Int, to: Int, r: Float, paint: Paint, insetStart: Boolean = false, insetEnd: Boolean = false) {
        val inset = GAP_DEG + Math.toDegrees((2.5f / R).toDouble()).toFloat()   // a 5 px round stripe's end
        val a = if (insetStart) inset else 0f
        val b = if (insetEnd) inset else 0f
        val start = (from % ScheduleRules.DIAL_MIN) / ScheduleRules.DIAL_MIN.toFloat() * 360f - 90f + a
        val sweep = (to - from) / ScheduleRules.DIAL_MIN.toFloat() * 360f - a - b
        if (sweep <= 0f) return
        canvas.drawArc(RectF(C - r, C - r, C + r, C + r), start, sweep, false, paint)
    }

    private fun midDeg(from: Int, to: Int) =
        (((from + to) / 2) % ScheduleRules.DIAL_MIN) / ScheduleRules.DIAL_MIN.toFloat() * 360f

    /** Caution band (R10): 5 px on / 5 px off, at 45° to the arc at its middle. */
    private fun stripes(color: Int, midDeg: Float): Shader {
        val tile = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        Canvas(tile).drawRect(0f, 0f, 10f, 5f, Paint().apply { this.color = color })
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setRotate(midDeg + 45f) })
        }
    }
}
