package io.github.karthikeyags07.scheduleclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

/**
 * Meeting buzzes (RULES §11, H1–H6): the watch vibrates 2 min before each meeting starts, halfway
 * through it, and 5 min before it ends. One exact alarm is set for the next buzz; when it fires, the
 * buzz plays and the one after is set. Re-armed whenever the calendar changes, the ring redraws,
 * the watch restarts or the clock changes.
 */
object MeetingBuzz {
    enum class Kind(val priority: Int, private val timings: LongArray) {
        START(2, longArrayOf(0, 750)),                 // H1: one 0.75 s buzz
        MIDDLE(1, longArrayOf(0, 1500)),               // H3: one 1.5 s buzz
        END(3, longArrayOf(0, 750, 400, 750));         // H2: two 0.75 s buzzes

        /** Full strength while on, silent in the gaps. */
        fun effect(): VibrationEffect =
            VibrationEffect.createWaveform(timings, IntArray(timings.size) { if (it % 2 == 1) 255 else 0 }, -1)
    }

    private data class Buzz(val at: Long, val kind: Kind)

    private const val START_BEFORE_MIN = 2
    private const val END_BEFORE_MIN = 5
    private const val MIDDLE_MIN_LENGTH = 15   // H4: shorter meetings get no halfway buzz
    private const val END_MIN_LENGTH = 10      // H4: …and no end warning under 10 min
    private const val LATE_MS = 2 * 60_000L    // a buzz fired this late (watch asleep) still plays

    /** Buzzes for the real calendar only (not the sample days), in epoch ms. */
    private fun buzzes(context: Context): List<Buzz> {
        if (!SpikeSchedule.usesCalendar(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        fun ms(min: Int) = midnight + min * 60_000L
        return CalendarSource.meetings(context).filter { !it.allDay }.flatMap { m ->
            val length = m.endMin - m.startMin
            buildList {
                add(Buzz(ms(m.startMin - START_BEFORE_MIN), Kind.START))
                if (length >= MIDDLE_MIN_LENGTH) add(Buzz(ms(m.startMin) + length * 30_000L, Kind.MIDDLE))
                if (length >= END_MIN_LENGTH) add(Buzz(ms(m.endMin - END_BEFORE_MIN), Kind.END))
            }
        }.filter { PhoneSettings.buzzOn(context, it.kind) }   // H6: the phone app's switches
    }

    /** Sets one exact alarm for the next buzz (or clears it when there is none). */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(context, 0, Intent(context, BuzzReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val all = buzzes(context)
        val next = all.filter { it.at > System.currentTimeMillis() + 1_000 }.minByOrNull { it.at }
        if (next == null || !alarms.canScheduleExactAlarms()) {
            Log.i(TAG, "no buzz set: ${all.size} buzzes, next=${next != null}, exact alarms=${alarms.canScheduleExactAlarms()}")
            alarms.cancel(intent)
            return
        }
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, intent)
        Log.i(TAG, "next buzz: ${next.kind} at ${java.time.Instant.ofEpochMilli(next.at).atZone(ZoneId.systemDefault()).toLocalTime()}")
    }

    /** Plays what's due now (H5: when several fall together, only the most important), then sets the next. */
    fun fire(context: Context) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("buzz", Context.MODE_PRIVATE)
        val last = prefs.getLong("lastAt", 0L)
        val due = buzzes(context).filter { it.at in (now - LATE_MS)..(now + 5_000) && it.at > last }
        due.maxByOrNull { it.kind.priority }?.let {
            if (PhoneSettings.buzzAllowedNow(context)) play(context, it.kind)
            else Log.i(TAG, "buzz: ${it.kind} held back (Do Not Disturb, and the phone app says not to buzz through it)")
            prefs.edit().putLong("lastAt", due.maxOf { b -> b.at }).apply()
        }
        schedule(context)
    }

    /** Plays a pattern at alarm strength, so it comes through while the screen is off. */
    fun play(context: Context, kind: Kind) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(kind.effect(), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        Log.i(TAG, "buzz: $kind")
    }
}

/** The exact alarm for the next buzz. */
class BuzzReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = MeetingBuzz.fire(context)
}

/** Alarms don't survive a restart, and a clock or time-zone change moves every buzz: set them again. */
class BuzzRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = MeetingBuzz.schedule(context)
}
