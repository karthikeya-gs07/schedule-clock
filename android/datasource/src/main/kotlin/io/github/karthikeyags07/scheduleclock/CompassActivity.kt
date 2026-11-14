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
    private var shown = Float.NaN   // the heading on screen, eased towards the sensor's

    fun update(heading: Float) {
        shown = if (shown.isNaN()) heading else {
            val d = ((heading - shown + 540f) % 360f) - 180f   // shortest way round
            (shown + d * .18f + 360f) % 360f
        }
        invalidate()
    }

    private val grey = Color.parseColor("#8e8e93")
    private val track = blend(theme.background, Color.WHITE, .12f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height) / 450f            // the face's 450-unit box
        val cx = width / 2f; val cy = height / 2f; val r = 219f * s
        canvas.drawColor(theme.background)
        p.style = Paint.Style.STROKE; p.strokeWidth = 12f * s; p.color = track
        canvas.drawCircle(cx, cy, r, p)
        val heading = if (shown.isNaN()) 0f else shown
        // The dial turns so that north (its 0° spoke) sits where north is; the arrow stays at the top.
        // Spokes every 2°, longer every 10°, longest every 30° with the degree beside it; 0° in the accent.
        for (d in 0 until 360 step 2) {
            val a = Math.toRadians((d - heading).toDouble())
            val major = d % 30 == 0; val mid = d % 10 == 0
            val len = if (major) 16f else if (mid) 10f else 5f
            val r0 = r - 12f * s; val r1 = r - (12f + len) * s
            p.strokeWidth = (if (d == 0) 5f else if (major) 2.5f else if (mid) 1.8f else 1.1f) * s
            p.color = if (d == 0) theme.accent else if (major) grey else if (mid) blend(grey, theme.background, .35f) else track
            canvas.drawLine(cx + r0 * sin(a).toFloat(), cy - r0 * cos(a).toFloat(), cx + r1 * sin(a).toFloat(), cy - r1 * cos(a).toFloat(), p)
            if (major) {
                val rl = r - 48f * s
                text.textSize = 20f * s; text.color = if (d == 0) theme.accent else grey
                canvas.drawText("$d", cx + rl * sin(a).toFloat(), cy - rl * cos(a).toFloat() + 7f * s, text)
            }
        }
        // The arrow, fixed at the top on the ring, pointing in; north is where the 0° spoke meets it.
        p.style = Paint.Style.FILL; p.color = theme.accent
        val tip = cy - r + 30f * s; val base = cy - r - 2f * s
        canvas.drawPath(Path().apply { moveTo(cx, tip); lineTo(cx - 12f * s, base); lineTo(cx + 12f * s, base); close() }, p)
        // Middle: the heading, in the arrow's colour.
        text.color = theme.accent; text.textSize = 80f * s
        canvas.drawText(if (shown.isNaN()) "—" else "${shown.roundToInt() % 360}°", cx, cy + 28f * s, text)
        if (noSensor || needsCalibration) {
            text.color = grey; text.textSize = 22f * s
            canvas.drawText(if (noSensor) "No compass on this watch" else "Move your watch in a figure 8", cx, cy + 80f * s, text)
        }
    }

    private fun cardinal(h: Float) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((h + 22.5f) % 360f) / 45f).toInt() % 8]

    private fun blend(a: Int, b: Int, f: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt())
}
