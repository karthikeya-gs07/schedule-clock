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
import java.io.ByteArrayOutputStream

/**
 * Draws the whole ring (main-ring meetings with elapsed parts, later lane, double-booking stripes,
 * private caution stripes) into one transparent 450 × 450 PNG, sent to the face as a SMALL_IMAGE
 * (PHOTO style). The face draws the track and trail underneath. Geometry and colors follow RULES §2.
 */
object RingRenderer {
    const val SIZE = 450
    private const val C = SIZE / 2f
    private const val R = 219f              // band 213–225, flush with the edge (R1)
    private const val R_LATER = R - 10f     // later lane (R5)
    private const val BAND = 12f
    private const val GAP_DEG = 1f          // back-to-back gap (R2)

    private val AMBER = Color.parseColor("#ffb547")
    private val AMBER_PAST = Color.parseColor("#6e4f22")
    private val AMBER_DIM = Color.parseColor("#5a4320")
    private val PRIVATE = Color.parseColor("#8e8e93")
    private val PRIVATE_PAST = Color.parseColor("#48484c")
    private val PRIVATE_DIM = Color.parseColor("#48484c")
    private val LATER = Color.parseColor("#8fa3d6")
    private val CLASH = Color.parseColor("#ff7a59")
    private val CLASH_PAST = Color.parseColor("#7a3b2c")

    /**
     * @param selectedKey dims every other meeting (R9); the highlight itself is the selected-arc slot.
     * @param hiddenKey left out entirely (a meeting the settle arc is carrying, M3).
     */
    fun renderPng(list: List<Placed>, now: Int, selectedKey: String?, hiddenKey: String? = null): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
        val shown = list.filter { it.meeting.key != hiddenKey }

        // Main ring (R3, R9, R10): elapsed part dimmer, private meetings as caution stripes.
        for (p in shown.filter { it.lane == Lane.MAIN }) {
            val m = p.meeting
            val dim = selectedKey != null && m.key != selectedKey
            val color = when {
                m.private -> if (dim) PRIVATE_DIM else PRIVATE
                else -> if (dim) AMBER_DIM else AMBER
            }
            val past = if (m.private) PRIVATE_PAST else AMBER_PAST
            val split = if (ScheduleRules.isLive(p, now)) now else p.cs
            stroke.strokeWidth = BAND
            paintArc(canvas, stroke, p.cs, split, R, past, m.private)
            paintArc(canvas, stroke, split, p.ce, R, color, m.private)
        }

        // Double bookings (R4): blank the band, then two 5 px coral stripes at R ± 4.5.
        for ((a, b) in ScheduleRules.clashRanges(shown)) {
            for ((from, to, color) in listOf(Triple(a, minOf(b, maxOf(a, now)), CLASH_PAST), Triple(maxOf(a, now), b, CLASH))) {
                if (to <= from) continue
                stroke.shader = null
                stroke.color = Color.BLACK; stroke.strokeWidth = BAND + 2
                arc(canvas, from, to, R, stroke)
                stroke.color = color; stroke.strokeWidth = 5f
                arc(canvas, from, to, R - 4.5f, stroke)
                arc(canvas, from, to, R + 4.5f, stroke)
            }
        }

        // Later lane (R5): thin blue, rounded; private ones grey and dashed.
        stroke.shader = null
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeWidth = 4f
        for (p in shown.filter { it.lane == Lane.LATER }) {
            stroke.color = if (p.meeting.private) PRIVATE else LATER
            stroke.pathEffect = if (p.meeting.private) DashPathEffect(floatArrayOf(4f, 3f), 0f) else null
            arc(canvas, p.cs, p.ce, R_LATER, stroke)
        }

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun paintArc(canvas: Canvas, paint: Paint, from: Int, to: Int, r: Float, color: Int, private: Boolean) {
        if (to <= from) return
        paint.color = color
        paint.shader = if (private) stripes(color, midDeg(from, to)) else null
        arc(canvas, from, to, r, paint)
        paint.shader = null
    }

    private fun arc(canvas: Canvas, from: Int, to: Int, r: Float, paint: Paint) {
        val start = (from % ScheduleRules.DIAL_MIN) / ScheduleRules.DIAL_MIN.toFloat() * 360f - 90f + GAP_DEG
        val sweep = (to - from) / ScheduleRules.DIAL_MIN.toFloat() * 360f - 2 * GAP_DEG
        canvas.drawArc(RectF(C - r, C - r, C + r, C + r), start, sweep.coerceAtLeast(0.5f), false, paint)
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
