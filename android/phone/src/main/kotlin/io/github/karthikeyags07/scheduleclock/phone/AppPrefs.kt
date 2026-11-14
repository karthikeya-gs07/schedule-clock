package io.github.karthikeyags07.scheduleclock.phone

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * What the person chose in the app: setup done, which calendars go to the watch (RULES A4), the colourway
 * (§9, A5) and the meeting buzzes (A6). The buzz and colour choices also go to the watch as the Data Layer
 * item `/settings`, which the watch app follows.
 */
object AppPrefs {
    val COLOURWAYS = Looks.IDS
    const val SETTINGS_PATH = "/settings"

    private fun prefs(context: Context) = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun setupDone(context: Context) = prefs(context).getBoolean("setupDone", false)
    fun setSetupDone(context: Context, done: Boolean) = prefs(context).edit().putBoolean("setupDone", done).apply()

    /** The calendars switched on, or null while the person hasn't changed anything (then all are on). */
    fun enabledCalendars(context: Context): Set<Long>? =
        prefs(context).getStringSet("calendars", null)?.mapNotNull { it.toLongOrNull() }?.toSet()

    fun setCalendarEnabled(context: Context, all: List<PhoneCalendar>, id: Long, on: Boolean) {
        val current = enabledCalendars(context) ?: all.map { it.id }.toSet()
        val next = if (on) current + id else current - id
        prefs(context).edit().putStringSet("calendars", next.map { it.toString() }.toSet()).apply()
    }

    /** Meetings hidden from the watch in Today's drawer (RULES A15), by [Meeting.key]. */
    fun hiddenEvents(context: Context): Set<String> = prefs(context).getStringSet("hiddenEvents", emptySet())!!.toSet()
    fun setHiddenEvents(context: Context, keys: Set<String>) {
        // Occurrences that ended more than two days ago can't come back into the sync window: drop them.
        val cutoff = System.currentTimeMillis() - 2 * 86_400_000L
        val kept = keys.filter { k -> k.substringAfter('@').toLongOrNull()?.let { it > cutoff } ?: false }.toSet()
        prefs(context).edit().putStringSet("hiddenEvents", kept).apply()
    }

    /** Meetings made private in Today's drawer (RULES A17), by [Meeting.key]; kept and pruned like hidden ones. */
    fun lockedEvents(context: Context): Set<String> = prefs(context).getStringSet("lockedEvents", emptySet())!!.toSet()
    fun setLockedEvents(context: Context, keys: Set<String>) {
        val cutoff = System.currentTimeMillis() - 2 * 86_400_000L
        val kept = keys.filter { k -> k.substringAfter('@').toLongOrNull()?.let { it > cutoff } ?: false }.toSet()
        prefs(context).edit().putStringSet("lockedEvents", kept).apply()
    }

    fun colourway(context: Context) = prefs(context).getString("colourway", "amber")!!
    fun setColourway(context: Context, colourway: String) = prefs(context).edit().putString("colourway", colourway).apply()

    enum class Buzz(val key: String) { START("buzzStart"), MIDDLE("buzzMiddle"), END("buzzEnd"), DND("buzzDnd") }
    fun buzz(context: Context, b: Buzz) = prefs(context).getBoolean(b.key, true)
    fun setBuzz(context: Context, b: Buzz, on: Boolean) = prefs(context).edit().putBoolean(b.key, on).apply()

    /** Clock on the watch face (RULES T6): 24-hour, and in 12-hour mode whether AM/PM shows. */
    /** Tapping the 12 on the face opens the compass (RULES A19); off until switched on in Settings. */
    fun compass(context: Context) = prefs(context).getBoolean("compass", false)
    fun setCompass(context: Context, on: Boolean) = prefs(context).edit().putBoolean("compass", on).apply()
    fun clock24(context: Context) = prefs(context).getBoolean("clock24", false)
    fun showAmPm(context: Context) = prefs(context).getBoolean("showAmPm", true)
    fun setClock(context: Context, clock24: Boolean, showAmPm: Boolean) =
        prefs(context).edit().putBoolean("clock24", clock24).putBoolean("showAmPm", showAmPm).apply()

    /** Sends the colourway and buzz choices to the watch (it keeps the latest; nothing private in it). */
    fun sendSettings(context: Context) {
        val request = PutDataMapRequest.create(SETTINGS_PATH).apply {
            dataMap.putString("colourway", colourway(context))
            Buzz.entries.forEach { dataMap.putBoolean(it.key, buzz(context, it)) }
            dataMap.putBoolean("clock24", clock24(context))
            dataMap.putBoolean("showAmPm", showAmPm(context))
            dataMap.putBoolean("compass", compass(context))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }
}
