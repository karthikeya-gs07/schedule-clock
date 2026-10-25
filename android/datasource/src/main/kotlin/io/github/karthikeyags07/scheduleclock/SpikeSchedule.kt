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
import java.util.Calendar

internal const val TAG = "ScheduleClockSpike"

/**
 * Phase 1 spikes: fake meetings (stand-in for the calendar) run through the real rules engine
 * ([ScheduleRules]) at the current time, plus the selection state and the data for each slot.
 *
 * Packed number fields (WFF can't measure text or pass extra fields, so widths and flags ride in
 * RANGED_VALUE MAX; VALUE stays 0/1 for "selected"):
 * - status:  MAX = 1 + titleWidth + 10000 × flags, flags = 1 arrow (C12) + 2 lock (C13) + 4 clash (C7)
 * - details: MAX = 1 + lineWidth + 10000 × kind, kind 0 place · 1 clash · 2 tomorrow · 3 private
 * - selected arc: MIN/MAX = angles + 1000 (later lane) + 2000 (private)
 */
object SpikeSchedule {

    // The prototype's busy day, plus a late meeting that lands on the later lane during the day.
    private val meetings = listOf(
        Meeting(9 * 60, 9 * 60 + 30, "Standup", "Google Meet"),
        Meeting(10 * 60, 11 * 60 + 30, "Roadmap planning", "Atlas room"),
        Meeting(12 * 60 + 30, 13 * 60, "Lunch with Sam", "Café Blue", private = true),
        Meeting(13 * 60, 14 * 60, "Design review", "Room 4"),
        Meeting(13 * 60, 14 * 60, "Vendor call", "Zoom"),                 // full double booking
        Meeting(15 * 60, 15 * 60 + 45, "Candidate interview", "Zoom"),
        Meeting(15 * 60 + 30, 16 * 60, "Recruiter sync", "Room 2"),       // partial overlap
        Meeting(16 * 60, 17 * 60, "Weekly sync", "Google Meet"),
        Meeting(19 * 60, 20 * 60, "Client call", "Zoom"),
        Meeting(23 * 60, 23 * 60 + 45, "Late sync", "Zoom"),             // later lane until 11:45 AM
    )

    private const val SWITCH_FADE_MS = 400L  // old arc fades out in place before the new one appears (M2)
    private const val SETTLE_MS = 900L       // M3

    private fun prefs(context: Context) = context.getSharedPreferences("spike", Context.MODE_PRIVATE)

    /**
     * Minutes since midnight, shifted by a debug offset (`app://<package>/time?offset=-720`). On the
     * 12 h dial a ±12 h shift keeps the face's clock, dot and trail in the same place, so the spike can
     * be tested in daytime when the emulator's clock says evening.
     */
    fun now(context: Context): Int {
        val real = Calendar.getInstance().run { get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE) }
        return ((real + prefs(context).getInt("offsetMin", 0)) % 1440 + 1440) % 1440
    }

    fun setOffset(context: Context, minutes: Int) {
        prefs(context).edit().putInt("offsetMin", minutes).commit()
        requestUpdate(context.applicationContext)
    }

    private fun placed(context: Context, now: Int = now(context)) = ScheduleRules.place(meetings, now)

    /** The selected meeting's index in [list], or -1. A selection that is no longer shown is cleared (I10). */
    private fun selectedIndex(context: Context, list: List<Placed>): Int {
        val key = prefs(context).getString("selected", null) ?: return -1
        return list.indexOfFirst { it.meeting.key == key }
    }

    /** I2–I6: tapping a dial hour cycles its meetings (main ring first), then back to the default. */
    fun tapHour(context: Context, hour: Int) {
        val list = placed(context)
        val hits = ScheduleRules.meetingsInHour(list, hour)
        val pos = hits.indexOf(selectedIndex(context, list))
        val next = when {
            hits.isEmpty() -> null
            pos < 0 -> list[hits[0]].meeting.key
            pos + 1 < hits.size -> list[hits[pos + 1]].meeting.key
            else -> null
        }
        select(context, next)
    }

    /** Switching between two meetings fades the old arc out where it is, then shows the new one (M2). */
    private fun select(context: Context, next: String?) {
        val app = context.applicationContext
        val prev = prefs(app).getString("selected", null)
        prefs(app).edit().putString("selected", next).commit()
        if (prev != null && next != null && prev != next) {
            setArc(app, prev, shown = false)
            requestUpdate(app)
            Handler(Looper.getMainLooper()).postDelayed({
                setArc(app, next, shown = true)
                requestUpdate(app, SelectedDataSourceService::class.java)
            }, SWITCH_FADE_MS)
        } else {
            setArc(app, next ?: prefs(app).getString("arcKey", null), shown = next != null)
            requestUpdate(app)
        }
        Log.i(TAG, "selected=$next")
    }

    private fun setArc(context: Context, key: String?, shown: Boolean) {
        prefs(context).edit().putString("arcKey", key).putBoolean("arcShown", shown).commit()
    }

    // ---- Slot data -------------------------------------------------------------------------

    /** Slot 1: the whole ring as one image (spike 2). */
    fun ringImage(context: Context): SmallImageComplicationData {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val now = now(context)
        val list = placed(context, now)
        val selected = selectedIndex(context, list).takeIf { it >= 0 }?.let { list[it].meeting.key }
        val png = RingRenderer.renderPng(list, now, selected, hiddenKey = prefs(context).getString("settling", null))
        Log.i(TAG, "ring image: ${png.size / 1024} KB PNG, drawn+encoded in ${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000} ms")
        val image = SmallImage.Builder(Icon.createWithData(png, 0, png.size), SmallImageType.PHOTO).build()
        return SmallImageComplicationData.Builder(image, text("Today's meetings")).build()
    }

    /** Slot 2: the selected arc (R9). VALUE = MAX shown, MIN hidden; hidden keeps its last position. */
    fun selectedArc(context: Context): RangedValueComplicationData {
        val list = placed(context)
        val p = list.firstOrNull { it.meeting.key == prefs(context).getString("arcKey", null) }
            ?: return ranged(0f, 0f, 1f, "", "")
        val shown = prefs(context).getBoolean("arcShown", false) && selectedIndex(context, list) >= 0
        val offset = (if (p.lane == Lane.LATER) 1000f else 0f) + (if (p.meeting.private) 2000f else 0f)
        val (start, end) = degrees(p)
        return ranged(if (shown) end + offset else start + offset, start + offset, end + offset, p.meeting.title, "")
    }

    /**
     * Slot 4: VALUE = selected (0/1) drives every M1 Transform. TEXT = title (or "Private", C13),
     * TITLE = time range (C4, C8). MAX carries the measured title width and the icon flags.
     */
    fun status(context: Context): RangedValueComplicationData {
        val now = now(context)
        val list = placed(context, now)
        val selected = selectedIndex(context, list)
        val i = if (selected >= 0) selected else ScheduleRules.defaultIndex(list, now)
        if (i < 0) return ranged(0f, 0f, 1f, "", "")
        val p = list[i]
        val arrow = ScheduleRules.showsArrow(list, i, now)
        val lock = p.meeting.private
        val clash = ScheduleRules.clashesFor(list, p).isNotEmpty()
        val iconSpace = (if (arrow) 30 else 0) + (if (lock) 25 else 0)
        val (title, width) = TextFit.title(if (lock) "Private" else p.meeting.title, 260 - iconSpace)
        val (range, _) = TextFit.line(ScheduleRules.formatRange(p.meeting), 255)
        val flags = (if (arrow) 1 else 0) + (if (lock) 2 else 0) + (if (clash) 4 else 0)
        return ranged(if (selected >= 0) 1f else 0f, 0f, 1f + width + 10000f * flags, title, range)
    }

    /**
     * Slot 3: the line under the time range. TEXT = place, or the clashing meeting (C7), or
     * "Tomorrow · place" (C6), or "Details hidden" (C13). MAX carries its width and kind.
     */
    fun detailsLine(context: Context): RangedValueComplicationData {
        val now = now(context)
        val list = placed(context, now)
        val selected = selectedIndex(context, list)
        val i = if (selected >= 0) selected else ScheduleRules.defaultIndex(list, now)
        if (i < 0) return ranged(0f, 0f, 1f, "", "")
        val p = list[i]
        val clashes = ScheduleRules.clashesFor(list, p)
        val (kind, raw) = when {
            clashes.isNotEmpty() -> 1 to (clashes[0].meeting.let { if (it.private) "Private" else it.title } +
                if (clashes.size > 1) " +${clashes.size - 1}" else "")
            p.meeting.private -> 3 to "Details hidden"
            p.meeting.startMin >= 24 * 60 -> 2 to "Tomorrow · ${p.meeting.where}"
            else -> 0 to p.meeting.where
        }
        val (line, width) = TextFit.line(raw, if (kind == 1) 190 else 220)
        return ranged(if (selected >= 0) 1f else 0f, 0f, 1f + width + 10000f * kind, line, "")
    }

    /** Slot 5: the settling arc (M3). VALUE runs MIN (lane) → MAX (ring); no data otherwise. */
    fun promoteArc(context: Context): ComplicationData {
        val key = prefs(context).getString("settling", null) ?: return NoDataComplicationData()
        val p = placed(context).firstOrNull { it.meeting.key == key } ?: return NoDataComplicationData()
        val (start, end) = degrees(p)
        return ranged(if (prefs(context).getBoolean("settleMoving", false)) end else start, start, end, p.meeting.title, "")
    }

    /**
     * Demo of the lane → ring settle (spike 3) for the first later-lane meeting: the image drops it,
     * the settle arc carries it out to the ring, then it returns to its rule-based place.
     */
    fun runPromotion(context: Context) {
        val app = context.applicationContext
        val key = placed(app).firstOrNull { it.lane == Lane.LATER }?.meeting?.key ?: return
        val main = Handler(Looper.getMainLooper())
        prefs(app).edit().putString("settling", key).putBoolean("settleMoving", false).commit()
        requestUpdate(app, RingImageDataSourceService::class.java, PromoteDataSourceService::class.java)
        main.postDelayed({
            prefs(app).edit().putBoolean("settleMoving", true).commit()
            requestUpdate(app, PromoteDataSourceService::class.java)
        }, 150)
        main.postDelayed({
            prefs(app).edit().remove("settling").commit()
            requestUpdate(app, RingImageDataSourceService::class.java, PromoteDataSourceService::class.java)
        }, 150 + SETTLE_MS)
    }

    private val allServices = listOf(
        RingImageDataSourceService::class.java,
        SelectedDataSourceService::class.java,
        StatusDataSourceService::class.java,
        DetailsDataSourceService::class.java,
        PromoteDataSourceService::class.java,
    )

    fun requestUpdate(context: Context, vararg services: Class<out ComplicationDataSourceService>) {
        for (service in services.ifEmpty { allServices.toTypedArray() }) {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
        }
    }

    /** Start and end of a placed meeting on the 12 h dial, in degrees clockwise from 12 o'clock. */
    private fun degrees(p: Placed): Pair<Float, Float> {
        val start = (p.cs % ScheduleRules.DIAL_MIN) / ScheduleRules.DIAL_MIN.toFloat() * 360f
        return start to start + (p.ce - p.cs) / ScheduleRules.DIAL_MIN.toFloat() * 360f
    }

    private fun ranged(value: Float, min: Float, max: Float, text: String, title: String) =
        RangedValueComplicationData.Builder(value, min, max, text(text.ifEmpty { "—" }))
            .setText(text(text))
            .apply { if (title.isNotEmpty()) setTitle(text(title)) }
            .build()

    private fun text(s: String) = PlainComplicationText.Builder(s).build()
}
