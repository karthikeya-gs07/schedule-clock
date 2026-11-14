package io.github.karthikeyags07.scheduleclock

import android.app.NotificationManager
import android.content.Context
import com.google.android.gms.wearable.DataMap

/**
 * The choices made in the phone app (RULES A5, A6), received as the Data Layer item `/settings`: which
 * meeting buzzes are on, whether they come through Do Not Disturb, and the colourway. Everything is on
 * until the phone says otherwise.
 */
object PhoneSettings {
    const val PATH = "/settings"
    private fun prefs(context: Context) = context.getSharedPreferences("phone_settings", Context.MODE_PRIVATE)

    fun save(context: Context, map: DataMap) {
        prefs(context).edit().apply {
            map.getString("colourway")?.let { putString("colourway", it) }
            listOf("buzzStart", "buzzMiddle", "buzzEnd", "buzzDnd", "clock24", "showAmPm", "compass", "agenda", "flashlight", "countdown").forEach { if (map.containsKey(it)) putBoolean(it, map.getBoolean(it)) }
        }.apply()
    }

    /** Clock format chosen on the phone (RULES T6): 12-hour with AM/PM until it says otherwise. */
    fun countdown(context: Context) = prefs(context).getBoolean("countdown", true)
    fun flashlight(context: Context) = prefs(context).getBoolean("flashlight", false)
    fun agenda(context: Context) = prefs(context).getBoolean("agenda", true)
    fun compass(context: Context) = prefs(context).getBoolean("compass", false)
    fun clock24(context: Context) = prefs(context).getBoolean("clock24", false)
    fun showAmPm(context: Context) = prefs(context).getBoolean("showAmPm", true)

    fun colourway(context: Context): String? = prefs(context).getString("colourway", null)

    fun buzzOn(context: Context, kind: MeetingBuzz.Kind) = prefs(context).getBoolean(
        when (kind) { MeetingBuzz.Kind.START -> "buzzStart"; MeetingBuzz.Kind.MIDDLE -> "buzzMiddle"; MeetingBuzz.Kind.END -> "buzzEnd" }, true)

    /** False when the person turned off "Buzz in Do Not Disturb" and the watch is in Do Not Disturb right now. */
    fun buzzAllowedNow(context: Context): Boolean {
        if (prefs(context).getBoolean("buzzDnd", true)) return true
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return filter == NotificationManager.INTERRUPTION_FILTER_ALL || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }
}
