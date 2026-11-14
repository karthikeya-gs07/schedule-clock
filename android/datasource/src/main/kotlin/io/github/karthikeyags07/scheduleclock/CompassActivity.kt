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
 */
class CompassActivity : Activity(), SensorEventListener {
    private lateinit var dial: CompassView
    private lateinit var sensors: SensorManager
    private var sensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dial = CompassView(this, Themes.of(FacePush.colourway(this) ?: PhoneSettings.colourway(this)))
        setContentView(dial)
        sensors = getSystemService(SensorManager::class.java)
        sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) dial.noSensor = true
    }

    override fun onResume() {
        super.onResume()
        sensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        sensors.unregisterListener(this)
        super.onPause()
    }

    private val rotation = FloatArray(9)
    private val angles = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, angles)
        val heading = (Math.toDegrees(angles[0].toDouble()).toFloat() + 360f) % 360f   // 0 = the watch's 12 faces north
        dial.update(heading)
    }

    override fun onAccuracyChanged(s: Sensor, accuracy: Int) {
        dial.needsCalibration = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        dial.invalidate()
    }
}

private class CompassView(context: Context, private val theme: Theme) : View(context) {
    var noSensor = false
    var needsCalibration = false
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

    private val grey = Color.parseColor("#8e8e93")
    private val track = blend(theme.background, Color.WHITE, .12f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val anta = context.resources.getFont(R.font.anta_regular)   // the face's clock and number font
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = anta }
    private val plain = Typeface.create("sans-serif", Typeface.NORMAL)

    override fun onDraw(canvas: Canvas) {
        step()
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
            val r0 = r - 12f * s; val r1 = r - (12f + len) * s
            p.strokeWidth = (if (d == 0) 5f else if (major) 2.5f else if (mid) 1.8f else 1.2f) * s
            val base = if (major) Color.WHITE else if (mid) blend(Color.WHITE, theme.background, .35f) else blend(Color.WHITE, theme.background, .55f)
            p.color = if (d == 0) theme.accent else if (off < 45f) blend(theme.accent, base, off / 45f) else base
            canvas.drawLine(cx + r0 * sin(a).toFloat(), cy - r0 * cos(a).toFloat(), cx + r1 * sin(a).toFloat(), cy - r1 * cos(a).toFloat(), p)
            if (major) {
                val rl = r - 60f * s   // well inside the longest spokes, so wide numbers (240, 210…) stay clear
                text.textSize = 21f * s; text.color = if (d == 0) theme.accent else grey
                val label = when (d) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$d" }   // the four directions by letter
                val letter = d % 90 == 0
                if (letter) { text.textSize = 30f * s; text.isFakeBoldText = true }   // Anta has one weight: bold is drawn heavier
                canvas.drawText(label, cx + rl * sin(a).toFloat(), cy - rl * cos(a).toFloat() + (if (letter) 10.5f else 7.5f) * s, text)
                text.isFakeBoldText = false
            }
        }
        // A faint inner ring, with room between it and the degree numbers.
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * s; p.color = blend(theme.background, Color.WHITE, .1f)
        canvas.drawCircle(cx, cy, r - 100f * s, p)
        // The arrow, fixed at the top on the ring, pointing in, with slightly rounded corners.
        p.style = Paint.Style.FILL; p.color = theme.accent; p.pathEffect = android.graphics.CornerPathEffect(4f * s)
        val tip = cy - r + 30f * s; val base = cy - r - 2f * s
        canvas.drawPath(Path().apply { moveTo(cx, tip); lineTo(cx - 12f * s, base); lineTo(cx + 12f * s, base); close() }, p)
        p.pathEffect = null
        // Middle: the heading, in the arrow's colour and the face's font.
        text.color = theme.accent; text.textSize = 84f * s
        canvas.drawText(if (shown.isNaN()) "—" else "${shown.roundToInt() % 360}°", cx, cy + 20f * s, text)
        // Under it, the direction: N, NE, E, SE, S, SW, W or NW.
        if (!shown.isNaN()) {
            text.color = Color.WHITE; text.textSize = 30f * s
            canvas.drawText(cardinal(shown), cx, cy + 64f * s, text)
        }
        if (noSensor || needsCalibration) {
            text.typeface = plain; text.color = grey; text.textSize = 22f * s
            canvas.drawText(if (noSensor) "No compass on this watch" else "Move your watch in a figure 8", cx, cy + 96f * s, text)
            text.typeface = anta
        }
    }

    private fun cardinal(h: Float) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((h + 22.5f) % 360f) / 45f).toInt() % 8]

    private fun blend(a: Int, b: Int, f: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt())
}
