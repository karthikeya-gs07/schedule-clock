package io.github.karthikeyags07.scheduleclock

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.util.Log
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

internal const val TAG = "ScheduleClockSpike"

/** Phase 1 spikes: fixed fake meetings and the selection, standing in for the calendar and rules engine. */
object SpikeSchedule {

    data class Meeting(
        val startMin: Int,
        val endMin: Int,
        val title: String,
        val private: Boolean = false,
        val later: Boolean = false,
    )

    // Spike 2: the prototype's busy day, more than the 7-segment weighted-elements limit allowed (spike 1, risk 9).
    val meetings = listOf(
        Meeting(9 * 60, 9 * 60 + 30, "Standup"),
        Meeting(10 * 60, 11 * 60 + 30, "Roadmap planning"),
        Meeting(12 * 60 + 30, 13 * 60, "Lunch with Sam", private = true),
        Meeting(13 * 60, 14 * 60, "Design review"),
        Meeting(13 * 60, 14 * 60, "Vendor call"),                 // full double booking
        Meeting(15 * 60, 15 * 60 + 45, "Candidate interview"),
        Meeting(15 * 60 + 30, 16 * 60, "Recruiter sync"),         // partial overlap
        Meeting(16 * 60, 17 * 60, "Weekly sync"),
        Meeting(19 * 60, 20 * 60, "Client call", later = true),   // later lane
    )

    private const val DIAL_MIN = 12 * 60

    fun selected(context: Context): Int =
        context.getSharedPreferences("spike", Context.MODE_PRIVATE).getInt("selected", -1)

    fun setSelected(context: Context, index: Int) {
        context.getSharedPreferences("spike", Context.MODE_PRIVATE).edit().putInt("selected", index).commit()
    }

    /** Index of the first main-ring meeting in the tapped dial hour (0 = 12→1 o'clock), or -1. */
    fun meetingInHour(hour: Int): Int =
        meetings.indexOfFirst { !it.later && (it.startMin % DIAL_MIN) / 60 == hour }

    /**
     * The whole ring as one image (spike 2). Sent as SMALL_IMAGE with the PHOTO style: WearServices
     * rejects PHOTO_IMAGE from our provider ("Unexpected complication data type PHOTO_IMAGE").
     */
    fun ringImage(selected: Int): SmallImageComplicationData {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val png = RingRenderer.renderPng(meetings, selected)
        Log.i(TAG, "ring image: ${png.size / 1024} KB PNG, drawn+encoded in ${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000} ms, selected=$selected")
        val image = SmallImage.Builder(Icon.createWithData(png, 0, png.size), SmallImageType.PHOTO).build()
        return SmallImageComplicationData.Builder(image, text("Today's meetings")).build()
    }

    /** VALUE = selected (0/1) drives the face's Transforms; TEXT = the shown meeting's title. */
    fun status(selected: Int): RangedValueComplicationData {
        val shown = meetings[if (selected >= 0) selected else 0]
        val title = if (shown.private) "Private" else shown.title
        return RangedValueComplicationData.Builder(
            value = if (selected >= 0) 1f else 0f,
            min = 0f,
            max = 1f,
            contentDescription = text(title),
        ).setText(text(title)).build()
    }

    fun requestUpdate(context: Context) {
        for (service in listOf(RingImageDataSourceService::class.java, StatusDataSourceService::class.java)) {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
        }
    }

    private fun text(s: String) = PlainComplicationText.Builder(s).build()
}
