package io.github.karthikeyags07.scheduleclock

/**
 * The prototype's rules engine (RULES §1 and §3), ported for the spike. Times are minutes from
 * today's midnight; tomorrow's meetings are 1440 + …
 */
data class Meeting(
    val startMin: Int,
    val endMin: Int,
    val title: String,
    val where: String = "",
    val private: Boolean = false,
    /** W9: an all-day event is kept as an event but never placed, shown or counted as a clash. From CalendarContract.Events.ALL_DAY. */
    val allDay: Boolean = false,
) {
    val key: String get() = "$startMin|$title"
}

enum class Lane { MAIN, LATER }

/** A meeting placed on the dial: [cs, ce) is the part drawn (clipped to the window). */
data class Placed(val meeting: Meeting, val lane: Lane, val cs: Int, val ce: Int, val allDay: List<Meeting> = emptyList())

object ScheduleRules {
    private const val WINDOW_MIN = 12 * 60
    private const val DAY_MIN = 24 * 60
    private const val LOOKAHEAD_MIN = 14 * 60   // W6: tomorrow's meetings starting this soon wait on the lane
    const val DIAL_MIN = 12 * 60
    private const val DAY_START_MIN = 6 * 60   // W5a: before 6 AM the day's meetings wait on the lane
    private const val PROMOTE_GAP_MIN = 30      // W8: a lane meeting moves to the ring 30 min after the now dot passes its slot

    /**
     * W1–W7: main ring = meetings that end within the 12 h window (which starts at a live meeting's
     * start, W2); later lane = the rest of today, plus tomorrow's that start inside the window but
     * end past it; everything else, including past meetings, is not shown. Chronological (W10).
     * W5a: from 12 to 6 AM the day hasn't started, so nothing takes the main ring: every meeting, night ones
     * included, stays on the lane once it ends within the window (never a clipped sliver; never past 6 PM,
     * so lane arcs never share an angle), and they settle onto the ring at 6 AM.
     * W8: a meeting takes the main ring only once it ends 30 min inside the window, i.e. 30 min after the now
     * dot has passed its slot (the 7–8 PM call moves at 8:30 AM, not 8:00).
     */
    fun place(meetings: List<Meeting>, now: Int): List<Placed> {
        // W9 (2026-10-31): all-day events are left off the face entirely; they don't clash with the day's meetings.
        val all = meetings.filter { !it.allDay }.sortedBy { it.startMin }
        val winStart = minOf(now, all.filter { it.startMin < now && it.endMin > now }.minOfOrNull { it.startMin } ?: now)
        val winEnd = winStart + WINDOW_MIN
        val laneEnd = minOf(winEnd, DAY_START_MIN + WINDOW_MIN)
        val ringEnd = winEnd - PROMOTE_GAP_MIN
        return all.mapNotNull { m ->
            when {
                now < DAY_START_MIN ->
                    if (m.endMin > now && m.endMin <= laneEnd) Placed(m, Lane.LATER, m.startMin, m.endMin) else null
                // W5: tomorrow's meetings (from the next midnight) never take the main ring before midnight.
                m.endMin > now && m.endMin <= ringEnd && m.startMin < DAY_MIN ->
                    Placed(m, Lane.MAIN, maxOf(m.startMin, winStart), m.endMin)
                (m.endMin > ringEnd || m.startMin >= DAY_MIN) && m.startMin > now &&
                    (m.startMin < DAY_MIN || m.startMin < winEnd || m.startMin <= now + LOOKAHEAD_MIN) ->
                    Placed(m, Lane.LATER, m.startMin, if (m.startMin < DAY_MIN) minOf(m.endMin, DAY_MIN) else m.endMin)
                else -> null
            }
        }
    }


    fun isLive(p: Placed, now: Int) = p.meeting.startMin <= now && p.meeting.endMin > now

    /** C1: the next meeting that hasn't started; the live one only if nothing comes after it. */
    fun defaultIndex(list: List<Placed>, now: Int): Int {
        val i = list.indexOfFirst { !isLive(it, now) }
        return if (i >= 0) i else if (list.isEmpty()) -1 else 0
    }

    /** C12: the look-ahead arrow, while a meeting is live and the details show the upcoming one. */
    fun showsArrow(list: List<Placed>, index: Int, now: Int) =
        index >= 0 && !isLive(list[index], now) && list.any { isLive(it, now) } && index == defaultIndex(list, now)

    /** What this meeting clashes with (R4, C7): other main-ring meetings overlapping it, then the day's all-day events (W9). */
    fun clashesFor(list: List<Placed>, p: Placed): List<Meeting> =
        (if (p.lane != Lane.MAIN) emptyList()
        else list.filter { it !== p && it.lane == Lane.MAIN && it.meeting.startMin < p.meeting.endMin && p.meeting.startMin < it.meeting.endMin }
            .map { it.meeting }) + p.allDay

    /** Merged minute ranges where two or more main-ring meetings overlap, or one sits on an all-day event (R4, W9). */
    fun clashRanges(list: List<Placed>): List<Pair<Int, Int>> {
        val main = list.filter { it.lane == Lane.MAIN }
        val pairs = buildList {
            main.filter { it.allDay.isNotEmpty() }.forEach { add(it.cs to it.ce) }
            for (i in main.indices) for (j in i + 1 until main.size) {
                val a = maxOf(main[i].cs, main[j].cs)
                val b = minOf(main[i].ce, main[j].ce)
                if (b > a) add(a to b)
            }
        }.sortedBy { it.first }
        return pairs.fold(mutableListOf()) { merged, r ->
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.second) merged[merged.size - 1] = last.first to maxOf(last.second, r.second)
            else merged += r
            merged
        }
    }

    /**
     * R4a (2026-10-31): how many main-ring meetings overlap, moment by moment. Minute ranges where 3 or more run at
     * once, with that count (capped at 4): the ring splits a clash stripe in half there (3), or both (4).
     */
    fun clashDepths(list: List<Placed>): List<Triple<Int, Int, Int>> {
        val main = list.filter { it.lane == Lane.MAIN }
        val edges = main.flatMap { listOf(it.cs, it.ce) }.distinct().sorted()
        return edges.zipWithNext().mapNotNull { (a, b) ->
            val depth = main.count { it.cs < b && a < it.ce }
            if (depth >= 3) Triple(a, b, minOf(depth, 4)) else null
        }.fold(mutableListOf()) { merged, seg ->   // join neighbours of the same depth
            val last = merged.lastOrNull()
            if (last != null && last.second == seg.first && last.third == seg.third) merged[merged.size - 1] = Triple(last.first, seg.second, seg.third)
            else merged += seg
            merged
        }
    }

    /** I3: indexes of meetings touching a dial hour, main ring first, then the later lane. */
    fun meetingsInHour(list: List<Placed>, hour: Int): List<Int> =
        list.indices.filter { i ->
            val p = list[i]
            (p.cs until p.ce).any { ((it % DIAL_MIN) + DIAL_MIN) % DIAL_MIN / 60 == hour }
        }.sortedWith(compareBy({ list[it].lane == Lane.LATER }, { it }))

    /** C8: "1:00 – 2:00 PM", or AM/PM on both ends when they differ. */
    fun formatRange(m: Meeting): String {
        val sameHalf = (Math.floorMod(m.startMin, DAY_MIN) < 720) == (Math.floorMod(m.endMin, DAY_MIN) < 720)
        return "${clock(m.startMin, !sameHalf)} – ${clock(m.endMin, true)}"
    }

    private fun clock(min: Int, suffix: Boolean): String {
        val h24 = Math.floorMod(Math.floorDiv(min, 60), 24)   // a meeting can start yesterday (min < 0)
        val text = "${if (h24 % 12 == 0) 12 else h24 % 12}:${Math.floorMod(min, 60).toString().padStart(2, '0')}"
        return if (suffix) "$text ${if (h24 < 12) "AM" else "PM"}" else text
    }
}
