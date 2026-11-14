package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import java.time.LocalTime
import kotlin.math.min

/**
 * The meeting countdown (RULES A22), opened by tapping the 3 on the face (`app://<package>/countdown`) when Settings →
 * Tap 3 for meeting countdown is on. In a meeting: "Ends in" and the time left, with an accent ring round the edge
 * that empties as it runs out. Between meetings: "Starts in" and the time to the next one (today or tomorrow), the
 * ring emptying over the last hour before it. The ring only ever shrinks. The meeting's title (or "Private") and its time sit underneath.
 * Ticks every second; closes when left.
 */
class CountdownActivity : Activity() {
    private lateinit var view: CountdownView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = CountdownView(this, Themes.of(FacePush.colourway(this) ?: PhoneSettings.colourway(this)),
            CalendarSource.meetings(this).filter { !it.allDay }, PhoneSettings.clock24(this))
        setContentView(view)
    }

    override fun onStop() {
        super.onStop()
        finish()   // leaving by home closes it, so the next tap on the face opens what was tapped
    }
}

private class CountdownView(context: Context, private val theme: Theme, private val meetings: List<Meeting>, private val h24: Boolean) : View(context) {
    private val anta = context.resources.getFont(R.font.anta_regular)
    private val grey = Color.parseColor("#8e8e93")
    private val track = Color.rgb(
        (Color.red(theme.background) * .88f + 255 * .12f).toInt(), (Color.green(theme.background) * .88f + 255 * .12f).toInt(),
        (Color.blue(theme.background) * .88f + 255 * .12f).toInt())
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tick = object : Runnable { override fun run() { invalidate(); postDelayed(this, 1000) } }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height) / 450f
        val cx = width / 2f; val cy = height / 2f
        canvas.drawColor(theme.background)
        val t = LocalTime.now(); val nowSec = t.toSecondOfDay()
        val now = nowSec / 60
        val live = meetings.filter { it.startMin <= now && it.endMin > now }.minByOrNull { it.endMin }
        val next = meetings.filter { it.startMin > now }.minByOrNull { it.startMin }
        val m = live ?: next
        // The ring round the edge: the meeting's time left, or the last hour before the next one.
        val inset = 12f * s; val box = RectF(inset, inset, width - inset, height - inset)
        ring.strokeWidth = 12f * s; ring.color = track
        canvas.drawArc(box, 0f, 360f, false, ring)
        val (label, seconds, fraction) = when {
            live != null -> Triple("Ends in", live.endMin * 60 - nowSec, (live.endMin * 60 - nowSec).toFloat() / ((live.endMin - live.startMin) * 60))
            // Before a meeting the ring also runs down: full an hour out (or more), empty as it starts.
            next != null -> Triple("Starts in", next.startMin * 60 - nowSec, ((next.startMin * 60 - nowSec) / 3600f).coerceIn(0f, 1f))
            else -> Triple("", 0, 0f)
        }
        if (m != null && fraction > 0f) { ring.color = theme.accent; canvas.drawArc(box, -90f, 360f * fraction.coerceIn(0f, 1f), false, ring) }
        if (m == null) {
            text.typeface = Typeface.create("sans-serif", Typeface.NORMAL); text.color = grey; text.textSize = 26f * s
            canvas.drawText("No more meetings", cx, cy - 6f * s, text)
            canvas.drawText("today or tomorrow", cx, cy + 28f * s, text)
            return
        }
        text.typeface = Typeface.create("sans-serif", Typeface.NORMAL); text.color = grey; text.textSize = 26f * s
        canvas.drawText(label, cx, cy - 78f * s, text)
        // The time left: h:mm:ss over an hour, else mm:ss.
        val h = seconds / 3600; val mm = seconds % 3600 / 60; val ss = seconds % 60
        val big = if (h > 0) "$h:${"%02d".format(mm)}:${"%02d".format(ss)}" else "${"%02d".format(mm)}:${"%02d".format(ss)}"
        text.typeface = anta; text.color = theme.accent; text.textSize = (if (h > 0) 76f else 96f) * s
        canvas.drawText(big, cx, cy + 22f * s, text)
        // The meeting underneath: its title (one line) and time.
        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); text.color = Color.WHITE; text.textSize = 28f * s
        val title = TextUtils.ellipsize(if (m.private) "Private" else m.title.ifEmpty { "Busy" },
            android.text.TextPaint(text), 300f * s, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, cx, cy + 78f * s, text)
        text.typeface = Typeface.create("sans-serif", Typeface.NORMAL); text.color = grey; text.textSize = 22f * s
        val day = if (m.startMin >= 1440) "Tomorrow · " else ""
        canvas.drawText(day + ScheduleRules.formatRange(m, h24), cx, cy + 112f * s, text)
    }
}
