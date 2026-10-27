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
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * The day's [Meeting]s (PRD §7.0 phase 2), from up to three places, merged with duplicates (same start,
 * end and title) dropped:
 * - events sent by the phone app (`PhoneCalendarListener`, cached in a file): the only source on Galaxy
 *   watches, whose Samsung Calendar keeps events private (PRD §6.3f);
 * - the watch's own calendar, and Wear OS's copy of the phone's calendar, when calendar access is
 *   granted on the watch. Columns are looked up by name, since the Wear copy may not have all of them.
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
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val from = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val found = LinkedHashMap<String, Meeting>()
        fun add(m: Meeting) = found.putIfAbsent("${m.startMin}|${m.endMin}|${m.title}", m)
        phoneEvents(context)?.let { events ->
            for (i in 0 until events.length()) events.getJSONObject(i).run {
                add(meeting(getLong("begin"), getLong("end"), getBoolean("allDay"), optString("title"), optString("where"),
                    getBoolean("private"), midnight, today))
            }
        } ?: fetchFromPhone(context)
        if (hasPermission(context)) for (base in PROVIDERS) {
            val uri = base.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    var n = 0
                    while (c.moveToNext()) {
                        add(read(c, midnight, today) ?: continue)
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
        val access = int(c, Instances.ACCESS_LEVEL)
        return meeting(begin, long(c, Instances.END) ?: begin, int(c, Instances.ALL_DAY) == 1, string(c, Instances.TITLE),
            string(c, Instances.EVENT_LOCATION), access == Events.ACCESS_PRIVATE || access == Events.ACCESS_CONFIDENTIAL, midnight, today)
    }

    /** An event in epoch ms as a [Meeting] in minutes from today's local midnight. */
    private fun meeting(begin: Long, end: Long, allDay: Boolean, title: String?, where: String?, private: Boolean,
                        midnight: Long, today: LocalDate): Meeting {
        // W9: all-day events are stored as UTC midnight to midnight, so their day comes from the UTC date.
        fun allDayMin(ms: Long) = ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()).toInt() * 1440
        val startMin = if (allDay) allDayMin(begin) else Math.floorDiv(begin - midnight, 60_000L).toInt()
        val endMin = if (allDay) allDayMin(end) else Math.floorDiv(end - midnight, 60_000L).toInt()
        return Meeting(
            startMin = startMin,
            endMin = maxOf(endMin, startMin + 1),
            title = title?.takeIf { it.isNotBlank() } ?: "(No title)",
            where = where.orEmpty(),
            private = private,   // C13
            allDay = allDay,
        )
    }

    // ---- Events from the phone app ---------------------------------------------------------

    private const val PHONE_FILE = "phone_calendar.json"
    private val PHONE_URI: Uri = Uri.parse("wear://*/calendar")   // the phone app's Data Layer item
    @Volatile private var fetching = false

    fun hasPhoneData(context: Context) = File(context.filesDir, PHONE_FILE).exists()

    private fun phoneEvents(context: Context): JSONArray? = try {
        JSONObject(File(context.filesDir, PHONE_FILE).readText()).getJSONArray("events")
    } catch (e: Exception) {
        null
    }

    /** Keeps the phone's latest calendar (a Data Layer item) as a file, so the face never waits on the phone. */
    fun savePhone(context: Context, data: DataMap) {
        val events = JSONArray()
        for (e in data.getDataMapArrayList("events").orEmpty()) events.put(JSONObject().apply {
            put("begin", e.getLong("begin")); put("end", e.getLong("end"))
            put("allDay", e.getBoolean("allDay")); put("private", e.getBoolean("private"))
            put("title", e.getString("title").orEmpty()); put("where", e.getString("where").orEmpty())
        })
        File(context.filesDir, PHONE_FILE).writeText(JSONObject().put("updated", data.getLong("updated")).put("events", events).toString())
        Log.i(TAG, "phone calendar: ${events.length()} events")
    }

    /**
     * The phone may have sent its calendar before this app was installed, when nothing was listening:
     * fetch the current item once, then redraw.
     */
    private fun fetchFromPhone(context: Context) {
        if (fetching) return
        fetching = true
        Wearable.getDataClient(context).getDataItems(PHONE_URI).addOnCompleteListener { task ->
            task.result?.use { items -> items.firstOrNull()?.let { savePhone(context, DataMapItem.fromDataItem(it).dataMap) } }
            if (hasPhoneData(context)) SpikeSchedule.requestUpdate(context.applicationContext) else fetching = false
        }
    }

    private fun index(c: Cursor, column: String) = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }
    private fun int(c: Cursor, column: String) = index(c, column)?.let { c.getInt(it) }
    private fun long(c: Cursor, column: String) = index(c, column)?.let { c.getLong(it) }
    private fun string(c: Cursor, column: String) = index(c, column)?.let { c.getString(it) }
}
