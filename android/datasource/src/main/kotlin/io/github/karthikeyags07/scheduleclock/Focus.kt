package io.github.karthikeyags07.scheduleclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The Focus face's data (RULES A26; the layout is in tools/gen_focus_face.py). While a meeting is on: its end, and
 * the ring from its start; otherwise the next meeting's start (today or tomorrow), the ring from an hour before.
 * The face counts down by itself from the watch's clock; an exact alarm refreshes this slot when the meeting ends
 * or the next one starts, and at midnight (the face counts seconds from midnight).
 */
object Focus {
    fun data(context: Context): RangedValueComplicationData {
        val nowSec = LocalTime.now().toSecondOfDay()
        val meetings = SpikeSchedule.meetingsNow(context).filter { !it.allDay }
        val live = meetings.filter { it.startMin * 60 <= nowSec && it.endMin * 60 > nowSec }.minByOrNull { it.endMin }
        val next = meetings.filter { it.startMin * 60 > nowSec }.minByOrNull { it.startMin }
        val flags = (if (PhoneSettings.clock24(context)) 4 else 0) + (if (PhoneSettings.hideAodDetails(context)) 8 else 0)
        val (m, kind, target, from) = when {
            live != null -> Quad(live, 1, live.endMin * 60, live.startMin * 60)
            next != null -> Quad(next, 2, next.startMin * 60, next.startMin * 60 - 3600)
            else -> Quad(null, 0, 0, 0)
        }
        schedule(context, listOfNotNull(live?.endMin, next?.startMin).map { it * 60 }.filter { it > nowSec }.minOrNull())
        val title = if (m == null) "" else if (m.private) "Private" else m.title.ifEmpty { "Busy" }
        val range = if (m == null) "" else (if (m.startMin >= 1440) "Tomorrow · " else "") + ScheduleRules.formatRange(m, PhoneSettings.clock24(context))
        return RangedValueComplicationData.Builder(target.toFloat(), from.toFloat(), 300000f + kind + flags, text(title.ifEmpty { "—" }))
            .setText(text(title))
            .apply { if (range.isNotEmpty()) setTitle(text(range)) }
            .build()
    }

    private data class Quad(val m: Meeting?, val kind: Int, val target: Int, val from: Int)
    private fun text(s: String) = PlainComplicationText.Builder(s).build()

    /** One exact alarm: at [atSec] (seconds from today's midnight) if sooner than midnight, else at midnight. */
    private fun schedule(context: Context, atSec: Int?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(context, 1, Intent(context, FocusTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val at = midnight + 1000L * minOf(atSec ?: 86400, 86400)
        if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }
}

/** A meeting started or ended (or it's midnight): the Focus face asks again. */
class FocusTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        SpikeSchedule.requestUpdate(context.applicationContext, FocusDataSourceService::class.java)
}
