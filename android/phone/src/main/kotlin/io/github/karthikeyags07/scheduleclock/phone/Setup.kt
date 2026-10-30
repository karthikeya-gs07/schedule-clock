package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STEPS = 5

/** Where the install step is (RULES A1): not started, sent and waiting for the watch, or the "didn't show up" help. */
private enum class Install { IDLE, SENDING, CONFIRM, NO_PROMPT }

/**
 * First-run setup (RULES A1): welcome → calendar access → choose calendars → put the face on the watch
 * (confirm on the watch) → done. A back button sits beside the main button on steps 2–4.
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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp).semantics { contentDescription = "Step ${step + 1} of $STEPS" },
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEPS) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) LocalAccent.current else Palette.divider)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> {
                    Hero { WatchFacePreview(m.colourway, m.meetings, 230.dp) }
                    ScreenTitle("Your day, around the clock")
                    Text("Calen-Time draws your meetings as arcs on your watch, so you see how busy the next 12 hours are at a glance.", style = SubStyle)
                    Bullets(Icons.calendar to "Reads the calendars you choose on this phone",
                        Icons.watch to "Installs the watch face on your Galaxy Watch",
                        Icons.buzz to "Buzzes before, during and near the end of each meeting")
                }
                1 -> {
                    ScreenTitle("Allow calendar access")
                    Text("Calen-Time reads today's and tomorrow's meetings so it can send them to your watch.", style = SubStyle)
                    Bullets(Icons.lock to "Only sent to your own watch, never to a server",
                        Icons.lock to "Private events go without their title or place",
                        Icons.calendar to "You choose which calendars on the next step")
                    if (asked && !m.hasPermission) {
                        Spacer(Modifier.height(28.dp))
                        Banner("Access is off", "Without it the watch shows an empty day. You can allow it in Android settings later.")
                    }
                }
                2 -> {
                    ScreenTitle("Choose calendars")
                    Text("Turn on the ones you want on your watch. You can change this any time.", style = SubStyle)
                    CalendarsList(m, onAllow)
                }
                3 -> {
                    ScreenTitle("Put it on your watch")
                    Text(
                        when {
                            m.link.watch == null -> "Your watch isn't connected. Open Galaxy Wearable and check it's paired and nearby."
                            face == null -> "First, get the Calen-Time watch app: tap below, then tap Install on your watch."
                            !face.supported -> "Your watch needs Wear OS 6 or newer. Check for a watch update in Galaxy Wearable."
                            else -> "Calen-Time installs its watch face on your ${m.link.watch?.name ?: "watch"}. You'll confirm it on the watch."
                        }, style = SubStyle,
                    )
                    when (install) {
                        Install.IDLE -> Hero { WatchFacePreview(m.colourway, m.meetings, 200.dp) }
                        Install.SENDING, Install.CONFIRM -> {
                            LinearProgressIndicator(
                                progress = { if (install == Install.SENDING) .6f else 1f },
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = LocalAccent.current, trackColor = Palette.divider, drawStopIndicator = {},
                            )
                            Text(if (install == Install.SENDING) "Sending to your watch…" else "Installed. Confirm on your watch.", style = SubStyle)
                            if (install == Install.CONFIRM) {
                                Hero { WatchFacePreview(m.colourway, m.meetings, 200.dp) }
                                Text("Use Calen-Time as your watch face?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.text,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Install.NO_PROMPT -> {
                            Spacer(Modifier.height(14.dp))
                            Banner("No prompt on your watch?", "Calen-Time is already on your watch. You can pick it yourself:", info = true)
                            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(20.dp)).background(Palette.surface).padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("Wake your watch, then **touch and hold** the watch face.",
                                    "Swipe to **Calen-Time**. Not there? Swipe to the end, tap **+ Add** and pick it from the list.",
                                    "Tap it to make it your watch face.").forEachIndexed { i, s -> Text(bold("${i + 1}. $s"), style = SubStyle.copy(color = Palette.body)) }
                            }
                            Text("Still nothing? Keep the watch close to your phone with Bluetooth on, then send it again.", style = SubStyle)
                        }
                    }
                }
                else -> {
                    Hero { WatchFacePreview(m.colourway, m.meetings, 230.dp) }
                    ScreenTitle("You're set")
                    Text("Your meetings are on your watch and stay in sync on their own. Change colours and buzzes in the app any time.", style = SubStyle)
                }
            }
        }
        // Actions: the main one (with Back beside it), any others below.
        Column(Modifier.padding(top = 18.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canBack) Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Palette.surface2).clickable(role = Role.Button) { back() }
                        .semantics { contentDescription = "Back" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.back, null, tint = Palette.text) }
                val main = Modifier.weight(1f)
                when (step) {
                    0 -> PrimaryButton("Get started", main) { step = 1 }
                    1 -> if (m.hasPermission) PrimaryButton("Continue", main) { step = 2 }
                         else PrimaryButton("Allow access", main) { asked = true; onAllow() }
                    2 -> PrimaryButton("Continue", main) { step = 3 }
                    3 -> when (install) {
                        Install.IDLE -> if (m.link.watch != null && face == null) PrimaryButton("Get the watch app", main) { m.getWatchApp() }
                            else PrimaryButton("Install on watch", main, enabled = watchOk) { sendToWatch() }
                        Install.SENDING -> PrimaryButton("Installing", main, busy = true) {}
                        Install.CONFIRM -> PrimaryButton("I've confirmed it", main) { scope.launch { m.refreshWatch().join(); install = Install.IDLE; step = 4 } }
                        Install.NO_PROMPT -> PrimaryButton("It's on my watch now", main) { scope.launch { m.refreshWatch().join(); install = Install.IDLE; step = 4 } }
                    }
                    else -> PrimaryButton("Open Calen-Time", main) { m.finishSetup() }
                }
            }
            when {
                step == 1 && asked && !m.hasPermission -> TextButton("Skip for now") { step = 2 }
                step == 3 && install == Install.IDLE -> TextButton("Do this later") { step = 4 }
                step == 3 && install == Install.CONFIRM -> TextButton("It didn't show up") { install = Install.NO_PROMPT }
                step == 3 && install == Install.NO_PROMPT -> {
                    TonalButton("Send it again", modifier = Modifier.fillMaxWidth()) { sendToWatch() }
                    TextButton("Do this later") { install = Install.IDLE; step = 4 }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Hero(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), contentAlignment = Alignment.Center) { content() }

@Composable
private fun Bullets(vararg items: Pair<ImageVector, String>) = Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    items.forEach { (icon, text) ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(20.dp).padding(top = 1.dp))
            Text(text, style = SubStyle.copy(color = Palette.body))
        }
    }
}
