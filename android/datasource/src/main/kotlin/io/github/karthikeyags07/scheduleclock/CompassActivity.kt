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
 * The compass (RULES A19), opened by tapping the 12 on the face (`app://<package>/compass`). It looks like the face:
 * the colourway's dial, track and ticks, with an arrow on the ring pointing north. When north lines up with 12, the
 * thick 12 o'clock line lights up in the colourway's accent. The sensor runs only while this screen is open.
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
        // Track
        p.style = Paint.Style.STROKE; p.strokeWidth = 12f * s; p.color = track
        canvas.drawCircle(cx, cy, r, p)
        // North, in the watch's frame: the arrow sits where north is on the dial.
        val north = if (shown.isNaN()) 0f else (360f - shown) % 360f
        val aligned = !shown.isNaN() && (north < 4f || north > 356f)
        // Ticks every 15 minutes, as on the face; the 12 o'clock one thicker, lit when north lines up with it.
        for (q in 0 until 48) {
            val hour = q % 4 == 0
            val len = if (hour) 10f else if (q % 2 == 0) 6f else 3f
            val a = Math.toRadians(q * 7.5)
            val r0 = r - 14f * s; val r1 = r - (14f + len) * s
            p.strokeWidth = (if (q == 0) 4.5f else if (hour) 2.5f else 1.5f) * s
            p.color = if (q == 0 && aligned) theme.accent else if (hour) grey else track
            canvas.drawLine(cx + r0 * sin(a).toFloat(), cy - r0 * cos(a).toFloat(), cx + r1 * sin(a).toFloat(), cy - r1 * cos(a).toFloat(), p)
        }
        // The north arrow on the ring: a filled triangle pointing outwards, in the accent.
        if (!shown.isNaN()) {
            canvas.save(); canvas.rotate(north, cx, cy)
            p.style = Paint.Style.FILL; p.color = theme.accent
            val tip = cy - r - 2f * s; val base = cy - r + 26f * s
            canvas.drawPath(Path().apply { moveTo(cx, tip); lineTo(cx - 11f * s, base); lineTo(cx + 11f * s, base); close() }, p)
            canvas.restore()
        }
        // Middle: the heading in degrees and its direction.
        text.color = Color.WHITE; text.textSize = 76f * s
        val deg = if (shown.isNaN()) "—" else "${shown.roundToInt() % 360}°"
        canvas.drawText(deg, cx, cy + 20f * s, text)
        text.color = if (aligned) theme.accent else grey; text.textSize = 28f * s
        canvas.drawText(if (shown.isNaN()) "" else cardinal(shown), cx, cy + 66f * s, text)
        text.color = grey; text.textSize = 22f * s
        val hint = when {
            noSensor -> "No compass on this watch"
            needsCalibration -> "Move your watch in a figure 8"
            aligned -> "12 is facing north"
            else -> "Turn until the arrow meets 12"
        }
        canvas.drawText(hint, cx, cy - 92f * s, text)
    }

    private fun cardinal(h: Float) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((h + 22.5f) % 360f) / 45f).toInt() % 8]

    private fun blend(a: Int, b: Int, f: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt())
}
