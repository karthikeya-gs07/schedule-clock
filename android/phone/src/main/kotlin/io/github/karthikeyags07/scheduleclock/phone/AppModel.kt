package io.github.karthikeyags07.scheduleclock.phone

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the screens show, refreshed from the calendar and the watch. Lives as long as the activity. */
class AppModel(private val context: Context, private val scope: CoroutineScope) {
    var setupDone by mutableStateOf(AppPrefs.setupDone(context))
    var hasPermission by mutableStateOf(CalendarSync.hasPermission(context))
    var calendars by mutableStateOf(emptyList<PhoneCalendar>())
    var enabled by mutableStateOf<Set<Long>?>(null)
    var meetings by mutableStateOf(emptyList<Meeting>())
    var colourway by mutableStateOf(AppPrefs.colourway(context))
    var link by mutableStateOf(WatchLink.State(null, null))
    var linkChecked by mutableStateOf(false)
    var syncing by mutableStateOf(false)
    var lastSync by mutableStateOf(0L)
    var lastCount by mutableStateOf(0)
    var buzz by mutableStateOf(AppPrefs.Buzz.entries.associateWith { AppPrefs.buzz(context, it) })
    var toast by mutableStateOf<String?>(null)
    /** The colourway on show while browsing Watchface (null elsewhere); the nav's selected icon takes its colour. */
    var previewColourway by mutableStateOf<String?>(null)

    fun isOn(c: PhoneCalendar) = enabled?.contains(c.id) ?: true

    /**
     * Demo mode, for store screenshots: the sample busy day and sample calendars at 10:13 AM instead of the
     * phone's real calendar, and a connected watch. Started with `adb shell am start … --ez demo true`; nothing
     * is saved or sent while it's on.
     */
    var demo by mutableStateOf(false)
        private set
    fun startDemo() {
        demo = true
        hasPermission = true
        calendars = SampleDay.calendars()
        enabled = calendars.filter { it.name != "Holidays" }.map { it.id }.toSet()
        meetings = SampleDay.meetings()
        link = WatchLink.State(WatchLink.Watch("demo", "Galaxy Watch8"), WatchLink.FaceStatus(true, true, true, colourway, true))
        linkChecked = true
        lastSync = System.currentTimeMillis() - 120_000
    }
    /** Now, as the screens see it: 10:13 AM in demo mode. */
    fun now(): Long = if (!demo) System.currentTimeMillis() else
        LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + SampleDay.SAMPLE_MINUTE * 60_000L

    /** Re-reads calendars, meetings and the last sync; call when the app opens or calendar access changes. */
    fun reload() = scope.launch {
        if (demo) return@launch
        hasPermission = CalendarSync.hasPermission(context)
        val (cals, list) = withContext(Dispatchers.IO) { CalendarSync.calendars(context) to CalendarSync.meetings(context) }
        calendars = cals
        meetings = list
        enabled = AppPrefs.enabledCalendars(context)
        val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
        lastSync = prefs.getLong("lastSync", 0L)
        lastCount = prefs.getInt("lastCount", 0)
    }

    fun refreshWatch() = scope.launch {
        if (demo) return@launch
        var next = WatchLink.state(context)
        // Right after the watch app updates, the face reads as not active for a moment; ask again before saying so.
        if (next.face?.installed == true && next.face?.active == false && link.face?.active != false) {
            delay(3000)
            next = WatchLink.state(context)
        }
        link = next
        linkChecked = true
    }

    fun permissionChanged() {
        reload()
        if (CalendarSync.hasPermission(context)) SyncWorker.schedule(context)
    }

    fun toggleCalendar(c: PhoneCalendar, on: Boolean) {
        if (demo) { enabled = (enabled ?: emptySet()).let { if (on) it + c.id else it - c.id }; return }
        AppPrefs.setCalendarEnabled(context, calendars, c.id, on)
        enabled = AppPrefs.enabledCalendars(context)
        if (setupDone) say(if (on) "${c.name} will sync to your watch" else "${c.name} removed from your watch")
        scope.launch {
            meetings = withContext(Dispatchers.IO) { CalendarSync.meetings(context) }
            if (setupDone) syncNow(quiet = true)
        }
    }

    fun syncNow(quiet: Boolean = false) = scope.launch {
        if (demo) { syncing = true; delay(1000); syncing = false; lastSync = System.currentTimeMillis(); return@launch }
        syncing = true
        val started = System.currentTimeMillis()
        val sent = withContext(Dispatchers.IO) { runCatching { CalendarSync.send(context) } }
        delay((1000 - (System.currentTimeMillis() - started)).coerceAtLeast(0))   // a sync takes ~0.1 s: spin one full turn so it's seen
        syncing = false
        sent.onSuccess { n ->
            lastSync = System.currentTimeMillis(); lastCount = n
            if (!quiet) say(if (n == 1) "1 meeting sent to your watch" else "$n meetings sent to your watch")
        }.onFailure { if (!quiet) say("Couldn't reach your watch") }
    }

    fun pickColourway(c: String) {
        if (demo) { colourway = c; link = link.copy(face = link.face?.copy(colourway = c)); return }
        colourway = c
        AppPrefs.setColourway(context, c)
        scope.launch {
            // With the face on the watch, the change plays as a transition there (the ring in the new colour covers
            // the swap); otherwise, or if the watch can't open it, the swap goes by request or waits for the watch.
            val shown = link.face?.installed == true && WatchLink.openColourChange(context, c)
            if (shown) {
                say("Changing to ${lookName(c)}…")
                delay(4500)   // the swap and the new face loading, under the transition
                AppPrefs.sendSettings(context)   // after the swap, so the watch's catch-up finds it already done
                link = WatchLink.state(context)
                say("${lookName(c)} is on your watch")
            } else {
                AppPrefs.sendSettings(context)
                val status = WatchLink.setColourway(context, c)
                if (status != null) link = link.copy(face = status)
                say(if (status?.installed == true) "${lookName(c)} is on your watch" else "Saved. It's used when the face is on your watch")
            }
        }
    }

    fun setBuzz(b: AppPrefs.Buzz, on: Boolean) {
        AppPrefs.setBuzz(context, b, on)
        buzz = buzz + (b to on)
        AppPrefs.sendSettings(context)
    }

    fun finishSetup() {
        AppPrefs.setSetupDone(context, true)
        setupDone = true
        AppPrefs.sendSettings(context)
        if (hasPermission) SyncWorker.schedule(context)
    }

    /** Watch app missing: opens its Play page on the watch, then keeps checking until it answers. */
    fun getWatchApp() = scope.launch {
        if (!WatchLink.openPlayOnWatch(context)) { say("Couldn't reach your watch"); return@launch }
        say("Tap Install on your watch")
        repeat(40) {   // ~2 min
            delay(3000)
            link = WatchLink.state(context)
            if (link.face != null) return@launch
        }
    }

    fun restartSetup() { AppPrefs.setSetupDone(context, false); setupDone = false }

    fun say(text: String) = scope.launch {
        toast = text
        delay(1800)
        if (toast == text) toast = null
    }
}

fun lookName(c: String) = c.replaceFirstChar(Char::uppercase)
