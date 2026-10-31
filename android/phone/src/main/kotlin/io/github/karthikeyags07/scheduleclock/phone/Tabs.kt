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
        .forEach { (key, cals) ->
        Column(Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp)) {
            Text(key.first, style = BodyStyle, fontWeight = FontWeight.Medium)
            if (key.second.isNotEmpty()) Text(key.second, style = HintStyle)
        }
        Rows {
            cals.forEachIndexed { i, c ->
                if (i > 0) RowDivider()
                SwitchRow(c.name.ifEmpty { "Calendar" }, checked = m.isOn(c), dot = Color(c.color)) { m.toggleCalendar(c, it) }
            }
        }
    }
    if (m.hasPermission && m.calendars.isEmpty()) Text("No calendars on this phone yet.", style = SubStyle, modifier = Modifier.padding(vertical = 22.dp))
    OutlookHelp()
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
private fun OutlookHelp() {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp).clip(RoundedCornerShape(20.dp)).background(Palette.surface)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Don't see your work or Teams calendar?", style = BodyStyle, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.chevron, null, tint = Palette.text, modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f))
        }
        if (open) Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Open the **Outlook** app and tap your profile picture.", "Tap **Settings**, then your work account.",
                "Turn on **Sync calendars**.", "Come back here. It shows up under Outlook within a minute.")
                .forEachIndexed { i, s -> Text(bold("${i + 1}. $s"), style = SubStyle.copy(color = Palette.body)) }
        }
    }
}

/** **text** → bold, for short help lists. */
fun bold(s: String) = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Palette.text)) { append(part) } else append(part) }
}

// ---------- Watchface (RULES A5) ----------
@Composable
fun WatchfaceScreen(m: AppModel) {
    ScreenTitle("Watchface")
    val face = m.link.face
    val enabled = m.link.watch != null && face?.installed == true
    if (!enabled) Text("Connect your watch with the watch face on it to change its colours.", style = SubStyle, modifier = Modifier.padding(bottom = 18.dp))
    else Spacer(Modifier.height(12.dp))
    val alpha = if (enabled) 1f else .45f
    LookCard("solaris", "Solaris", "Colours that follow the sun through your day.", m, enabled, alpha, wide = true)
    Spacer(Modifier.height(12.dp))
    listOf("amber", "mint", "violet", "rose").chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            pair.forEach { LookCard(it, lookName(it), null, m, enabled, alpha, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun LookCard(id: String, name: String, text: String?, m: AppModel, enabled: Boolean, alpha: Float, modifier: Modifier = Modifier, wide: Boolean = false) {
    val selected = m.colourway == id
    val sample = remember { SampleDay.meetings() }   // a busy sample day, so every colourway shows its arcs
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    // Same frosted glass as Today's buttons: a light grey tint with a hairline edge (accent edge when chosen).
    val box = modifier.fillMaxWidth().clip(shape).background(Color(0xFF3A3A3E).copy(alpha = .45f))
        .border(BorderStroke(1.dp, if (selected) accent.copy(alpha = .45f) else Color.White.copy(alpha = .07f)), shape)
        .clickable(enabled = enabled, role = Role.RadioButton) { m.pickColourway(id) }
    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = BodyStyle.copy(color = Palette.text.copy(alpha = alpha)), fontWeight = FontWeight.Medium)
            if (selected) { Spacer(Modifier.width(6.dp)); Icon(Icons.check, null, tint = accent, modifier = Modifier.size(18.dp)) }
        }
    }
    if (wide) Row(box.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        WatchFacePreview(id, sample, 120.dp, atMinute = SampleDay.SAMPLE_MINUTE) { if (enabled) m.pickColourway(id) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { title(); text?.let { Text(it, style = HintStyle) } }
    } else Column(box.padding(top = 15.dp, bottom = 13.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WatchFacePreview(id, sample, 136.dp, atMinute = SampleDay.SAMPLE_MINUTE) { if (enabled) m.pickColourway(id) }
        title()
    }
}

// ---------- Settings (RULES A6) ----------
@Composable
fun SettingsScreen(m: AppModel, onRunSetup: () -> Unit) {
    ScreenTitle("Settings")
    val context = LocalContext.current
    Text("Calen-Time ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}", style = SubStyle)
    Row(Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.buzz, null, tint = Palette.muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Meeting buzzes", style = HintStyle)
    }
    val any = m.buzz[AppPrefs.Buzz.START] == true || m.buzz[AppPrefs.Buzz.MIDDLE] == true || m.buzz[AppPrefs.Buzz.END] == true
    Rows {
        SwitchRow("2 min before it starts", "One short buzz", m.buzz[AppPrefs.Buzz.START] == true) { m.setBuzz(AppPrefs.Buzz.START, it) }
        RowDivider()
        SwitchRow("Halfway through", "One long buzz · meetings of 15 min or more", m.buzz[AppPrefs.Buzz.MIDDLE] == true) { m.setBuzz(AppPrefs.Buzz.MIDDLE, it) }
        RowDivider()
        SwitchRow("5 min before it ends", "Two short buzzes · meetings of 10 min or more", m.buzz[AppPrefs.Buzz.END] == true) { m.setBuzz(AppPrefs.Buzz.END, it) }
        RowDivider()
        SwitchRow("Buzz in Do Not Disturb", "Buzzes still come through when your watch is silenced", m.buzz[AppPrefs.Buzz.DND] == true, enabled = any) { m.setBuzz(AppPrefs.Buzz.DND, it) }
    }
    SectionLabel("Sync")
    Rows {
        InfoRow("Automatic", "Every 15 minutes, and as soon as a meeting changes")
        RowDivider()
        InfoRow("What's sent", "Yesterday to tomorrow, from the calendars you picked. Private events go without title or place.")
    }
    SectionLabel("About")
    Rows {
        InfoRow("Run setup again", null) { onRunSetup() }
        RowDivider()
        InfoRow("Privacy", "Your calendar stays on your phone and watch. Calen-Time has no internet access.")
        RowDivider()
        InfoRow("Fonts", "Google Sans Flex and Anta, under the SIL Open Font License")
    }
}

@Composable
private fun InfoRow(label: String, hint: String?, onClick: (() -> Unit)? = null) = Column(
    Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 18.dp, vertical = 14.dp),
) {
    Text(label, style = BodyStyle)
    hint?.let { Text(it, style = HintStyle) }
}
