package io.github.karthikeyags07.scheduleclock

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.WeightedElementsComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

internal const val TAG = "ScheduleClockSpike"

/** Phase 1 spike: fixed fake meetings and the selection, standing in for the calendar and rules engine. */
object SpikeSchedule {

    data class Meeting(val startMin: Int, val endMin: Int, val title: String)

    // The prototype's original example: 1–2 PM and 4–5 PM.
    val meetings = listOf(
        Meeting(13 * 60, 14 * 60, "Design review"),
        Meeting(16 * 60, 17 * 60, "Weekly sync"),
    )

    private const val DIAL_MIN = 12 * 60
    private val AMBER = Color.parseColor("#ffb547")
    private val AMBER_DIM = Color.parseColor("#5a4320")
    private val AMBER_SELECTED = Color.parseColor("#ffd27f")
    // Free time. Fully transparent (0x00000000) reaches the face as "#0", which the WFF runtime
    // rejects ("Unknown color") and then draws the whole ring white. Alpha 0x10 keeps 8 hex digits.
    private const val FREE = 0x10000000

    fun selected(context: Context): Int =
        context.getSharedPreferences("spike", Context.MODE_PRIVATE).getInt("selected", -1)

    fun setSelected(context: Context, index: Int) {
        context.getSharedPreferences("spike", Context.MODE_PRIVATE).edit().putInt("selected", index).commit()
    }

    /** Index of the meeting in the tapped dial hour (0 = 12→1 o'clock), or -1. */
    fun meetingInHour(hour: Int): Int =
        meetings.indexOfFirst { (it.startMin % DIAL_MIN) / 60 == hour }

    /** Meetings as segments round the 12 h dial from 12 o'clock; free time is transparent. */
    fun ring(selected: Int): WeightedElementsComplicationData {
        val elements = mutableListOf<WeightedElementsComplicationData.Element>()
        var cursor = 0
        meetings.forEachIndexed { i, m ->
            val a = m.startMin % DIAL_MIN
            val b = a + (m.endMin - m.startMin)
            if (a > cursor) elements += WeightedElementsComplicationData.Element((a - cursor).toFloat(), FREE)
            val color = when {
                selected < 0 -> AMBER
                i == selected -> AMBER_SELECTED
                else -> AMBER_DIM
            }
            elements += WeightedElementsComplicationData.Element((b - a).toFloat(), color)
            cursor = b
        }
        if (cursor < DIAL_MIN) elements += WeightedElementsComplicationData.Element((DIAL_MIN - cursor).toFloat(), FREE)
        Log.i(TAG, "ring: ${elements.size} elements (max ${WeightedElementsComplicationData.getMaxElements()}), selected=$selected")
        // The library requires a text, title or image even though the face only draws the segments.
        return WeightedElementsComplicationData.Builder(elements, text("Today's meetings"))
            .setText(text("${meetings.size} meetings"))
            .build()
    }

    /** VALUE = selected (0/1) drives the face's Transforms; TEXT = the shown meeting's title. */
    fun status(selected: Int): RangedValueComplicationData {
        val shown = meetings[if (selected >= 0) selected else 0]
        return RangedValueComplicationData.Builder(
            value = if (selected >= 0) 1f else 0f,
            min = 0f,
            max = 1f,
            contentDescription = text(shown.title),
        ).setText(text(shown.title)).build()
    }

    fun requestUpdate(context: Context) {
        for (service in listOf(RingDataSourceService::class.java, StatusDataSourceService::class.java)) {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
        }
    }

    private fun text(s: String) = PlainComplicationText.Builder(s).build()
}
