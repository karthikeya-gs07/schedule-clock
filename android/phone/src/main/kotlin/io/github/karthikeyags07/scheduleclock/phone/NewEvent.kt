package io.github.karthikeyags07.scheduleclock.phone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * New event (RULES A16): CalenTime doesn't write to the calendar itself (no extra permission). It lists the
 * calendar apps on the phone that can add an event; the person picks one, and that app opens on its own
 * "new event" screen, starting at the next half hour for an hour. Back in CalenTime, the new event syncs.
 */
data class CalendarApp(val packageName: String, val label: String, val icon: ImageBitmap)

/** The calendar apps that can add an event, by name. */
fun calendarApps(context: Context): List<CalendarApp> {
    val pm = context.packageManager
    return pm.queryIntentActivities(newEventIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        .distinctBy { it.activityInfo.packageName }
        .map { CalendarApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()) }
        .sortedBy { it.label.lowercase() }
}

/** A new event starting at the next half hour, for an hour. */
private fun newEventIntent(): Intent {
    val now = ZonedDateTime.now()
    val start = now.truncatedTo(ChronoUnit.HOURS).plusMinutes(if (now.minute < 30) 30 else 60)
    return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start.plusHours(1).toInstant().toEpochMilli())
}

/** The picker: one row per calendar app. Choosing one opens it; CalenTime syncs when the person comes back. */
@Composable
fun NewEventPicker(m: AppModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { calendarApps(context) }
    CalenPopup("Add an event", onClose,
        if (apps.isEmpty()) "No calendar app on this phone can add events. Install one, such as Google Calendar, and try again."
        else "Pick the app to add it in. Your new event reaches the watch when you come back.") {
        if (apps.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            PopupRows(apps.size) { i ->
                val app = apps[i]
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        onClose()
                        runCatching { context.startActivity(newEventIntent().setPackage(app.packageName)) }
                            .onSuccess { m.syncOnReturn = true }
                            .onFailure { m.say("Couldn't open ${app.label}") }
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(app.icon, null, Modifier.size(36.dp).clip(CircleShape))
                    Text(app.label, style = BodyStyle, modifier = Modifier.weight(1f))
                    Icon(Icons.chevron, null, tint = Palette.muted, modifier = Modifier.size(18.dp).rotate(-90f))
                }
            }
        }
        // Nothing to confirm here (picking an app is the action): one button to close.
        Spacer(Modifier.height(20.dp))
        SecondaryButton("Cancel", Modifier.fillMaxWidth(), onClose)
    }
}
