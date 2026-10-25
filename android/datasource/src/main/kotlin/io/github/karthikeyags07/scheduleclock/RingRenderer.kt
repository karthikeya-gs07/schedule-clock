package io.github.karthikeyags07.scheduleclock

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import java.io.ByteArrayOutputStream

/**
 * Spike 2: draws the whole ring (meetings, later lane, double-booking stripes, private caution
 * stripes) into one transparent 450 × 450 PNG, sent to the face as a SMALL_IMAGE (PHOTO style).
 * Geometry and colors follow RULES §2 (R1–R10) on the 450 px canvas.
 */
object RingRenderer {
    const val SIZE = 450
    private const val C = SIZE / 2f
    private const val R = 219f              // band 213–225, flush with the edge (R1)
    private const val R_LATER = R - 10f     // later lane (R5)
    private const val BAND = 12f
    private const val GAP_DEG = 1f          // back-to-back gap (R2)
    private const val DIAL_MIN = 12 * 60

    private val AMBER = Color.parseColor("#ffb547")
    private val AMBER_DIM = Color.parseColor("#5a4320")
    private val AMBER_SELECTED = Color.parseColor("#ffd27f")
    private val PRIVATE = Color.parseColor("#8e8e93")
    private val PRIVATE_DIM = Color.parseColor("#48484c")
    private val PRIVATE_SELECTED = Color.parseColor("#c7c7cc")
    private val LATER = Color.parseColor("#8fa3d6")
    private val CLASH = Color.parseColor("#ff7a59")

    /** Draws the ring and returns it as PNG bytes (transparent outside the arcs). */
    fun renderPng(meetings: List<SpikeSchedule.Meeting>, selected: SpikeSchedule.Meeting?): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }

        // No track here: the face draws it, with the comet trail on top, so free time stays transparent.

        // Main ring (R3, R9, R10)
        meetings.forEach { m ->
            if (m.later) return@forEach
            val base = when {
                m.private && selected == null -> PRIVATE
                m.private && m == selected -> PRIVATE_SELECTED
                m.private -> PRIVATE_DIM
                selected == null -> AMBER
                m == selected -> AMBER_SELECTED
                else -> AMBER_DIM
            }
            stroke.strokeWidth = BAND
            stroke.color = base
            stroke.shader = if (m.private) stripes(base, midDeg(m)) else null
            arc(canvas, m.startMin, m.endMin, R, stroke)
            stroke.shader = null
        }

        // Double bookings (R4): blank the band, then two 5 px coral stripes at R ± 4.5
        for ((a, b) in clashRanges(meetings.filterNot { it.later })) {
            stroke.color = Color.BLACK; stroke.strokeWidth = BAND + 2
            arc(canvas, a, b, R, stroke)
            stroke.color = CLASH; stroke.strokeWidth = 5f
            arc(canvas, a, b, R - 4.5f, stroke)
            arc(canvas, a, b, R + 4.5f, stroke)
        }

        // Later lane (R5)
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeWidth = 4f; stroke.color = LATER
        meetings.filter { it.later }.forEach { arc(canvas, it.startMin, it.endMin, R_LATER, stroke) }

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun arc(canvas: Canvas, from: Int, to: Int, r: Float, paint: Paint) {
        val start = (from % DIAL_MIN) / DIAL_MIN.toFloat() * 360f - 90f + GAP_DEG
        val sweep = (to - from) / DIAL_MIN.toFloat() * 360f - 2 * GAP_DEG
        canvas.drawArc(RectF(C - r, C - r, C + r, C + r), start, sweep.coerceAtLeast(0.5f), false, paint)
    }

    private fun midDeg(m: SpikeSchedule.Meeting) =
        (((m.startMin + m.endMin) / 2) % DIAL_MIN) / DIAL_MIN.toFloat() * 360f

    /** Caution band (R10): 5 px on / 5 px off, at 45° to the arc at its middle. */
    private fun stripes(color: Int, midDeg: Float): Shader {
        val tile = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        Canvas(tile).drawRect(0f, 0f, 10f, 5f, Paint().apply { this.color = color })
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setRotate(midDeg + 45f) })
        }
    }

    /** Merged minute ranges where two or more meetings overlap. */
    private fun clashRanges(main: List<SpikeSchedule.Meeting>): List<Pair<Int, Int>> {
        val pairs = buildList {
            for (i in main.indices) for (j in i + 1 until main.size) {
                val a = maxOf(main[i].startMin, main[j].startMin)
                val b = minOf(main[i].endMin, main[j].endMin)
                if (b > a) add(a to b)
            }
        }.sortedBy { it.first }
        return pairs.fold(mutableListOf()) { merged, r ->
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.second) merged[merged.size - 1] = last.first to maxOf(last.second, r.second)
            else merged += r
            merged
        }
    }
}
