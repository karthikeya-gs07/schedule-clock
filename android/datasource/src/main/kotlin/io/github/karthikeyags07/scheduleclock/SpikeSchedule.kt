package io.github.karthikeyags07.scheduleclock

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
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

    // The prototype's busy day, more than the 7-segment weighted-elements limit allowed (spike 1, risk 9).
    val meetings = listOf(
        Meeting(9 * 60, 9 * 60 + 30, "Standup"),
        Meeting(10 * 60, 11 * 60 + 30, "Roadmap planning"),
        Meeting(12 * 60 + 30, 13 * 60, "Lunch with Sam", private = true),
        Meeting(13 * 60, 14 * 60, "Design review"),
        Meeting(13 * 60, 14 * 60, "Vendor call"),                 // full double booking
        Meeting(15 * 60, 15 * 60 + 45, "Candidate interview"),
        Meeting(15 * 60 + 30, 16 * 60, "Recruiter sync"),         // partial overlap
        Meeting(16 * 60, 17 * 60, "Weekly sync"),
        Meeting(19 * 60, 20 * 60, "Client call", later = true),   // later lane; spike 3 promotes it (M3)
    )
    private val promotable = meetings.first { it.later }

    private const val DIAL_MIN = 12 * 60

    // Lane → ring settle (RULES W8, M3), spike 3.
    const val PROMOTE_IDLE = 0     // on the lane
    const val PROMOTE_START = 1    // removed from the image; promote arc drawn on the lane
    const val PROMOTE_MOVING = 2   // promote arc animating out to the ring
    const val PROMOTE_DONE = 3     // on the main ring in the image; promote arc gone
    private const val SETTLE_MS = 900L

    private fun prefs(context: Context) = context.getSharedPreferences("spike", Context.MODE_PRIVATE)

    fun selected(context: Context): Int = prefs(context).getInt("selected", -1)

    fun setSelected(context: Context, index: Int) {
        prefs(context).edit().putInt("selected", index).apply {
            if (index >= 0) putInt("lastSelected", index)
        }.commit()
    }

    fun promotePhase(context: Context): Int = prefs(context).getInt("promote", PROMOTE_IDLE)

    private fun setPromotePhase(context: Context, phase: Int) {
        prefs(context).edit().putInt("promote", phase).commit()
    }

    /** Index of the first main-ring meeting in the tapped dial hour (0 = 12→1 o'clock), or -1. */
    fun meetingInHour(hour: Int): Int =
        meetings.indexOfFirst { !it.later && (it.startMin % DIAL_MIN) / 60 == hour }

    /** Meetings as the ring image should show them for the current promotion phase. */
    private fun ringMeetings(context: Context): List<Meeting> = when (promotePhase(context)) {
        PROMOTE_START, PROMOTE_MOVING -> meetings - promotable
        PROMOTE_DONE -> meetings.map { if (it == promotable) it.copy(later = false) else it }
        else -> meetings
    }

    /** Slot 1: the whole ring as one image (spike 2). SMALL_IMAGE with the PHOTO style, since PHOTO_IMAGE is rejected. */
    fun ringImage(context: Context): SmallImageComplicationData {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val selected = selected(context).takeIf { it >= 0 }?.let { meetings[it] }
        val png = RingRenderer.renderPng(ringMeetings(context), selected)
        Log.i(TAG, "ring image: ${png.size / 1024} KB PNG, drawn+encoded in ${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000} ms")
        val image = SmallImage.Builder(Icon.createWithData(png, 0, png.size), SmallImageType.PHOTO).build()
        return SmallImageComplicationData.Builder(image, text("Today's meetings")).build()
    }

    /**
     * Slot 2: the selected arc (R9). MIN/MAX = its start/end angle on the dial; VALUE = MAX when
     * shown, MIN when hidden. Hidden keeps the last position so the face fades it out in place.
     */
    fun selectedArc(context: Context): RangedValueComplicationData {
        val index = selected(context)
        val m = meetings[if (index >= 0) index else prefs(context).getInt("lastSelected", 0)]
        val (start, end) = degrees(m)
        return ranged(if (index >= 0) end else start, start, end, m.title)
    }

    /** Slot 4: VALUE = selected (0/1) drives the face's Transforms; TEXT = the shown meeting's title. */
    fun status(context: Context): RangedValueComplicationData {
        val index = selected(context)
        val shown = meetings[if (index >= 0) index else 0]
        return ranged(if (index >= 0) 1f else 0f, 0f, 1f, if (shown.private) "Private" else shown.title)
    }

    /** Slot 5: the settling arc. VALUE runs MIN (lane) → MAX (ring); no data outside a promotion. */
    fun promoteArc(context: Context): ComplicationData {
        val (start, end) = degrees(promotable)
        return when (promotePhase(context)) {
            PROMOTE_START -> ranged(start, start, end, promotable.title)
            PROMOTE_MOVING -> ranged(end, start, end, promotable.title)
            else -> NoDataComplicationData()
        }
    }

    /**
     * Plays the lane → ring settle (spike 3): hide it from the image and show the promote arc on
     * the lane, then move it out, then hand over to the image. Tapping again resets to the lane.
     */
    fun runPromotion(context: Context) {
        val app = context.applicationContext
        if (promotePhase(app) != PROMOTE_IDLE) {
            setPromotePhase(app, PROMOTE_IDLE)
            requestUpdate(app)
            return
        }
        val main = Handler(Looper.getMainLooper())
        setPromotePhase(app, PROMOTE_START)
        requestUpdate(app, RingImageDataSourceService::class.java, PromoteDataSourceService::class.java)
        main.postDelayed({
            setPromotePhase(app, PROMOTE_MOVING)
            requestUpdate(app, PromoteDataSourceService::class.java)
        }, 150)
        main.postDelayed({
            setPromotePhase(app, PROMOTE_DONE)
            requestUpdate(app, RingImageDataSourceService::class.java, PromoteDataSourceService::class.java)
        }, 150 + SETTLE_MS)
    }

    private val allServices = listOf(
        RingImageDataSourceService::class.java,
        SelectedDataSourceService::class.java,
        StatusDataSourceService::class.java,
        PromoteDataSourceService::class.java,
    )

    fun requestUpdate(context: Context, vararg services: Class<out ComplicationDataSourceService>) {
        for (service in services.ifEmpty { allServices.toTypedArray() }) {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
        }
    }

    /** Start and end of a meeting on the 12 h dial, in degrees clockwise from 12 o'clock. */
    private fun degrees(m: Meeting): Pair<Float, Float> {
        val start = (m.startMin % DIAL_MIN) / DIAL_MIN.toFloat() * 360f
        return start to start + (m.endMin - m.startMin) / DIAL_MIN.toFloat() * 360f
    }

    private fun ranged(value: Float, min: Float, max: Float, title: String) =
        RangedValueComplicationData.Builder(value, min, max, text(title)).setText(text(title)).build()

    private fun text(s: String) = PlainComplicationText.Builder(s).build()
}
