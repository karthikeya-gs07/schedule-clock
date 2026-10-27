package io.github.karthikeyags07.scheduleclock

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Reads the watch's calendars into [Meeting]s (PRD §7.0 phase 2). Two providers are read and merged:
 * the watch's own calendar, and Wear OS's copy of the phone's calendar (about the next day, kept in
 * sync by the companion). Which one a watch fills in varies by maker, so both are read and duplicates
 * (same start, end and title) dropped. Columns are looked up by name, since the Wear copy may not have
 * all of them.
 */
object CalendarSource {
    private val PROVIDERS = listOf(
        Instances.CONTENT_URI,
        Uri.parse("content://com.google.android.wearable.provider.calendar/instances/when"),
    )

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Meetings from yesterday (one may still be live) to the end of tomorrow, in minutes from today's
     * local midnight. Declined and cancelled events are left out.
     */
    fun meetings(context: Context): List<Meeting> {
        if (!hasPermission(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val from = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val found = LinkedHashMap<String, Meeting>()
        for (base in PROVIDERS) {
            val uri = base.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    var n = 0
                    while (c.moveToNext()) {
                        val m = read(c, midnight, today) ?: continue
                        found.putIfAbsent("${m.startMin}|${m.endMin}|${m.title}", m)
                        n++
                    }
                    Log.i(TAG, "calendar ${uri.authority}: $n events")
                }
            } catch (e: Exception) {
                Log.w(TAG, "calendar ${uri.authority} unavailable: ${e.message}")
            }
        }
        return found.values.sortedBy { it.startMin }
    }

    private fun read(c: Cursor, midnight: Long, today: LocalDate): Meeting? {
        if (int(c, Instances.SELF_ATTENDEE_STATUS) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) return null
        if (int(c, Instances.STATUS) == Events.STATUS_CANCELED) return null
        val begin = long(c, Instances.BEGIN) ?: return null
        val end = long(c, Instances.END) ?: begin
        val allDay = int(c, Instances.ALL_DAY) == 1
        // W9: all-day events are stored as UTC midnight to midnight, so their day comes from the UTC date.
        fun allDayMin(ms: Long) = ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()).toInt() * 1440
        val startMin = if (allDay) allDayMin(begin) else Math.floorDiv(begin - midnight, 60_000L).toInt()
        val endMin = if (allDay) allDayMin(end) else Math.floorDiv(end - midnight, 60_000L).toInt()
        val access = int(c, Instances.ACCESS_LEVEL)
        return Meeting(
            startMin = startMin,
            endMin = maxOf(endMin, startMin + 1),
            title = string(c, Instances.TITLE)?.takeIf { it.isNotBlank() } ?: "(No title)",
            where = string(c, Instances.EVENT_LOCATION).orEmpty(),
            private = access == Events.ACCESS_PRIVATE || access == Events.ACCESS_CONFIDENTIAL,   // C13
            allDay = allDay,
        )
    }

    private fun index(c: Cursor, column: String) = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }
    private fun int(c: Cursor, column: String) = index(c, column)?.let { c.getInt(it) }
    private fun long(c: Cursor, column: String) = index(c, column)?.let { c.getLong(it) }
    private fun string(c: Cursor, column: String) = index(c, column)?.let { c.getString(it) }
}
