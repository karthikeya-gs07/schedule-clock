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

    /** Meetings hidden from the watch (RULES A15). They stay in the drawer, in order, dimmed. */
    var hidden by mutableStateOf(AppPrefs.hiddenEvents(context))
    fun isHidden(meeting: Meeting) = meeting.key in hidden
    /** The meetings the watch shows: everything but the hidden ones. */
    val shownMeetings get() = meetings.filter { it.key !in hidden }.map { if (it.key in locked) it.copy(private = true) else it }

    /** Meetings made private in the drawer (RULES A17): sent without title or place, shown as private on the face. */
    var locked by mutableStateOf(AppPrefs.lockedEvents(context))
    fun isPrivate(meeting: Meeting) = meeting.private || meeting.key in locked
    fun toggleLocked(meeting: Meeting) {
        if (meeting.private) return   // private in the calendar itself: stays private
        locked = if (meeting.key in locked) locked - meeting.key else locked + meeting.key
        if (demo) return
        AppPrefs.setLockedEvents(context, locked)
        syncNow(quiet = true)
    }

    fun toggleHidden(meeting: Meeting) {
        val hide = !isHidden(meeting)
        hidden = if (hide) hidden + meeting.key else hidden - meeting.key
        if (demo) return
        AppPrefs.setHiddenEvents(context, hidden)
        syncNow(quiet = true)
    }

    /** Opens this occurrence in the phone's calendar app (RULES A18); edits made there sync on return. */
    fun openEvent(meeting: Meeting) {
        if (demo || meeting.eventId == 0L) return
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, meeting.eventId))
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, meeting.begin)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, meeting.end)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        // Straight to the app the meeting's calendar belongs to, when it's on the phone; then Samsung Calendar;
        // otherwise Android asks.
        val type = calendars.firstOrNull { it.id == meeting.calendarId }?.accountType.orEmpty()
        val owner = when {
            type.contains("google", true) -> "com.google.android.calendar"
            type.contains("outlook", true) || type.contains("exchange", true) || type.contains("microsoft", true) -> "com.microsoft.office.outlook"
            type.contains("samsung", true) || type == "com.osp.app.signin" || type.equals("LOCAL", true) -> "com.samsung.android.calendar"
            else -> null
        }
        // Samsung Calendar shows every calendar on the phone, so it stands in when the owner's app isn't installed.
        listOfNotNull(owner, "com.samsung.android.calendar")
            .firstOrNull { android.content.Intent(intent).setPackage(it).resolveActivity(context.packageManager) != null }
            ?.let { intent.setPackage(it) }
        runCatching { context.startActivity(intent) }
            .onSuccess { syncOnReturn = true }
            .onFailure { say("Couldn't open your calendar") }
    }

    /** Set when a calendar app was opened to add an event, so coming back syncs the new event to the watch. */
    var syncOnReturn = false

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
        link = WatchLink.State(WatchLink.Watch("demo", "Galaxy Watch8"), WatchLink.FaceStatus(true, true, true, colourway, true, BuildConfig.WATCH_VERSION_CODE))
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
            // Not the watch face yet (never put on, or another face picked since): the watch's face setup puts it on
            // and makes it the watch face. Android lets an app do that once; after that the watch says how to pick it.
            if (link.face?.installed != true || link.face?.active != true) {
                AppPrefs.sendSettings(context)
                if (!WatchLink.install(context, c)) { say("Couldn't reach your watch"); return@launch }
                val usedBefore = link.face?.activateUsed == true
                // The watch can't switch faces itself any more: show the steps here too, at the same time.
                if (usedBefore) notice = PICK_ON_WATCH
                repeat(if (usedBefore) 45 else 10) {   // ~20 s for the face to go on; ~90 s for the person to pick it
                    delay(2000)
                    val next = WatchLink.state(context)
                    if (next.face != null) link = next
                    if (link.face?.active == true) { if (notice == PICK_ON_WATCH) notice = null; return@launch }   // picked: the steps close
                }
                if (!usedBefore && link.face?.activateUsed == true) notice = PICK_ON_WATCH
                return@launch
            }
            val shown = WatchLink.openColourChange(context, c)
            if (shown) {
                delay(4500)   // the swap and the new face loading, under the transition
                AppPrefs.sendSettings(context)   // after the swap, so the watch's catch-up finds it already done
                link = WatchLink.state(context)
            } else {
                AppPrefs.sendSettings(context)
                val status = WatchLink.setColourway(context, c)
                if (status != null) link = link.copy(face = status)
                if (status?.installed != true) say("Saved. It's used when the face is on your watch")
            }
        }
    }

    fun setBuzz(b: AppPrefs.Buzz, on: Boolean) {
        AppPrefs.setBuzz(context, b, on)
        buzz = buzz + (b to on)
        AppPrefs.sendSettings(context)
    }

    var compass by mutableStateOf(AppPrefs.compass(context))
    fun switchCompass(on: Boolean) {
        AppPrefs.setCompass(context, on)
        compass = on
        AppPrefs.sendSettings(context)
    }

    var agenda by mutableStateOf(AppPrefs.agenda(context))
    fun switchAgenda(on: Boolean) {
        AppPrefs.setAgenda(context, on)
        agenda = on
        AppPrefs.sendSettings(context)
    }

    var clock24 by mutableStateOf(AppPrefs.clock24(context))
    var showAmPm by mutableStateOf(AppPrefs.showAmPm(context))
    private fun clockParam() = if (clock24) "24" else if (showAmPm) "12" else "12plain"
    init { FaceClock.param = clockParam() }
    fun setClock(clock24: Boolean = this.clock24, showAmPm: Boolean = this.showAmPm) {
        AppPrefs.setClock(context, clock24, showAmPm)
        this.clock24 = clock24; this.showAmPm = showAmPm
        FaceClock.param = clockParam()
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

    // ---- Updates (RULES A14) ----
    /** A newer CalenTime is on Play for this phone; the prompt shows when the app opens. */
    var appUpdateReady by mutableStateOf(false)
    var appUpdateDismissed by mutableStateOf(false)
    /** The watch app is older than the one released with this phone app. */
    val watchUpdateReady get() = !demo && link.face?.let { it.version < BuildConfig.WATCH_VERSION_CODE } == true
    var watchUpdateDismissed by mutableStateOf(false)

    /** Opens CalenTime's Play page on the watch so it can update there, then waits for the new version. */
    fun updateWatchApp() = scope.launch {
        watchUpdateDismissed = true
        if (!WatchLink.openPlayOnWatch(context)) { say("Couldn't reach your watch"); return@launch }
        say("Tap Update on your watch")
        repeat(60) {   // ~3 min
            delay(3000)
            val next = WatchLink.state(context)
            if (next.face != null) link = next
            if (link.face != null && !watchUpdateReady) { say("CalenTime is up to date on your watch"); return@launch }
        }
    }

    /** A message pop-up (title, text), such as the answer to "Check for updates" (RULES A14). */
    var notice by mutableStateOf<Pair<String, String>?>(null)
    var checkingApp by mutableStateOf(false)
    var checkingWatch by mutableStateOf(false)
    /** Asks Play for a newer phone app; set by the activity, which owns Play's update manager. */
    var checkAppUpdate: () -> Unit = {}

    /** Settings → Check for watch update: asks the watch its version, then prompts to update or says it's current. */
    fun checkWatchUpdate() = scope.launch {
        if (demo) { notice = "Your watch is up to date" to "CalenTime on your watch is the latest version."; return@launch }
        checkingWatch = true
        val next = WatchLink.state(context)
        checkingWatch = false
        link = next; linkChecked = true
        notice = when {
            next.watch == null -> "Watch not connected" to "Open Galaxy Wearable and check your watch is paired and nearby, then try again."
            next.face == null -> "CalenTime isn't on your watch" to "Get the watch app from the watch status on Today."
            watchUpdateReady -> { watchUpdateDismissed = false; null }   // the update prompt shows
            else -> "Your watch is up to date" to "CalenTime on your watch is the latest version."
        }
    }

    fun restartSetup() { AppPrefs.setSetupDone(context, false); setupDone = false }

    fun say(text: String) = scope.launch {
        toast = text
        delay(1800)
        if (toast == text) toast = null
    }
}

private val PICK_ON_WATCH = "Pick CalenTime on your watch" to "Touch and hold your watch face, swipe to CalenTime and tap it."

fun lookName(c: String) = Looks.of(c).name
