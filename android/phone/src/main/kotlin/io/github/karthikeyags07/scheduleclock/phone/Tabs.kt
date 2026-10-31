package io.github.karthikeyags07.scheduleclock.phone

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------- Meeting rows (Today's drawer) ----------
private val TIME = DateTimeFormatter.ofPattern("h:mm a")

@Composable
internal fun MeetingRow(meeting: Meeting, live: Boolean, clash: Boolean, color: Color) = Row(
    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(Instant.ofEpochMilli(meeting.begin).atZone(ZoneId.systemDefault()).format(TIME),
        style = HintStyle.copy(color = if (live) LocalAccent.current else Palette.muted), modifier = Modifier.width(66.dp).padding(top = 1.dp))
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(meeting.title.ifEmpty { "Busy" }, style = BodyStyle, modifier = Modifier.weight(1f, fill = false))
            if (meeting.private) Tag("Private", Palette.muted, Palette.surface2)
            if (clash) Tag("Clash", Color(0xFFFF9D82), Color(0x24FF7A59))
        }
        val where = if (meeting.private) "Shown as “Private” on the watch" else meeting.where
        if (where.isNotEmpty()) Text(where, style = HintStyle, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun Tag(text: String, fg: Color, bg: Color) = Text(
    text, color = fg, fontSize = 11.sp,
    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 7.dp, vertical = 1.dp),
)

// ---------- Calendars (RULES A4) ----------
@Composable
fun CalendarsList(m: AppModel, onAllow: () -> Unit) {
    if (!m.hasPermission) {
        Spacer(Modifier.height(12.dp))
        Banner("Calendar access is off", "Allow access to choose which calendars go to your watch.", action = "Allow access", onAction = onAllow)
    }
    // One group per provider and account: "Google · you@gmail.com", "Samsung · …", "On this phone".
    m.calendars.groupBy { accountLabel(it.accountType) to it.account.takeIf { a -> '@' in a }.orEmpty() }
        .toList().sortedBy { (key, _) -> PROVIDER_ORDER.indexOf(key.first) }
        .forEachIndexed { i, (key, cals) ->
        if (i > 0) ItemDivider(Modifier.padding(top = 8.dp))   // a hairline between accounts
        PageHeading(key.first, key.second.ifEmpty { null })
        cals.forEach { c ->
            ToggleItem(c.name.ifEmpty { "Calendar" }, checked = m.isOn(c), dot = Color(c.color)) { m.toggleCalendar(c, it) }
        }
    }
    if (m.hasPermission && m.calendars.isEmpty()) Text("No calendars on this phone yet.", style = PageBody, modifier = Modifier.padding(vertical = 22.dp))
    OutlookHelp(divider = m.calendars.isNotEmpty())
}

private val PROVIDER_ORDER = listOf("Google", "Outlook", "Samsung", "On this phone", "Other")

/** Calendar providers by Android account type: Samsung accounts are `com.osp.app.signin`, local ones `LOCAL`. */
private fun accountLabel(type: String) = when {
    type.contains("google", true) -> "Google"
    type.contains("outlook", true) || type.contains("exchange", true) || type.contains("microsoft", true) -> "Outlook"
    type.contains("samsung", true) || type == "com.osp.app.signin" -> "Samsung"
    type.equals("LOCAL", true) -> "On this phone"
    else -> "Other"
}

@Composable
private fun OutlookHelp(divider: Boolean) {
    var open by remember { mutableStateOf(false) }
    // A question that opens into the steps, in the page's plain style (no card).
    if (divider) ItemDivider(Modifier.padding(top = 8.dp))
    PageHeading("Missing a calendar?")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { open = !open }.padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).frostCard(CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.info, null, tint = Palette.text, modifier = Modifier.size(18.dp))
            }
            Text("Don't see your work or Teams calendar?", style = BodyStyle.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Icon(Icons.chevron, null, tint = Palette.text, modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f))
        }
        if (open) Column(Modifier.padding(start = 52.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Open the **Outlook** app and tap your profile picture.", "Tap **Settings**, then your work account.",
                "Turn on **Sync calendars**.", "Come back here. It shows up under Outlook within a minute.")
                .forEachIndexed { i, s -> Text(bold("${i + 1}. $s"), style = ItemTextStyle) }
        }
    }
}

/** **text** → bold, for short help lists. */
fun bold(s: String) = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Palette.text)) { append(part) } else append(part) }
}

// ---------- Settings (RULES A6) ----------
@Composable
fun SettingsScreen(m: AppModel, onRunSetup: () -> Unit) {
    PageTitle("Settings")
    PageHeading("Meeting buzzes")
    val any = m.buzz[AppPrefs.Buzz.START] == true || m.buzz[AppPrefs.Buzz.MIDDLE] == true || m.buzz[AppPrefs.Buzz.END] == true
    ToggleItem("2 min before it starts", "One short buzz", m.buzz[AppPrefs.Buzz.START] == true, Icons.buzz) { m.setBuzz(AppPrefs.Buzz.START, it) }
    ToggleItem("Halfway through", "One long buzz · meetings of 15 min or more", m.buzz[AppPrefs.Buzz.MIDDLE] == true, Icons.clock) { m.setBuzz(AppPrefs.Buzz.MIDDLE, it) }
    ToggleItem("5 min before it ends", "Two short buzzes · meetings of 10 min or more", m.buzz[AppPrefs.Buzz.END] == true, Icons.buzz) { m.setBuzz(AppPrefs.Buzz.END, it) }
    ToggleItem("Buzz in Do Not Disturb", "Buzzes still come through when your watch is silenced", m.buzz[AppPrefs.Buzz.DND] == true, Icons.watch, enabled = any) { m.setBuzz(AppPrefs.Buzz.DND, it) }
    // Sync isn't a setting: a short note says how it works.
    ItemDivider(Modifier.padding(top = 8.dp))   // hairlines between the sections
    PageHeading("Sync")
    IconItem(Icons.sync, "Every 15 minutes", "And as soon as a meeting changes. CalenTime sends yesterday to tomorrow from the calendars you picked.")
    IconItem(Icons.lock, "Private events", "They go to your watch without their title or place.")
    // Setup again (also on About): calendar access, calendars and the face on the watch, from the start.
    ItemDivider(Modifier.padding(top = 8.dp))
    PageHeading("Setup")
    Text("Go through setup again: calendar access, which calendars to send, and putting the face on your watch.",
        style = ItemTextStyle, modifier = Modifier.padding(bottom = 16.dp))
    RunSetupButton(onRunSetup, Modifier.fillMaxWidth())
}
