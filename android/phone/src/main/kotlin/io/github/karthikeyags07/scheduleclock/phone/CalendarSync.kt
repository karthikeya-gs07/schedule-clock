package io.github.karthikeyags07.scheduleclock.phone

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate
import java.time.ZoneId

internal const val TAG = "CalenTimePhone"

/** One calendar on the phone, as listed on the Calendars screen (RULES A4). */
data class PhoneCalendar(val id: Long, val name: String, val account: String, val accountType: String, val color: Int)

/** A meeting as the phone app lists it (Today) and previews it on the face. Times in epoch ms. */
data class Meeting(
    val begin: Long, val end: Long, val title: String, val where: String,
    val allDay: Boolean, val private: Boolean, val calendarId: Long,
)

/**
 * Reads the phone's calendar from yesterday (a meeting may still be live) to the end of tomorrow and
 * sends it to the watch as one Data Layer item at [PATH]. The watch's `PhoneCalendarListener` reads the
 * same keys. Only the calendars switched on in the app are read (RULES A4). Declined and cancelled events
 * are left out; private events go without title or place, so they never leave the phone (RULES C13).
 */
object CalendarSync {
    const val PATH = "/calendar"

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Every calendar on the phone that the calendar app shows, sorted by account then name. */
    fun calendars(context: Context): List<PhoneCalendar> {
        if (!hasPermission(context)) return emptyList()
        val out = mutableListOf<PhoneCalendar>()
        val columns = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR, Calendars.VISIBLE)
        context.contentResolver.query(Calendars.CONTENT_URI, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (int(c, Calendars.VISIBLE) == 0) continue
                out += PhoneCalendar(
                    id = long(c, Calendars._ID) ?: continue,
                    name = string(c, Calendars.CALENDAR_DISPLAY_NAME).orEmpty(),
                    account = string(c, Calendars.ACCOUNT_NAME).orEmpty(),
                    accountType = string(c, Calendars.ACCOUNT_TYPE).orEmpty(),
                    color = int(c, Calendars.CALENDAR_COLOR) ?: 0xff8e8e93.toInt(),
                )
            }
        }
        return out.sortedWith(compareBy({ it.accountType }, { it.account }, { it.name }))
    }

    /** Meetings from the switched-on calendars, yesterday to the end of tomorrow, by start time. */
    fun meetings(context: Context): List<Meeting> {
        if (!hasPermission(context)) return emptyList()
        val enabled = AppPrefs.enabledCalendars(context)
        val out = mutableListOf<Meeting>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            ContentUris.appendId(it, today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli())
        }.build()
        context.contentResolver.query(uri, null, null, null, Instances.BEGIN)?.use { c ->
            while (c.moveToNext()) {
                if (int(c, Instances.SELF_ATTENDEE_STATUS) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                if (int(c, Instances.STATUS) == Events.STATUS_CANCELED) continue
                if (int(c, Instances.VISIBLE) == 0) continue   // calendars hidden in the calendar app
                val calendarId = long(c, Instances.CALENDAR_ID) ?: continue
                if (enabled != null && calendarId !in enabled) continue
                val begin = long(c, Instances.BEGIN) ?: continue
                val access = int(c, Instances.ACCESS_LEVEL)
                out += Meeting(
                    begin = begin,
                    end = long(c, Instances.END) ?: begin,
                    title = string(c, Instances.TITLE).orEmpty(),
                    where = string(c, Instances.EVENT_LOCATION).orEmpty(),
                    allDay = int(c, Instances.ALL_DAY) == 1,
                    private = access == Events.ACCESS_PRIVATE || access == Events.ACCESS_CONFIDENTIAL,
                    calendarId = calendarId,
                )
            }
        }
        return out
    }

    /** Reads and sends; blocking, so call it off the main thread. Returns the number of events sent. */
    fun send(context: Context): Int {
        val events = ArrayList(meetings(context).map { m ->
            DataMap().apply {
                putLong("begin", m.begin)
                putLong("end", m.end)
                putBoolean("allDay", m.allDay)
                putBoolean("private", m.private)
                putString("title", if (m.private) "" else m.title)
                putString("where", if (m.private) "" else m.where)
            }
        })
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putLong("updated", System.currentTimeMillis())   // always changes, so the watch hears every sync
            dataMap.putDataMapArrayList("events", events)
        }.asPutDataRequest().setUrgent()
        Tasks.await(Wearable.getDataClient(context).putDataItem(request))
        context.getSharedPreferences("sync", Context.MODE_PRIVATE).edit()
            .putLong("lastSync", System.currentTimeMillis()).putInt("lastCount", events.size).apply()
        Log.i(TAG, "sent ${events.size} events to the watch")
        SyncWidget.refresh(context)   // the home-screen widget shows the new time
        return events.size
    }

    private fun index(c: Cursor, column: String) = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }
    private fun int(c: Cursor, column: String) = index(c, column)?.let { c.getInt(it) }
    private fun long(c: Cursor, column: String) = index(c, column)?.let { c.getLong(it) }
    private fun string(c: Cursor, column: String) = index(c, column)?.let { c.getString(it) }
}
