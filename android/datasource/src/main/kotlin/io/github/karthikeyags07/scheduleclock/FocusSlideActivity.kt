package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.animation.PathInterpolator
import org.json.JSONObject
import java.time.LocalTime
import kotlin.math.min

/**
 * The slide into and out of focus mode on the watch (RULES A26). The face can't animate its Focus layer coming and
 * going, so the phone opens this see-through screen (`app://<package>/focusslide?on=1`) as it switches focus mode,
 * and sends the setting a moment later. It draws the Focus layout exactly as the face's layer does
 * (tools/gen_focus_face.py: same places, sizes, fonts and the colourway's colours) and:
 * - on: slides it in from the right over the face, holds it until the real layer is on underneath, then closes;
 * - off: covers the face with it at once, waits until the real layer is off underneath, then slides it out to the
 *   right, showing the face.
 * Turning off by itself when the countdown reaches zero doesn't come through here: that stays instant.
 */
class FocusSlideActivity : Activity() {
    private lateinit var view: SlideView
    private var turningOn = true
    private val start = SystemClock.uptimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turningOn = intent.getBooleanExtra(EXTRA_ON, true)
        view = SlideView(this, colours(this), Focus.state(this), PhoneSettings.clock24(this))
        view.shown = if (turningOn) 0f else 1f
        setContentView(view)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        view.post(step)
    }

    private var phaseStart = 0L       // when the slide began (0: not yet)
    private var settledAt = 0L        // when the real layer was seen in its new state

    /** Runs every frame: slides, waits for the layer underneath, and closes when the hand-off is done. */
    private val step = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val layerOn = Focus.active(this@FocusSlideActivity)
            val layerDone = layerOn == turningOn
            if (layerDone && settledAt == 0L) settledAt = now
            if (turningOn) {
                if (phaseStart == 0L) phaseStart = now
                view.shown = EASE.getInterpolation(((now - phaseStart) / SLIDE_MS.toFloat()).coerceIn(0f, 1f))
                // In place, and the layer has been on long enough for the face to have drawn it: hand over.
                if (view.shown >= 1f && settledAt != 0L && now - settledAt > HANDOFF_MS) return close()
            } else {
                // Covering until the layer is off underneath, then out to the right.
                if (settledAt != 0L && now - settledAt > HANDOFF_MS && phaseStart == 0L) phaseStart = now
                if (phaseStart != 0L) {
                    view.shown = 1f - EASE.getInterpolation(((now - phaseStart) / SLIDE_MS.toFloat()).coerceIn(0f, 1f))
                    if (view.shown <= 0f) return close()
                }
            }
            if (now - start > GIVE_UP_MS) return close()   // the setting never came: don't sit over the face
            view.invalidate()
            view.postOnAnimation(this)
        }
    }

    private fun close() {
        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) close()
    }

    companion object {
        const val EXTRA_ON = "on"
        private const val SLIDE_MS = 600L
        private const val HANDOFF_MS = 450L      // the face asking for and drawing the layer's new state
        private const val GIVE_UP_MS = 5_000L
        private val EASE = PathInterpolator(.4f, 0f, .2f, 1f)   // as on the phone

        /** The colourway's Focus colours (written by tools/gen_spike_face.py into assets/focus_colours.json). */
        fun colours(context: Context): IntArray {
            val id = FacePush.colourway(context) ?: PhoneSettings.faceNow(context) ?: "amber"
            val all = JSONObject(context.assets.open("focus_colours.json").bufferedReader().readText())
            val c = all.optJSONObject(id) ?: all.getJSONObject("amber")
            return listOf("bg", "track", "text", "muted", "accent").map { Color.parseColor(c.getString(it)) }.toIntArray()
        }
    }
}

/** The Focus layout in the face's 450-unit box, slid [shown] of the way in from the right (0 out, 1 in place). */
private class SlideView(context: Context, colours: IntArray, private val st: Focus.State, private val h24: Boolean) : View(context) {
    var shown = 0f
    private val bg = colours[0]; private val track = colours[1]; private val ink = colours[2]
    private val muted = colours[3]; private val accent = colours[4]
    private val anta = context.resources.getFont(R.font.anta_regular)
    private val sans = Typeface.DEFAULT                          // the face's SYNC_TO_DEVICE
    private val medium = Typeface.create(Typeface.DEFAULT, 500, false)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val clip = Path()

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height) / 450f
        val ox = (width - 450f * s) / 2 + (1f - shown) * width      // slid to the right by what's left to go
        val oy = (height - 450f * s) / 2
        clip.reset(); clip.addCircle(width / 2f, height / 2f, min(width, height) / 2f, Path.Direction.CW)
        canvas.save(); canvas.clipPath(clip)
        canvas.translate(ox, oy); canvas.scale(s, s)
        p.style = Paint.Style.FILL; p.color = bg
        canvas.drawRect(0f, 0f, 450f, 450f, p)
        val nowSec = LocalTime.now().toSecondOfDay()
        if (st.kind == 0) {
            box(canvas, clockText(), anta, 96f, ink, 155f, 110f)
            box(canvas, "No more meetings", sans, 24f, muted, 280f, 32f)
        } else {
            val left = (st.target - nowSec).coerceIn(0, 200000)
            val frac = (left / (st.target - st.from + .001f)).coerceIn(0f, 1f)
            // The ring: the track, and the time left in the accent from 12 o'clock.
            p.style = Paint.Style.STROKE; p.strokeWidth = 12f; p.strokeCap = Paint.Cap.BUTT; p.color = track
            val ring = RectF(225f - 219f, 225f - 219f, 225f + 219f, 225f + 219f)
            canvas.drawArc(ring, 0f, 360f, false, p)
            if (frac > 0f) { p.color = accent; p.strokeCap = Paint.Cap.ROUND; canvas.drawArc(ring, -90f, (360f * frac).coerceAtLeast(1f), false, p) }
            box(canvas, clockText(), anta, 40f, muted, 78f, 50f)
            t.letterSpacing = .08f
            box(canvas, if (st.kind == 1) "ENDS IN" else "STARTS IN", medium, 24f, accent, 138f, 32f)
            t.letterSpacing = 0f
            digits(canvas, left)
            box(canvas, fit(st.title, medium, 30f, 310f), medium, 30f, ink, 296f, 40f)
            box(canvas, fit(st.range, sans, 22f, 270f), sans, 22f, muted, 338f, 30f)
        }
        canvas.restore()
    }

    private fun clockText(): String {
        val n = LocalTime.now()
        return if (h24) "%02d:%02d".format(n.hour, n.minute) else "${(n.hour + 11) % 12 + 1}:${"%02d".format(n.minute)}"
    }

    /** Text centred in a box across the face at [y], [h] high, as the face's PartText draws it. */
    private fun box(canvas: Canvas, text: String, face: Typeface, size: Float, colour: Int, y: Float, h: Float, cx: Float = 225f) {
        t.typeface = face; t.textSize = size; t.color = colour
        val fm = t.fontMetrics
        canvas.drawText(text, cx, y + h / 2 - (fm.ascent + fm.descent) / 2, t)
    }

    private fun fit(text: String, face: Typeface, size: Float, width: Float): String {
        t.typeface = face; t.textSize = size
        return TextUtils.ellipsize(text, t, width, TextUtils.TruncateAt.END).toString()
    }

    /** The countdown, each digit centred in its own step as on the face (mm:ss, h:mm:ss or hh:mm:ss). */
    private fun digits(canvas: Canvas, left: Int) {
        val h = left / 3600; val m = left % 3600 / 60; val sec = left % 60
        fun two(n: Int) = listOf((n / 10).toString(), (n % 10).toString())
        val (size, dw, cw, cells) = when {
            h == 0 -> Quad(104f, 66f, 26f, two(m) + ":" + two(sec))
            h < 10 -> Quad(80f, 51f, 20f, listOf(h.toString(), ":") + two(m) + ":" + two(sec))
            else -> Quad(72f, 46f, 18f, two(h) + ":" + two(m) + ":" + two(sec))
        }
        var x = (450f - cells.sumOf { (if (it == ":") cw else dw).toDouble() }.toFloat()) / 2
        for (c in cells) {
            val w = if (c == ":") cw else dw
            box(canvas, c, anta, size, ink, 168f, 120f, x + w / 2)
            x += w
        }
    }

    private data class Quad(val size: Float, val dw: Float, val cw: Float, val cells: List<String>)
}
