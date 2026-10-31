package io.github.karthikeyags07.scheduleclock.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope

/**
 * The phone app (PRD §6.5, RULES §12): first-run setup, then Today, Calendars, Watchface and Settings.
 * Calendar sync to the watch runs in the background ([SyncWorker]); the face on the watch is handled
 * through [WatchLink].
 */
class MainActivity : ComponentActivity() {
    private lateinit var model: AppModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        model = AppModel(applicationContext, lifecycleScope)
        setContent { CalenTimeApp(model, ::openAppSettings) }
    }

    override fun onResume() {
        super.onResume()
        model.reload()
        model.refreshWatch()
        if (model.setupDone && CalendarSync.hasPermission(this)) SyncWorker.schedule(this)
    }

    private fun openAppSettings() =
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.home), CALENDARS("Calendars", Icons.calendar), WATCHFACE("Watchface", Icons.look), SETTINGS("Settings", Icons.settings)
}

@Composable
private fun CalenTimeApp(m: AppModel, openAppSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var setupStep by rememberSaveable { mutableStateOf(0) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) deniedOnce = true
        m.permissionChanged()
    }
    // Android stops asking after a second "Don't allow"; then the button opens the app's settings instead.
    val allow: () -> Unit = { if (deniedOnce && !m.hasPermission) openAppSettings() else permission.launch(Manifest.permission.READ_CALENDAR) }

    var about by rememberSaveable { mutableStateOf(false) }
    var watchStatus by rememberSaveable { mutableStateOf(false) }
    val install: () -> Unit = { setupStep = 3; m.restartSetup() }

    CompositionLocalProvider(LocalAccent provides animatedAccent(m.colourway), LocalTextStyle provides BodyStyle) {   // Google Sans Flex everywhere
        Box(Modifier.fillMaxSize().background(Palette.screen)) {
            when {
                !m.setupDone -> Box(Modifier.statusBarsPadding().navigationBarsPadding()) { SetupFlow(m, setupStep, allow) }
                about -> { BackHandler { about = false }; AboutScreen { about = false } }
                else -> {
                    BackHandler(enabled = tab != Tab.TODAY || watchStatus) { if (watchStatus) watchStatus = false else tab = Tab.TODAY }
                    if (tab == Tab.TODAY) HomeScreen(m, onWatchStatus = { watchStatus = true }, onAbout = { about = true })
                    else Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 132.dp)) {
                        when (tab) {
                            Tab.CALENDARS -> { ScreenTitle("Calendars"); CalendarsList(m, allow) }
                            Tab.WATCHFACE -> WatchfaceScreen(m)
                            else -> SettingsScreen(m) { setupStep = 0; m.restartSetup() }
                        }
                    }
                    NavBar(tab, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp)) { tab = it }
                    if (watchStatus) WatchStatusPanel(m, allow, install) { watchStatus = false }
                }
            }
            m.toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 124.dp)) }
        }
    }
}

/**
 * Floating navigation (Figma (4)): a 332 × 80 pill above every tab. Icons only (RULES §12), the selected one in
 * the accent colour, each a large tap area.
 */
@Composable
private fun NavBar(current: Tab, modifier: Modifier, onSelect: (Tab) -> Unit) = Row(
    modifier.width(332.dp).height(80.dp).clip(RoundedCornerShape(40.dp)).background(Color(0xFF191919))
        .clickable(enabled = false) {}.padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Tab.entries.forEach { t ->
        val selected = t == current
        Box(
            Modifier.weight(1f).height(68.dp).clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Tab) { onSelect(t) }
                .semantics { contentDescription = t.label; this.selected = selected },
            contentAlignment = Alignment.Center,
        ) { Icon(t.icon, null, tint = if (selected) LocalAccent.current else Palette.muted, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun Toast(text: String, modifier: Modifier) = Text(
    text, color = Color(0xFF111111), fontSize = 14.sp, fontFamily = GoogleSans,
    modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8E8EA)).padding(horizontal = 16.dp, vertical = 12.dp),
)
