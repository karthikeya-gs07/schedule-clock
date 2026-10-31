package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STEPS = 5

/** Where the install step is (RULES A1): not started, sent and waiting for the watch, or the "didn't show up" help. */
private enum class Install { IDLE, SENDING, CONFIRM, NO_PROMPT }

/**
 * First-run setup (RULES A1), in the app's own look (redesigned 2026-10-31): Today's grey page with the colourway's
 * glow from the bottom, the watch with the live face as the hero, step dots like Watchface's, frosted cards with
 * round icons like the watch status card, and frosted buttons. Welcome → calendar access → choose calendars →
 * put the face on the watch (confirm on the watch) → done. Back sits beside the main button on steps 2–4.
 */
@Composable
fun SetupFlow(m: AppModel, startStep: Int, onAllow: () -> Unit) {
    var step by remember { mutableIntStateOf(startStep) }
    var install by remember { mutableStateOf(Install.IDLE) }
    var asked by remember { mutableStateOf(m.hasPermission) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // While waiting for the watch's prompt, check every 2 s whether the face became the watch face.
    LaunchedEffect(install) {
        while (install == Install.CONFIRM || install == Install.NO_PROMPT) {
            delay(2000)
            m.refreshWatch().join()
            if (m.link.face?.active == true) { install = Install.IDLE; step = 4 }
        }
    }

    val face = m.link.face
    val watchOk = m.link.watch != null && face?.supported == true
    fun sendToWatch() = scope.launch {
        install = Install.SENDING
        val opened = WatchLink.install(context, m.colourway)
        if (!opened) { install = Install.IDLE; m.say("Couldn't reach your watch"); return@launch }
        delay(1500)
        install = Install.CONFIRM
    }
    fun back() {
        when {
            step == 3 && install == Install.NO_PROMPT -> install = Install.CONFIRM
            step == 3 && install == Install.CONFIRM -> install = Install.IDLE
            else -> step = (step - 1).coerceAtLeast(0)
        }
    }
    val canBack = step in 1 until STEPS - 1 && install != Install.SENDING
    val accent = LocalAccent.current

    Box(Modifier.fillMaxSize().background(StageBackground).drawBehind {
        // Today's glow of the colourway, rising from the bottom.
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha = .2f), accent.copy(alpha = .07f), Color.Transparent),
            center = Offset(size.width / 2, size.height * .95f), radius = size.width * 1.1f))
    }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            StepDots(step)
            // Each step fades into the next.
            AnimatedContent(step to install, Modifier.weight(1f), label = "setup",
                transitionSpec = { fadeIn(tween(320, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(200)) },
            ) { (shownStep, shownInstall) ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                    when (shownStep) {
                        0 -> {
                            WatchHero(m, 230.dp)
                            Heading("Your day, around the clock", centred = true)
                            Lead("Your meetings as arcs on your watch, so one glance shows how busy the next 12 hours are.", centred = true)
                            Spacer(Modifier.height(22.dp))
                            IconRows(Icons.calendar to "Reads the calendars you choose on this phone",
                                Icons.watch to "Puts the watch face on your Galaxy Watch",
                                Icons.buzz to "Buzzes before, during and near the end of each meeting")
                        }
                        1 -> {
                            Spacer(Modifier.height(40.dp))
                            Badge(Icons.calendar)
                            Heading("Allow calendar access")
                            Lead("Calen-Time reads today's and tomorrow's meetings so it can show them on your watch.")
                            Spacer(Modifier.height(22.dp))
                            IconRows(Icons.lock to "Only sent to your own watch, never to a server",
                                Icons.lock to "Private events go without their title or place",
                                Icons.calendar to "You choose which calendars on the next step")
                            if (asked && !m.hasPermission) {
                                Spacer(Modifier.height(14.dp))
                                Note(Icons.warn, Color(0xFFFF7A59), "Access is off",
                                    "Without it the watch shows an empty day. You can allow it in Android settings later.")
                            }
                        }
                        2 -> {
                            Spacer(Modifier.height(24.dp))
                            Heading("Choose calendars")
                            Lead("Turn on the ones you want on your watch. You can change this any time.")
                            CalendarsList(m, onAllow)
                        }
                        3 -> when (shownInstall) {
                            Install.IDLE -> {
                                WatchHero(m, 230.dp)
                                Heading("Put it on your watch", centred = true)
                                Lead(when {
                                    m.link.watch == null -> "Your watch isn't connected. Open Galaxy Wearable and check it's paired and nearby."
                                    face == null -> "First, get the Calen-Time watch app: tap below, then tap Install on your watch."
                                    !face.supported -> "Your watch needs Wear OS 6 or newer. Check for a watch update in Galaxy Wearable."
                                    else -> "Calen-Time puts its face on your ${m.link.watch?.name?.substringBefore(" (") ?: "watch"}. You'll confirm it there."
                                }, centred = true)
                            }
                            Install.SENDING -> {
                                WatchHero(m, 230.dp, dim = true)
                                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), color = Palette.text, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Sending to your watch…", style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium))
                                }
                            }
                            Install.CONFIRM -> {
                                WatchHero(m, 230.dp)
                                Heading("Confirm on your watch", centred = true)
                                Lead("Tap Allow on your watch to make Calen-Time your watch face. This page moves on by itself.", centred = true)
                            }
                            Install.NO_PROMPT -> {
                                Spacer(Modifier.height(32.dp))
                                Badge(Icons.watch)
                                Heading("No prompt on your watch?")
                                Lead("Calen-Time is already on your watch. You can pick it yourself:")
                                Spacer(Modifier.height(18.dp))
                                NumberedRows("Wake your watch, then **touch and hold** the watch face.",
                                    "Swipe to **Calen-Time**. Not there? Swipe to the end, tap **+ Add** and pick it.",
                                    "Tap it to make it your watch face.")
                                Text("Still nothing? Keep the watch close to your phone with Bluetooth on, then send it again.",
                                    style = SubStyle, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
                            }
                        }
                        else -> {
                            WatchHero(m, 230.dp)
                            Heading("You're set", centred = true)
                            Lead("Your meetings are on your watch and stay in sync on their own. Change colours and buzzes in the app any time.", centred = true)
                        }
                    }
                }
            }

            // Actions: the main one (with Back beside it), any quieter ones below.
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canBack) Box(
                        Modifier.size(56.dp).clip(CircleShape).frostCard(CircleShape).clickable(role = Role.Button) { back() }
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.back, null, tint = Palette.text, modifier = Modifier.size(22.dp)) }
                    val main = Modifier.weight(1f)
                    when (step) {
                        0 -> SetupButton("Get started", main) { step = 1 }
                        1 -> if (m.hasPermission) SetupButton("Continue", main) { step = 2 }
                             else SetupButton("Allow access", main) { asked = true; onAllow() }
                        2 -> SetupButton("Continue", main) { step = 3 }
                        3 -> when (install) {
                            Install.IDLE -> if (m.link.watch != null && face == null) SetupButton("Get the watch app", main) { m.getWatchApp() }
                                else SetupButton("Install on watch", main, enabled = watchOk) { sendToWatch() }
                            Install.SENDING -> SetupButton("Installing", main, busy = true) {}
                            Install.CONFIRM -> SetupButton("I've confirmed it", main) { scope.launch { m.refreshWatch().join(); install = Install.IDLE; step = 4 } }
                            Install.NO_PROMPT -> SetupButton("It's on my watch now", main) { scope.launch { m.refreshWatch().join(); install = Install.IDLE; step = 4 } }
                        }
                        else -> SetupButton("Open Calen-Time", main) { m.finishSetup() }
                    }
                }
                when {
                    step == 1 && asked && !m.hasPermission -> QuietButton("Skip for now") { step = 2 }
                    step == 3 && install == Install.IDLE -> QuietButton("Do this later") { step = 4 }
                    step == 3 && install == Install.CONFIRM -> QuietButton("It didn't show up") { install = Install.NO_PROMPT }
                    step == 3 && install == Install.NO_PROMPT -> {
                        QuietButton("Send it again") { sendToWatch() }
                        QuietButton("Do this later") { install = Install.IDLE; step = 4 }
                    }
                }
            }
        }
    }
}

/** Step dots, like Watchface's page dots: the current step a wide pill in the accent, done steps brighter. */
@Composable
private fun StepDots(step: Int) = Row(
    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp).semantics { contentDescription = "Step ${step + 1} of $STEPS" },
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
) {
    repeat(STEPS) { i ->
        Box(Modifier.height(6.dp).width(if (i == step) 22.dp else 6.dp).clip(CircleShape).background(when {
            i == step -> LocalAccent.current
            i < step -> Color.White.copy(alpha = .5f)
            else -> Color.White.copy(alpha = .2f)
        }))
    }
}

/** The watch from Today, with the live face in its screen; [width] is the photo's width. Dimmed while sending. */
@Composable
private fun WatchHero(m: AppModel, width: Dp, dim: Boolean = false) {
    val k = width / 334.5f                       // design units → dp, from the Figma watch box
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.size(width, k * 498.8f)) {
            WatchPhoto(Modifier.fillMaxSize())
            Box(Modifier.offset(x = k * 50.2f, y = k * 131.8f).size(k * 234.3f)) {
                WatchFacePreview(m.colourway, m.meetings, k * 234.3f)
                if (dim) Box(Modifier.matchParentSize().clip(CircleShape).background(Color.Black.copy(alpha = .45f)))
            }
        }
    }
}

@Composable
private fun Heading(text: String, centred: Boolean = false) = Text(
    text, style = TitleStyle.copy(fontSize = 30.sp, lineHeight = 36.sp), textAlign = if (centred) TextAlign.Center else TextAlign.Start,
    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
)

@Composable
private fun Lead(text: String, centred: Boolean = false) = Text(
    text, style = BodyStyle.copy(color = Palette.body, fontSize = 16.sp, lineHeight = 23.sp),
    textAlign = if (centred) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth(),
)

/** A big round icon at the top of a step without the watch. */
@Composable
private fun Badge(icon: ImageVector) = Box(
    Modifier.padding(bottom = 18.dp).size(64.dp).clip(CircleShape).frostCard(CircleShape), contentAlignment = Alignment.Center,
) { Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(28.dp)) }

/** Rows with a round icon, in one frosted card, like the watch status card. */
@Composable
private fun IconRows(vararg items: Pair<ImageVector, String>) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).frostCard(RoundedCornerShape(24.dp)),
) {
    items.forEachIndexed { i, (icon, text) ->
        if (i > 0) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .06f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Palette.text, modifier = Modifier.size(19.dp))
            }
            Text(text, style = BodyStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
        }
    }
}

/** Numbered steps, in one frosted card. */
@Composable
private fun NumberedRows(vararg items: String) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).frostCard(RoundedCornerShape(24.dp)),
) {
    items.forEachIndexed { i, text ->
        if (i > 0) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .06f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
                Text("${i + 1}", style = BodyStyle.copy(fontWeight = FontWeight.Medium))
            }
            Text(bold(text), style = BodyStyle.copy(fontSize = 15.sp, color = Palette.body), modifier = Modifier.weight(1f))
        }
    }
}

/** A short note with a coloured icon, in a frosted card. */
@Composable
private fun Note(icon: ImageVector, tint: Color, title: String, text: String) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).frostCard(RoundedCornerShape(24.dp)).padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
    Column(Modifier.weight(1f)) {
        Text(title, style = BodyStyle.copy(fontWeight = FontWeight.Medium))
        Text(text, style = SubStyle)
    }
}

/** The main button: frosted like Today's, a little brighter, full width. */
@Composable
private fun SetupButton(text: String, modifier: Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) = Row(
    modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).frostCard(RoundedCornerShape(28.dp), edge = Color.White.copy(alpha = .12f))
        .background(Color.White.copy(alpha = .06f))
        .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
        .padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
) {
    val colour = if (enabled) Palette.text else Palette.text.copy(alpha = .4f)
    if (busy) { CircularProgressIndicator(Modifier.size(16.dp), color = colour, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
    Text(text, color = colour, fontWeight = FontWeight.Medium, fontSize = 16.sp, fontFamily = GoogleSans)
}

/** A quieter action under the main button. */
@Composable
private fun QuietButton(text: String, onClick: () -> Unit) = Box(
    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
) { Text(text, style = BodyStyle.copy(color = Palette.muted, fontWeight = FontWeight.Medium)) }
