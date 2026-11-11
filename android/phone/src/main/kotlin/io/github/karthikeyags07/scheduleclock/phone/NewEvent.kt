package io.github.karthikeyags07.scheduleclock.phone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Cancel", style = BodyStyle.copy(color = Palette.muted)) } },
        title = { Text("Add an event in…", style = BodyStyle.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)) },
        text = {
            if (apps.isEmpty()) Text("No calendar app on this phone can add events. Install one, such as Google Calendar, and try again.",
                style = SubStyle.copy(color = Palette.body))
            else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pick the app to add it in. Your new event reaches the watch when you come back.",
                    style = SubStyle.copy(color = Palette.body), modifier = Modifier.padding(bottom = 8.dp))
                apps.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .05f))
                            .clickable(role = Role.Button) {
                                onClose()
                                runCatching { context.startActivity(newEventIntent().setPackage(app.packageName)) }
                                    .onSuccess { m.syncOnReturn = true }
                                    .onFailure { m.say("Couldn't open ${app.label}") }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Image(app.icon, null, Modifier.size(36.dp))
                        Text(app.label, style = BodyStyle.copy(fontSize = 16.sp))
                    }
                }
            }
        },
        containerColor = Palette.surface,
        shape = RoundedCornerShape(28.dp),
    )
}
