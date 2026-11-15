package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The compass (RULES A19), opened by tapping the 12 on the face (`app://<package>/compass`), in the colourway: a
 * degree dial that turns with the watch, an accent arrow fixed at the top, and the heading in the middle. The dial's
 * 0° spoke is in the accent too: when it meets the arrow, the watch's 12 faces north. The sensor runs only while open.
 *
 * Calibration (2026-11-15): the magnetometer reports how well calibrated it is (unreliable, low, medium, high). A small
 * handle sits at the bottom; swiping up opens a drawer with the compass's accuracy and a Calibrate button (swipe
 * down closes it). When the accuracy is low a Calibrate button shows above the handle too. Calibrate opens a guide:
 * a figure 8 with a dot running round it to follow with the wrist, and the accuracy filling up live; it closes by
 * itself once it's high (Wear OS has no "calibrate" call: moving the watch in a figure 8 is how the magnetometer
 * recalibrates, and Samsung's compass offers nothing other apps can open).
 */
class CompassActivity : Activity(), SensorEventListener {
    private lateinit var dial: CompassView
    private lateinit var sensors: SensorManager
    private var sensor: Sensor? = null
    private var magnet: Sensor? = null   // only for its calibration level

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dial = CompassView(this, Themes.of(FacePush.colourway(this) ?: PhoneSettings.colourway(this)))
        setContentView(dial)
        sensors = getSystemService(SensorManager::class.java)
        sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) dial.noSensor = true
        magnet = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        dial.haptics = { dial.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }
        // Debug builds: `app://<package>/compassguide` shows the guide as if the accuracy were low.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("guide", false)) dial.showGuideForTest()
    }

    override fun onResume() {
        super.onResume()
        sensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnet?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        sensors.unregisterListener(this)
        super.onPause()
    }

    // Leaving by home (or anything else) closes it, so the next tap on the face opens what was tapped.
    override fun onStop() {
        super.onStop()
        finish()
    }

    private val rotation = FloatArray(9)
    private val angles = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) { dial.setAccuracy(event.accuracy); return }
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, angles)
        val heading = (Math.toDegrees(angles[0].toDouble()).toFloat() + 360f) % 360f   // 0 = the watch's 12 faces north
        dial.update(heading)
    }

    override fun onAccuracyChanged(s: Sensor, accuracy: Int) {
        if (s.type == Sensor.TYPE_MAGNETIC_FIELD) dial.setAccuracy(accuracy)
    }
}

private class CompassView(context: Context, private val theme: Theme) : View(context) {
    var noSensor = false
    var haptics: () -> Unit = {}
    /** The magnetometer's calibration: -1 not known yet, 0 unreliable, 1 low, 2 medium, 3 high. */
    private var accuracy = -1
    private val needsCalibration get() = accuracy in 0..SensorManager.SENSOR_STATUS_ACCURACY_LOW

    fun setAccuracy(a: Int) {
        if (a == accuracy || testHold) return
        accuracy = a
        if (calibrating && a >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH && doneAt == 0L) { doneAt = System.nanoTime(); haptics() }
        postInvalidateOnAnimation()
    }

    // ---- The drawer and the calibration guide ----
    private var drawer = 0f          // 0 closed … 1 open, as drawn
    private var drawerTarget = 0f
    private var dragFrom = Float.NaN // the drawer's position when a drag started
    private var downY = 0f; private var downX = 0f; private var dragging = false
    private var calibrating = false
    private var calStart = 0L        // when the guide opened
    private var doneAt = 0L          // when it reached high (it closes a moment later)
    private var calShown = 0f        // the guide's fade, 0 … 1
    private val S get() = min(width, height) / 450f

    private fun startCalibration() {
        calibrating = true; calStart = System.nanoTime(); doneAt = 0L; drawerTarget = 0f
        if (accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH) { doneAt = calStart }   // already fine: says so and closes
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        val s = S; val panel = PANEL_H * s
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downY = e.y; downX = e.x; dragging = false; dragFrom = drawer; return true }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dy = e.y - downY
                if (!dragging && abs(dy) > 12f * s && !calibrating) dragging = true
                if (dragging) { drawer = (dragFrom - dy / panel).coerceIn(0f, 1f); drawerTarget = drawer; invalidate() }
                return true
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dy = e.y - downY
                if (calibrating) {
                    if (dy > 40f * s) { calibrating = false; postInvalidateOnAnimation() }        // swipe down: stop
                    else if (abs(dy) < 12f * s && failed() && inButton(e.x, e.y, 385f)) startCalibration()   // Try again
                    return true
                }
                if (dragging) {
                    drawerTarget = if (dy < 0) (if (drawer > .25f) 1f else 0f) else (if (drawer < .75f) 0f else 1f)
                } else if (drawer > .5f && inButton(e.x, e.y, 385f)) startCalibration()
                else if (drawer < .5f && needsCalibration && inButton(e.x, e.y, 345f)) startCalibration()
                else if (drawer < .5f && e.y > height - 60f * s) drawerTarget = 1f   // a tap on the handle opens it too
                else if (drawer > .5f && e.y < height - panel) drawerTarget = 0f    // a tap above the drawer closes it
                dragging = false
                postInvalidateOnAnimation()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    /** A button centred at [cy] (450 units) on the screen's centre line, 190 × 56. */
    private fun inButton(x: Float, y: Float, cy: Float): Boolean {
        val s = S; val bx = width / 2f; val by = (height / 2f - 225f * s) + cy * s
        return abs(x - bx) < 105f * s && abs(y - by) < 34f * s
    }

    private var testHold = false
    fun showGuideForTest() { accuracy = 1; testHold = true; startCalibration() }

    private fun failed() = calibrating && doneAt == 0L && System.nanoTime() - calStart > CAL_LIMIT_NS
    private fun accuracyName() = when (accuracy) { 3 -> "High"; 2 -> "Medium"; 1 -> "Low"; 0 -> "Not calibrated"; else -> "Checking…" }
    private var shown = Float.NaN   // the heading on screen
    private var target = Float.NaN  // the sensor's heading
    private var lastFrame = 0L

    fun update(heading: Float) {
        target = heading
        if (shown.isNaN()) shown = heading
        postInvalidateOnAnimation()
    }

    /** Eases the dial towards the sensor's heading frame by frame (about 120 ms to settle), the shortest way round. */
    private fun step() {
        if (target.isNaN()) return
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) .016f else ((now - lastFrame) / 1e9f).coerceIn(0f, .05f)
        lastFrame = now
        val d = ((target - shown + 540f) % 360f) - 180f
        shown = (shown + d * (1f - kotlin.math.exp(-dt / .12f)) + 360f) % 360f
        if (abs(d) > .05f) postInvalidateOnAnimation() else lastFrame = 0L
    }

    private val grey = theme.inkSoft
    private val track = blend(theme.background, Color.WHITE, .12f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val anta = context.resources.getFont(R.font.anta_regular)   // the face's clock and number font
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = anta }
    private val plain = Typeface.create("sans-serif", Typeface.NORMAL)

    override fun onDraw(canvas: Canvas) {
        step()
        // The drawer and the guide ease to where they're going.
        if (!dragging && abs(drawerTarget - drawer) > .002f) { drawer += (drawerTarget - drawer) * .25f; postInvalidateOnAnimation() }
        else if (!dragging) drawer = drawerTarget
        val calTarget = if (calibrating) 1f else 0f
        if (abs(calTarget - calShown) > .01f) { calShown += (calTarget - calShown) * .2f; postInvalidateOnAnimation() } else calShown = calTarget
        if (calibrating && doneAt != 0L && System.nanoTime() - doneAt > 1_400_000_000L) calibrating = false   // "Calibrated", then back
        if (calibrating) postInvalidateOnAnimation()   // the dot runs round the 8
        val s = min(width, height) / 450f            // the face's 450-unit box
        val cx = width / 2f; val cy = height / 2f; val r = 219f * s
        canvas.drawColor(theme.background)
        p.style = Paint.Style.STROKE   // no track round the edge
        val heading = if (shown.isNaN()) 0f else shown
        // The dial turns so that north (its 0° spoke) sits where north is; the arrow stays at the top.
        // Spokes every 2°, longer every 10°, longest every 30° with the degree beside it; 0° in the accent.
        // Near the arrow the spokes take its colour, fading to white over 45° either side.
        for (d in 0 until 360 step 2) {
            val at = ((d - heading) % 360f + 360f) % 360f            // where the spoke is now, 0 = top
            val off = min(at, 360f - at)                              // degrees from the arrow
            val a = Math.toRadians(at.toDouble())
            val major = d % 30 == 0; val mid = d % 10 == 0
            val len = if (major) 16f else if (mid) 10f else 5f
            val r0 = 225f * s; val r1 = (225f - len - 8f) * s   // from the very edge of the screen
            p.strokeWidth = (if (d == 0) 5f else if (major) 2.5f else if (mid) 1.8f else 1.2f) * s
            val base = if (major) theme.ink else if (mid) blend(theme.ink, theme.background, .35f) else blend(theme.ink, theme.background, .55f)
            p.color = if (d == 0) theme.accent else if (off < 45f) blend(theme.accent, base, off / 45f) else base
            canvas.drawLine(cx + r0 * sin(a).toFloat(), cy - r0 * cos(a).toFloat(), cx + r1 * sin(a).toFloat(), cy - r1 * cos(a).toFloat(), p)
            if (major) {
                val rl = r - 44f * s   // well inside the longest spokes, so wide numbers (240, 210…) stay clear
                text.textSize = 21f * s; text.color = if (d == 0) theme.accent else grey
                val label = when (d) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$d" }   // the four directions by letter
                val letter = d % 90 == 0
                if (letter) { text.textSize = 30f * s; text.isFakeBoldText = true }   // Anta has one weight: bold is drawn heavier
                canvas.drawText(label, cx + rl * sin(a).toFloat(), cy - rl * cos(a).toFloat() + (if (letter) 10.5f else 7.5f) * s, text)
                text.isFakeBoldText = false
            }
        }
        // The arrow, fixed at the top on the ring, pointing in, with slightly rounded corners.
        p.style = Paint.Style.FILL; p.color = theme.accent; p.pathEffect = android.graphics.CornerPathEffect(4f * s)
        val tip = cy - 225f * s + 34f * s; val base = cy - 225f * s   // from the edge
        canvas.drawPath(Path().apply { moveTo(cx, tip); lineTo(cx - 12f * s, base); lineTo(cx + 12f * s, base); close() }, p)
        p.pathEffect = null
        // Middle: the heading, in the arrow's colour and the face's font.
        text.color = theme.accent; text.textSize = 84f * s
        canvas.drawText(if (shown.isNaN()) "—" else "${shown.roundToInt() % 360}°", cx, cy + 20f * s, text)
        // Under it, the direction: N, NE, E, SE, S, SW, W or NW.
        if (!shown.isNaN()) {
            text.color = theme.ink; text.textSize = 30f * s
            canvas.drawText(cardinal(shown), cx, cy + 64f * s, text)
        }
        if (noSensor) {
            text.typeface = plain; text.color = grey; text.textSize = 22f * s
            canvas.drawText("No compass on this watch", cx, cy + 96f * s, text)
            text.typeface = anta
        }
        if (!noSensor) drawDrawer(canvas, s, cx, cy)
        if (calShown > 0f) drawGuide(canvas, s, cx, cy)
    }

    private val top get() = height / 2f - 225f * S   // the 450-unit box's top

    /** The handle at the bottom, the Calibrate nudge when it's needed, and the drawer itself. */
    private fun drawDrawer(canvas: Canvas, s: Float, cx: Float, cy: Float) {
        val y0 = top
        val closed = 1f - drawer
        // Low accuracy: a Calibrate button above the handle (fades as the drawer opens).
        if (needsCalibration && closed > 0f && !calibrating) {
            pill(canvas, cx, y0 + 345f * s, 170f * s, 50f * s, outline = true, alpha = closed)
            text.typeface = plain; text.isFakeBoldText = true; text.textSize = 22f * s; text.color = withAlpha(theme.accent, closed)
            canvas.drawText("Calibrate", cx, y0 + 353f * s, text); text.isFakeBoldText = false; text.typeface = anta
        }
        // The drawer: a panel rising from the bottom, a little lighter than the dial.
        val panelTop = y0 + 450f * s - PANEL_H * s * drawer
        if (drawer > 0f) {
            p.style = Paint.Style.FILL; p.color = blend(theme.background, Color.WHITE, .09f)
            canvas.drawRoundRect(cx - 240f * s, panelTop, cx + 240f * s, y0 + 470f * s, 34f * s, 34f * s, p)
            val a = ((drawer - .3f) / .7f).coerceIn(0f, 1f)   // its contents come in once it's mostly open
            text.typeface = plain
            text.textSize = 20f * s; text.color = withAlpha(grey, a)
            canvas.drawText("Compass accuracy", cx, panelTop + 66f * s, text)
            text.textSize = 30f * s; text.isFakeBoldText = true
            text.color = withAlpha(when { accuracy >= 3 -> theme.accent; accuracy == 2 -> theme.ink; accuracy < 0 -> grey; else -> theme.clash }, a)
            canvas.drawText(accuracyName(), cx, panelTop + 104f * s, text); text.isFakeBoldText = false
            pill(canvas, cx, panelTop + 155f * s, 190f * s, 56f * s, outline = false, alpha = a)
            text.textSize = 23f * s; text.isFakeBoldText = true; text.color = withAlpha(theme.background, a)
            canvas.drawText("Calibrate", cx, panelTop + 163f * s, text); text.isFakeBoldText = false
            text.typeface = anta
        }
        // The handle, on the drawer's top edge (at the bottom of the screen when closed).
        p.style = Paint.Style.FILL; p.color = if (needsCalibration && drawer < .5f) theme.accent else blend(theme.ink, theme.background, .5f)
        val hy = if (drawer > 0f) panelTop + 18f * s else y0 + 417f * s   // just above the spokes
        canvas.drawRoundRect(cx - 22f * s, hy - 3f * s, cx + 22f * s, hy + 3f * s, 3f * s, 3f * s, p)
    }

    /** The guide: a figure 8 with a dot to follow, the accuracy filling up, and a word on how it's going. */
    private fun drawGuide(canvas: Canvas, s: Float, cx: Float, cy: Float) {
        val a = calShown
        p.style = Paint.Style.FILL; p.color = withAlpha(theme.background, a)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        val y0 = top
        val done = doneAt != 0L
        text.typeface = plain
        text.textSize = 24f * s; text.color = withAlpha(theme.ink, a); text.isFakeBoldText = true
        canvas.drawText(when { done -> "Calibrated"; failed() -> "Still not calibrated"; else -> "Move your watch in a figure 8" }, cx, y0 + 96f * s, text)
        text.isFakeBoldText = false
        if (!done) {
            // The 8 on its side (a lemniscate), 230 wide, and a dot running round it about every 2.4 s.
            val w = 115f * s; val h = 62f * s; val my = y0 + 215f * s
            val path = Path()
            for (i in 0..120) {
                val t = i / 120.0 * 2 * Math.PI
                val d = 1 + sin(t) * sin(t)
                val x = cx + (w * cos(t) / d).toFloat(); val y = my + (h * 1.6f * sin(t) * cos(t) / d).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 6f * s; p.color = withAlpha(blend(theme.ink, theme.background, .7f), a)
            canvas.drawPath(path, p)
            val t = ((System.nanoTime() - calStart) / 2.4e9 % 1.0) * 2 * Math.PI
            val d = 1 + sin(t) * sin(t)
            p.style = Paint.Style.FILL; p.color = withAlpha(theme.accent, a)
            canvas.drawCircle(cx + (w * cos(t) / d).toFloat(), my + (h * 1.6f * sin(t) * cos(t) / d).toFloat(), 13f * s, p)
        } else {
            // A tick in the accent.
            p.style = Paint.Style.STROKE; p.strokeWidth = 10f * s; p.color = withAlpha(theme.accent, a)
            val my = y0 + 215f * s
            canvas.drawPath(Path().apply { moveTo(cx - 40f * s, my); lineTo(cx - 10f * s, my + 30f * s); lineTo(cx + 46f * s, my - 30f * s) }, p)
        }
        // The accuracy: four bars, filled up to where it is.
        for (i in 0..3) {
            val filled = i <= accuracy && accuracy >= 0
            p.style = Paint.Style.FILL
            p.color = withAlpha(if (filled) (if (accuracy >= 3) theme.accent else theme.ink) else blend(theme.ink, theme.background, .8f), a)
            val bx = cx - 63f * s + i * 42f * s
            canvas.drawRoundRect(bx - 16f * s, y0 + 318f * s, bx + 16f * s, y0 + 326f * s, 4f * s, 4f * s, p)
        }
        text.textSize = 20f * s; text.color = withAlpha(grey, a)
        if (failed()) {
            canvas.drawText("Move away from metal and magnets", cx, y0 + 296f * s, text)
            pill(canvas, cx, y0 + 385f * s, 190f * s, 56f * s, outline = false, alpha = a)
            text.textSize = 23f * s; text.isFakeBoldText = true; text.color = withAlpha(theme.background, a)
            canvas.drawText("Try again", cx, y0 + 393f * s, text); text.isFakeBoldText = false
        } else canvas.drawText("Accuracy: ${accuracyName()}", cx, y0 + 296f * s, text)
        if (!done && !failed()) { text.textSize = 18f * s; canvas.drawText("Swipe down to stop", cx, y0 + 380f * s, text) }
        text.typeface = anta
    }

    private fun pill(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, outline: Boolean, alpha: Float) {
        p.style = if (outline) Paint.Style.STROKE else Paint.Style.FILL; p.strokeWidth = 2.5f * S
        p.color = withAlpha(theme.accent, alpha)
        canvas.drawRoundRect(x - w / 2, y - h / 2, x + w / 2, y + h / 2, h / 2, h / 2, p)
    }

    private fun withAlpha(c: Int, a: Float) = Color.argb((Color.alpha(c) * a).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private companion object {
        const val PANEL_H = 200f                      // the drawer's height, in the 450-unit box
        const val CAL_LIMIT_NS = 30_000_000_000L      // the guide says "still not calibrated" after 30 s
    }

    private fun cardinal(h: Float) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((h + 22.5f) % 360f) / 45f).toInt() % 8]

    private fun blend(a: Int, b: Int, f: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt())
}
