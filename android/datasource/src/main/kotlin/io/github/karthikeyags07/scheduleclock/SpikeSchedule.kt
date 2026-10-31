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
 *            + 8 title scrolls (C9a) + 16 title at 24 px (C9) + 32 no meetings (C3)
 * - details: MAX = 1 + lineWidth + 10000 × kind, kind 0 place · 1 clash · 2 tomorrow · 3 private
 */
object SpikeSchedule {

    // `app://<package>/day?name=example`: the prototype's busy day at fixed times, plus a late meeting that
    // lands on the later lane during the day and a night call (W5a).
    private val exampleDay = listOf(
        Meeting(30, 2 * 60, "Night call", "Zoom"),                        // before 6 AM: on the lane, not the ring (W5a)
        Meeting(9 * 60, 9 * 60 + 30, "Standup", "Google Meet"),
        Meeting(10 * 60, 11 * 60 + 30, "Roadmap planning", "Atlas room"),
        Meeting(12 * 60 + 30, 13 * 60, "Lunch with Sam", "Café Blue", private = true),
        Meeting(13 * 60, 14 * 60, "Design review", "Room 4"),
        Meeting(13 * 60, 14 * 60, "Vendor call", "Zoom"),                 // full double booking
        Meeting(15 * 60, 15 * 60 + 45, "Candidate interview", "Zoom"),
        Meeting(15 * 60 + 30, 16 * 60, "Recruiter sync", "Room 2"),       // partial overlap
        Meeting(16 * 60, 17 * 60, "Weekly sync", "Google Meet"),
        Meeting(17 * 60 + 30, 18 * 60 + 30, "Quarterly roadmap and budget alignment with leadership", "Atlas room"),  // long title (C9a)
        Meeting(19 * 60, 20 * 60, "Client call", "Zoom"),
        Meeting(23 * 60, 23 * 60 + 45, "Late sync", "Zoom"),             // later lane until 11:45 AM
    )

    /**
     * The default sample (`day?name=busy`): a busy day that rolls with the clock, so a real watch looks busy
     * at any hour. Minutes from the hour it was made (`anchor`): a live meeting, back-to-back meetings, a
     * private one, a full and a partial double booking (with a third and fourth joining the full one, R4a), a long title, and one past the 12 h window. Made
     * again at the current hour once they've all ended. Past midnight they become tomorrow's (W5, W5a).
     */
    private fun busyDay(anchor: Int) = listOf(
        Meeting(anchor - 30, anchor + 30, "Standup", "Google Meet"),           // live
        Meeting(anchor + 45, anchor + 105, "Roadmap planning", "Atlas room"),
        Meeting(anchor + 105, anchor + 135, "Lunch with Sam", "Café Blue", private = true),
        Meeting(anchor + 150, anchor + 210, "Design review", "Room 4"),
        Meeting(anchor + 150, anchor + 210, "Vendor call", "Zoom"),            // full double booking
        Meeting(anchor + 170, anchor + 210, "Budget check", "Teams"),          // a third joins (R4a: inner stripe splits)
        Meeting(anchor + 185, anchor + 200, "Quick sync", "Teams"),            // and a fourth (both split)
        Meeting(anchor + 240, anchor + 285, "Candidate interview", "Zoom"),
        Meeting(anchor + 270, anchor + 300, "Recruiter sync", "Room 2"),       // partial overlap
        Meeting(anchor + 300, anchor + 360, "Weekly sync", "Google Meet"),
        Meeting(anchor + 390, anchor + 480, "Quarterly roadmap and budget alignment with leadership", "Atlas room"),  // C9a
        Meeting(anchor + 510, anchor + 570, "Client call", "Zoom"),
        Meeting(anchor + 600, anchor + 660, "Product demo", "Room 1"),
        Meeting(anchor + 780, anchor + 840, "Late sync", "Zoom"),              // past the 12 h window: later lane
    )

    /** The day shown: the real calendar (default), or a sample (`day?name=busy|example`) for testing. */
    private fun meetings(context: Context, now: Int): List<Meeting> {
        when (prefs(context).getString("day", "calendar")) {
            "example" -> return exampleDay
            "busy" -> {}
            else -> return CalendarSource.meetings(context)
        }
        // The anchor is kept as a real time, so the day stays put across hours and midnight (and the debug
        // time offset moves the clock through it rather than moving it along).
        val made = prefs(context).getLong("busyAt", 0L)
        busyDay(minutesFromMidnight(made)).let { if (made > 0 && it.any { m -> m.endMin > now }) return it }
        val hour = Calendar.getInstance().apply { set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        prefs(context).edit().putLong("busyAt", hour.timeInMillis).commit()
        return busyDay(minutesFromMidnight(hour.timeInMillis))
    }

    /** Minutes from today's midnight to a real time (negative if it was yesterday). */
    private fun minutesFromMidnight(millis: Long): Int {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return Math.floorDiv(millis - midnight.timeInMillis, 60_000L).toInt()
    }

    /** Debug: switch the day ("calendar", "busy" or "example"); a busy day is made fresh at the current hour. */
    /** True when the face shows the real calendar (not a sample day). */
    fun usesCalendar(context: Context) = prefs(context).getString("day", "calendar") == "calendar"

    /** True while the face has no calendar at all: nothing from the phone app and no access on the watch. */
    fun needsCalendarSetup(context: Context) =
        prefs(context).getString("day", "calendar") == "calendar" &&
            !CalendarSource.hasPhoneData(context) && !CalendarSource.hasPermission(context)

    fun setDay(context: Context, name: String) {
        prefs(context).edit().putString("day", name).remove("busyAt").remove("selected").commit()
        requestUpdate(context.applicationContext)
        MeetingBuzz.schedule(context)
    }

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

    // A tap (or any change) asks all six slots at once, and the face asks them all again ~1 s later; each used to read
    // the calendar again. One read now serves the burst: kept 3 s, for the same minute, and dropped on every
    // requestUpdate (a change of data, selection or settings).
    private var cache: Triple<Int, Long, List<Placed>>? = null
    private const val CACHE_MS = 3_000L

    private fun placed(context: Context, now: Int = now(context)): List<Placed> {
        val t = SystemClock.elapsedRealtime()
        cache?.let { (min, at, list) -> if (min == now && t - at < CACHE_MS) return list }
        return ScheduleRules.place(meetings(context, now), now).also { cache = Triple(now, t, it) }
    }

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

    /** I8: a tap on the details goes back to the normal view; with nothing selected it does nothing. */
    fun release(context: Context) {
        if (prefs(context).getString("selected", null) != null) select(context, null)
    }

    /** R9: the ring image redraws with the new selection at full strength and every other meeting dimmed. */
    private fun select(context: Context, next: String?) {
        prefs(context).edit().putString("selected", next).commit()
        requestUpdate(context.applicationContext)
        Log.i(TAG, "selected=$next")
    }

    // ---- Slot data -------------------------------------------------------------------------

    private var lastRing: Pair<List<Any?>, ByteArray>? = null

    /** Slot 1: the whole ring as one image (spike 2). */
    fun ringImage(context: Context): SmallImageComplicationData {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val now = now(context)
        val list = placed(context, now)
        val selected = selectedIndex(context, list).takeIf { it >= 0 }?.let { list[it].meeting.key }
        val hidden = prefs(context).getString("settling", null)
        val theme = Themes.of(FacePush.colourway(context))
        // The same ring as last time (the face asking again after a tap): send the PNG already made.
        val key = listOf(list, now, selected, hidden, theme.id)
        val png = lastRing?.takeIf { it.first == key }?.second
            ?: RingRenderer.renderPng(list, now, selected, hiddenKey = hidden, theme = theme).also { lastRing = key to it }
        MeetingBuzz.schedule(context)   // the ring redraws every 5 min and on changes: keep the next buzz set
        Log.i(TAG, "ring image: ${png.size / 1024} KB PNG, drawn+encoded in ${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000} ms")
        val image = SmallImage.Builder(Icon.createWithData(png, 0, png.size), SmallImageType.PHOTO).build()
        return SmallImageComplicationData.Builder(image, text("Today's meetings")).build()
    }

    /**
     * Slot 4: VALUE = selected (0/1) drives every M1 Transform. TEXT = title (or "Private", C13),
     * TITLE = time range (C4, C8). MAX carries the measured title width and the icon flags.
     */
    fun status(context: Context): RangedValueComplicationData {
        val now = now(context)
        val list = placed(context, now)
        if (needsCalendarSetup(context)) {
            // No calendar yet: point to the phone app. A tap on the ring asks for the watch's own calendar
            // instead (watches whose calendar apps share their events, PRD §6.3f).
            val (title, width) = TextFit.title("Open Calen-Time", 260)
            return ranged(0f, 0f, 1f + width, title, "on your phone to sync")
        }
        val selected = selectedIndex(context, list)
        val i = if (selected >= 0) selected else ScheduleRules.defaultIndex(list, now)
        if (i < 0) return ranged(0f, 0f, 1f + 10000f * 32, "", "")   // C3: no meetings, flag 32 centers the clock
        val p = list[i]
        val arrow = ScheduleRules.showsArrow(list, i, now)
        val lock = p.meeting.private
        val clash = ScheduleRules.clashesFor(list, p).isNotEmpty()
        val iconSpace = (if (arrow) 30 else 0) + (if (lock) 25 else 0)
        // C9: a title too wide at 30 px is drawn at 24 px (flag 16). TEXT stays the shortened title with an
        // ellipsis: it is what ambient mode shows (A2a). When the full title still overflows at 24 px (C9a)
        // the face hides it and the scroll slots draw the full title instead.
        val shown = if (lock) "Private" else p.meeting.title
        val (big, bigWidth) = TextFit.title(shown, 260 - iconSpace)
        val small = big != shown
        val (title, fitted) = if (small) TextFit.titleSmall(shown, 260 - iconSpace) else big to bigWidth
        val scrolls = scrollOver(p, arrow) > 0
        val width = if (scrolls) 260 - iconSpace else fitted   // icons sit at the edge of the full window
        val (range, _) = TextFit.line(ScheduleRules.formatRange(p.meeting), 255)
        val flags = (if (arrow) 1 else 0) + (if (lock) 2 else 0) + (if (clash) 4 else 0) + (if (scrolls) 8 else 0) + (if (small) 16 else 0)
        return ranged(if (selected >= 0) 1f else 0f, 0f, 1f + width + 10000f * flags, title, range)
    }

    /** C9a: pixels by which the full title at 24 px overflows its window; 0 if it fits or is private. */
    private fun scrollOver(p: Placed, arrow: Boolean): Int {
        if (p.meeting.private) return 0
        return (TextFit.titleMinWidth(p.meeting.title) - (260 - (if (arrow) 30 else 0))).coerceAtLeast(0)
    }

    /**
     * Slots 4 and 6: the full title when it doesn't fit, scrolled by the face (C9a). Slot 4 is the window used
     * without the look-ahead arrow, slot 6 the narrower one with it. VALUE = selected (0/1) moves the title up
     * with the details (M1). MAX = 1 + overflow px + 10000 × scrolling (1); idle slots send 1.
     */
    fun titleScroll(context: Context, withArrow: Boolean): ComplicationData {
        val now = now(context)
        val list = placed(context, now)
        val selected = selectedIndex(context, list)
        val i = if (selected >= 0) selected else ScheduleRules.defaultIndex(list, now)
        if (i < 0) return NoDataComplicationData()
        val p = list[i]
        val arrow = ScheduleRules.showsArrow(list, i, now)
        val over = if (arrow == withArrow) scrollOver(p, arrow) else 0
        val value = if (selected >= 0) 1f else 0f
        // No data unless this slot is scrolling: its time-driven Transform keeps the face redrawing
        // ~10×/s while it has data (0.41 → 3.2–3.7 s CPU/min interactive, spike 5).
        return if (over > 0) ranged(value, 0f, 1f + over + 10000f, p.meeting.title, "") else NoDataComplicationData()
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
            clashes.isNotEmpty() -> 1 to (clashes[0].let { if (it.private) "Private" else it.title } +
                if (clashes.size > 1) " +${clashes.size - 1}" else "")
            p.meeting.private -> 3 to "Details hidden"
            p.meeting.startMin >= 24 * 60 -> 2 to if (p.meeting.where.isBlank()) "Tomorrow" else "Tomorrow · ${p.meeting.where}"
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

    // The slots that move the text first (they drive the 0.52 s glide, M1), the ring image last: it takes the longest
    // to draw and used to hold the others back.
    private val allServices = listOf(
        StatusDataSourceService::class.java,
        DetailsDataSourceService::class.java,
        TitleScrollDataSourceService::class.java,
        TitleScrollArrowDataSourceService::class.java,
        PromoteDataSourceService::class.java,
        RingImageDataSourceService::class.java,
    )

    fun requestUpdate(context: Context, vararg services: Class<out ComplicationDataSourceService>) {
        cache = null
        for (service in services.ifEmpty { allServices.toTypedArray() }) {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
        }
    }

    /** Start and end of a placed meeting on the 12 h dial, in degrees clockwise from 12 o'clock. */
    private fun degrees(p: Placed): Pair<Float, Float> {
        val start = Math.floorMod(p.cs, ScheduleRules.DIAL_MIN) / ScheduleRules.DIAL_MIN.toFloat() * 360f
        return start to start + (p.ce - p.cs) / ScheduleRules.DIAL_MIN.toFloat() * 360f
    }

    private fun ranged(value: Float, min: Float, max: Float, text: String, title: String) =
        RangedValueComplicationData.Builder(value, min, max, text(text.ifEmpty { "—" }))
            .setText(text(text))
            .apply { if (title.isNotEmpty()) setTitle(text(title)) }
            .build()

    private fun text(s: String) = PlainComplicationText.Builder(s).build()
}
