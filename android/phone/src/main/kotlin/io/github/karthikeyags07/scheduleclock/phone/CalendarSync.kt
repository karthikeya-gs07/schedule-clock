package io.github.karthikeyags07.scheduleclock.phone

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
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

/**
 * Reads the phone's calendar from yesterday (a meeting may still be live) to the end of tomorrow and
 * sends it to the watch as one Data Layer item at [PATH]. The watch's `PhoneCalendarListener` reads the
 * same keys. Declined and cancelled events are left out; private events go without title or place, so
 * they never leave the phone (RULES C13).
 */
object CalendarSync {
    const val PATH = "/calendar"

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Reads and sends; blocking, so call it off the main thread. Returns the number of events sent. */
    fun send(context: Context): Int {
        val events = read(context)
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putLong("updated", System.currentTimeMillis())   // always changes, so the watch hears every sync
            dataMap.putDataMapArrayList("events", events)
        }.asPutDataRequest().setUrgent()
        Tasks.await(Wearable.getDataClient(context).putDataItem(request))
        context.getSharedPreferences("sync", Context.MODE_PRIVATE).edit()
            .putLong("lastSync", System.currentTimeMillis()).putInt("lastCount", events.size).apply()
        Log.i(TAG, "sent ${events.size} events to the watch")
        return events.size
    }

    private fun read(context: Context): ArrayList<DataMap> {
        val out = ArrayList<DataMap>()
        if (!hasPermission(context)) return out
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
                val begin = long(c, Instances.BEGIN) ?: continue
                val access = int(c, Instances.ACCESS_LEVEL)
                val private = access == Events.ACCESS_PRIVATE || access == Events.ACCESS_CONFIDENTIAL
                out += DataMap().apply {
                    putLong("begin", begin)
                    putLong("end", long(c, Instances.END) ?: begin)
                    putBoolean("allDay", int(c, Instances.ALL_DAY) == 1)
                    putBoolean("private", private)
                    putString("title", if (private) "" else string(c, Instances.TITLE).orEmpty())
                    putString("where", if (private) "" else string(c, Instances.EVENT_LOCATION).orEmpty())
                }
            }
        }
        return out
    }

    private fun index(c: Cursor, column: String) = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }
    private fun int(c: Cursor, column: String) = index(c, column)?.let { c.getInt(it) }
    private fun long(c: Cursor, column: String) = index(c, column)?.let { c.getLong(it) }
    private fun string(c: Cursor, column: String) = index(c, column)?.let { c.getString(it) }
}
