package io.github.karthikeyags07.scheduleclock.phone

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** The Today screen's own colours (Figma "Calen-Time", node 28:3). */
private object Home {
    val background = Color(0xFF191919)
    const val DESIGN_WIDTH = 393f          // the frame's width; the watch block scales with the screen width
}

/**
 * Today (RULES A2, redesigned 2026-10-31 from Figma): the live face inside a watch, the sync pill (1) and
 * sync button (2) under it, the watch status (7) and About (6) buttons at the top, and the drawer of
 * upcoming meetings (5) that pulls up from under the floating navigation (4).
 */
@Composable
fun HomeScreen(m: AppModel, haze: HazeState, onWatchStatus: () -> Unit, onAbout: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Home.background)) {
        val accent = LocalAccent.current
        val s = maxWidth.value / Home.DESIGN_WIDTH          // design px → dp on this screen
        fun d(v: Float) = (v * s).dp
        val faceSize = d(234.3f)
        val pillTop = d(477f)
        val drawerTop = d(559f)

        // Everything but the drawer is the "page" the drawer and the floating nav blur.
        Box(Modifier.fillMaxSize().hazeSource(haze, zIndex = 0f)) {
        // A soft glow of the colourway rising from the bottom of the page (the background colour stays).
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(
                listOf(accent.copy(alpha = .22f), accent.copy(alpha = .08f), Color.Transparent),
                center = Offset(size.width / 2, size.height * .86f), radius = size.width * 1.1f,
            ))
        })

        // ---- The watch: photo at 40 %, faded top and bottom, with the live face glowing in its screen ----
        Box(Modifier.offset(x = d(29f), y = d(27f)).size(d(334.5f), d(498.8f))) {
            Image(
                painterResource(R.drawable.watch_body), null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .4f }
                    .drawWithContent { drawContent(); drawRect(Home.background, blendMode = BlendMode.Multiply) },
            )
        }
        listOf(45.81f to false, 429.1f to true).forEach { (y, flip) ->
            Box(
                Modifier.offset(x = d(101.5f), y = d(y)).size(d(189f), d(77f)).background(
                    Brush.verticalGradient(if (flip) listOf(Color.Transparent, Home.background) else listOf(Home.background, Color.Transparent)),
                ),
            )
        }
        Box(Modifier.offset(x = d(79.2f), y = d(158.8f)).size(faceSize)) {
            // Glow: a blurred copy of the face behind it, so the arcs light up the watch around them.
            Box(Modifier.graphicsLayer {
                renderEffect = BlurEffect(18.dp.toPx(), 18.dp.toPx(), TileMode.Decal); alpha = .7f; scaleX = 1.04f; scaleY = 1.04f
            }) { WatchFacePreview(m.colourway, m.meetings, faceSize) }
            WatchFacePreview(m.colourway, m.meetings, faceSize, interactive = true)
        }
        }

        // ---- (7) watch status and (6) About (frosted, like the pill and sync button) ----
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 44.dp, start = d(31f), end = d(31f)), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundButton("Watch status", haze, onWatchStatus) {
                Icon(Icons.watch, null, tint = Palette.text, modifier = Modifier.size(24.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 13.dp).size(9.dp).clip(CircleShape)
                    .background(Color(0xFF3A3A3E)).padding(1.5.dp).clip(CircleShape).background(watchColour(m)))
            }
            RoundButton("About Calen-Time", haze, onAbout) { Icon(Icons.info, null, tint = Palette.text, modifier = Modifier.size(24.dp)) }
        }

        // ---- (1) sync pill and (2) sync button ----
        Row(Modifier.fillMaxWidth().offset(y = pillTop), horizontalArrangement = Arrangement.Center) {
            SyncPill(m, haze, Modifier.width(184.dp))
            Spacer(Modifier.width(18.dp))
            SyncButton(m, haze)
        }

        // ---- (5) the meetings drawer ----
        // Pulled up, the drawer stops just under the top buttons (status bar + 44 + 57 + 16 dp).
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        MeetingsDrawer(m, haze, collapsedTop = drawerTop, expandedTop = statusBar + 117.dp, height = maxHeight)
    }
}

/** Frosted glass over the page: the watch and glow show through, blurred and lightly tinted, with a hairline edge. */
private fun Modifier.frosted(haze: HazeState, shape: androidx.compose.ui.graphics.Shape) = this
    .hazeEffect(haze, HazeStyle(Home.background, HazeTint(Color(0xFF3A3A3E).copy(alpha = .45f)), blurRadius = 22.dp, noiseFactor = .12f))
    .border(1.dp, Color.White.copy(alpha = .07f), shape)

private fun watchColour(m: AppModel): Color = when {
    !m.linkChecked -> Palette.faint
    m.link.watch == null -> Palette.faint
    m.link.face?.active == true -> Palette.ok
    else -> Color(0xFFFFB547)   // connected, but something to fix
}

@Composable
private fun RoundButton(label: String, haze: HazeState, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) = Box(
    Modifier.size(57.dp).clip(CircleShape).frosted(haze, CircleShape).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label },
    contentAlignment = Alignment.Center, content = content,
)

/** (1) Last sync on the first line, the watch on the second; dimmed while a sync runs. */
@Composable
private fun SyncPill(m: AppModel, haze: HazeState, modifier: Modifier) {
    val dim by animateFloatAsState(if (m.syncing) .4f else 1f, tween(250), label = "dim")
    Column(
        modifier.height(57.dp).clip(RoundedCornerShape(40.dp)).frosted(haze, RoundedCornerShape(40.dp)).padding(horizontal = 20.dp)
            .graphicsLayer { alpha = dim },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                m.syncing -> "Syncing…"
                m.lastSync == 0L -> "Not synced yet"
                System.currentTimeMillis() - m.lastSync < 60_000 -> "Synced just now"
                else -> "Synced ${DateUtils.getRelativeTimeSpanString(m.lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
            },
            style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1,
        )
        Text(
            m.link.watch?.let { "${it.name.substringBefore(" (")} · Connected" } ?: "Watch not connected",
            style = HintStyle.copy(fontSize = 12.sp), maxLines = 1,
        )
    }
}

/** (2) Syncs now; the icon spins until the sync finishes. */
@Composable
private fun SyncButton(m: AppModel, haze: HazeState) {
    val spin = remember { Animatable(0f) }
    LaunchedEffect(m.syncing) {
        if (m.syncing) while (true) spin.animateTo(spin.value + 360f, tween(900, easing = LinearEasing))
        else if (spin.value % 360f != 0f) spin.animateTo((spin.value / 360f).toInt() * 360f + 360f, tween(400))
    }
    val enabled = m.link.watch != null && m.hasPermission && !m.syncing
    RoundButton("Sync now", haze, { if (enabled) m.syncNow() }) {
        Icon(Icons.sync, null, tint = if (m.link.watch != null && m.hasPermission) Palette.text else Palette.faint,
            modifier = Modifier.size(24.dp).rotate(spin.value))
    }
}

/**
 * (5) Upcoming meetings, in a drawer that rests under the sync pill and pulls up over the watch. The floating
 * navigation sits on top of its lower part, so the list leaves room for it.
 */
@Composable
private fun MeetingsDrawer(m: AppModel, haze: HazeState, collapsedTop: androidx.compose.ui.unit.Dp, expandedTop: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current
    val down = with(density) { collapsedTop.toPx() }
    val up = with(density) { expandedTop.toPx() }
    val top = remember { Animatable(down) }
    val scope = rememberCoroutineScope()
    fun settle(velocity: Float = 0f) = scope.launch {
        val target = when {
            velocity < -800 -> up
            velocity > 800 -> down
            else -> if (top.value < (up + down) / 2) up else down
        }
        top.animateTo(target, spring(dampingRatio = .85f, stiffness = 500f))
    }
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val endOfToday = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val left = m.meetings.filter { !it.allDay && it.end > now && it.begin < endOfToday }

    Column(
        Modifier.offset { IntOffset(0, top.value.roundToInt()) }.fillMaxWidth().height(height - expandedTop)
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
            // Strong blur of the watch and glow behind it; also a layer the floating nav blurs in turn.
            .hazeSource(haze, zIndex = 1f)
            .hazeEffect(haze, HazeStyle(Home.background, HazeTint(Color(0xFF080808).copy(alpha = .8f)), blurRadius = 48.dp, noiseFactor = .08f)),
    ) {
        // Handle and heading: drag here (or tap) to pull the drawer up and down.
        var dragVelocity = 0f
        Column(
            Modifier.fillMaxWidth()
                .pointerInput(down, up) {
                    detectVerticalDragGestures(
                        onDragEnd = { settle(dragVelocity) },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragVelocity = amount * 60f
                            scope.launch { top.snapTo((top.value + amount).coerceIn(up, down)) }
                        },
                    )
                }
                .clickable(role = Role.Button) { scope.launch { top.animateTo(if (top.value > (up + down) / 2) up else down, spring(.85f, 500f)) } }
                .semantics { contentDescription = "Meetings. Pull up to see them all" }
                .padding(top = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.faint))
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 22.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Today's calendar", style = BodyStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium))
                if (left.isNotEmpty()) Text("${left.size} meeting${if (left.size == 1) "" else "s"}", style = HintStyle)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 132.dp)) {
            if (left.isEmpty()) Text(
                if (!m.hasPermission) "Allow calendar access to see your meetings here." else "Nothing else today. The watch shows a clear day.",
                style = SubStyle, modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            )
            left.forEachIndexed { i, meeting ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                DrawerMeeting(meeting, live = meeting.begin <= now, clash = left.any { it !== meeting && it.begin < meeting.end && meeting.begin < it.end },
                    color = m.calendars.firstOrNull { it.id == meeting.calendarId }?.color?.let { Color(it) } ?: Palette.muted)
            }
        }
    }
}

private val DRAWER_TIME = java.time.format.DateTimeFormatter.ofPattern("h:mm a")

/** One meeting in the drawer: the title, time and place (or the private / clash note), with room around it. */
@Composable
private fun DrawerMeeting(meeting: Meeting, live: Boolean, clash: Boolean, color: Color) {
    val zone = ZoneId.systemDefault()
    fun t(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).format(DRAWER_TIME)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.padding(top = 7.dp).size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(meeting.title.ifEmpty { "Busy" }, style = BodyStyle.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium))
            Text("${t(meeting.begin)} – ${t(meeting.end)}", style = SubStyle.copy(fontSize = 15.sp, color = if (live) LocalAccent.current else Palette.body))
            val note = listOfNotNull(
                meeting.where.takeIf { it.isNotEmpty() && !meeting.private },
                "Private on the watch".takeIf { meeting.private },
                "Double booked".takeIf { clash },
            ).joinToString(" · ")
            if (note.isNotEmpty()) Text(note, style = SubStyle.copy(color = if (clash) Color(0xFFFF9D82) else Palette.muted))
        }
    }
}

// ---------- (7) Watch status ----------
/** What's going on with the watch, with the one fix that applies (RULES A3), shown over Today. */
@Composable
fun WatchStatusPanel(m: AppModel, onAllow: () -> Unit, onInstall: () -> Unit, onClose: () -> Unit) {
    val face = m.link.face
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.statusBarsPadding().padding(top = 92.dp, start = 16.dp, end = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFF2F2F2F)).clickable(enabled = false) {}.padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(watchColour(m)))
                Spacer(Modifier.width(10.dp))
                Text(m.link.watch?.name ?: "No watch connected", style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(Modifier.height(14.dp))
            val rows = buildList {
                add("Connection" to if (m.link.watch != null) "Connected" else "Not connected")
                add("Calen-Time on the watch" to when { m.link.watch == null -> "Unknown"; face == null -> "Not installed"; !face.supported -> "Needs Wear OS 6"; else -> "Installed" })
                add("Watch face" to when { face == null || !face.supported -> "—"; !face.installed -> "Not on the watch"; face.active -> "In use · ${lookName(face.colourway ?: m.colourway)}"; else -> "On the watch, not in use" })
                add("Calendar access" to if (m.hasPermission) "Allowed" else "Off")
            }
            rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, style = SubStyle); Text(v, style = SubStyle.copy(color = Palette.text))
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                !m.hasPermission -> PrimaryButton("Allow calendar access", Modifier.fillMaxWidth(), onClick = onAllow)
                m.link.watch == null -> Text("Open Galaxy Wearable and check your watch is paired and nearby.", style = SubStyle)
                face == null -> PrimaryButton("Install on watch", Modifier.fillMaxWidth()) { m.getWatchApp(); onClose() }
                !face.supported -> Text("Check for a watch update in Galaxy Wearable.", style = SubStyle)
                !face.installed -> PrimaryButton("Put the face on your watch", Modifier.fillMaxWidth()) { onClose(); onInstall() }
                !face.active -> Text("Touch and hold your watch face, then swipe to Calen-Time and tap it.", style = SubStyle)
                else -> Text("All good. Your meetings sync on their own.", style = SubStyle)
            }
        }
    }
}

// ---------- (6) About ----------
/** About Calen-Time: what it is, how to read the face, privacy, and credits. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    Column(Modifier.fillMaxSize().background(Palette.screen).statusBarsPadding()) {
        Box(Modifier.padding(start = 12.dp, top = 8.dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            Icon(Icons.back, null, tint = Palette.text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
            ScreenTitle("Calen-Time")
            Text("Version $version", style = SubStyle)
            Spacer(Modifier.height(18.dp))
            Text("Your day, around the clock. Calen-Time turns your watch face into a picture of the next 12 hours: every meeting is an arc on the ring, so one glance tells you how busy you are, what's next and how much time you have before it.",
                style = BodyStyle.copy(color = Palette.body))
            AboutSection("Reading the face", listOf(
                "Arcs" to "Each meeting is an arc at its time on the 12-hour ring. The part already gone is dimmer.",
                "The white dot" to "Now. The faint trail behind it fades over the last two hours.",
                "Inner line" to "Meetings later today that don't fit the next 12 hours yet. They move onto the ring 30 minutes after their slot frees up.",
                "Coral stripes" to "A double booking: two meetings at the same time.",
                "Grey stripes" to "A private meeting. Its title and place never leave your phone.",
                "Middle" to "The time, and what's next. Tap an hour on the ring to see what's on then; tap the details to go back.",
            ))
            AboutSection("Colourways", listOf(
                "Amber, Mint, Violet, Rose" to "Pick one on the Watchface tab; your watch changes in a few seconds.",
                "Solaris" to "The ring takes the colour of the light at each hour: dawn rose, noon gold, sunset coral, night indigo, from your sunrise and sunset.",
            ))
            AboutSection("Buzzes", listOf(
                "2 min before" to "One short buzz to wrap up and join.",
                "Halfway" to "One long buzz, for meetings of 15 minutes or more.",
                "5 min before the end" to "Two short buzzes, for meetings of 10 minutes or more.",
            ))
            AboutSection("Privacy", listOf(
                "Phone to watch only" to "Your calendar goes from your phone to your own watch and nowhere else. Calen-Time has no internet access, no accounts, no ads and no tracking.",
            ))
            AboutSection("Credits", listOf(
                "Fonts" to "Google Sans Flex and Anta, under the SIL Open Font License.",
            ))
        }
    }
}

@Composable
private fun AboutSection(title: String, items: List<Pair<String, String>>) {
    SectionLabel(title)
    Rows {
        items.forEachIndexed { i, (k, v) ->
            if (i > 0) RowDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(k, style = BodyStyle.copy(fontWeight = FontWeight.Medium))
                Text(v, style = SubStyle, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
