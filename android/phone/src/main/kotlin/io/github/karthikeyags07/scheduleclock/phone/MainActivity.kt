package io.github.karthikeyags07.scheduleclock.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.ripple
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
        // Demo mode (sample day, for screenshots) only in debug builds, and nothing of it is saved.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) { model.startDemo(); model.setupDone = true }
        setContent { CalenTimeApp(model, ::openAppSettings) }
    }

    override fun onResume() {
        super.onResume()
        model.reload()
        model.refreshWatch()
        if (model.setupDone && CalendarSync.hasPermission(this) && !model.demo) SyncWorker.schedule(this)
    }

    private fun openAppSettings() =
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.home), WATCHFACE("Watchface", Icons.look), CALENDARS("Calendars", Icons.calendar), SETTINGS("Settings", Icons.settings)
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
    val haze = rememberHazeState()   // the page behind the floating nav (and Today's drawer), for the frosted blur
    var watchStatus by rememberSaveable { mutableStateOf(false) }
    val install: () -> Unit = { setupStep = 3; m.restartSetup() }

    CompositionLocalProvider(LocalAccent provides animatedAccent(m.colourway), LocalTextStyle provides BodyStyle) {   // Google Sans Flex everywhere
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.screen)) {
            // Unfolded (Fold 7/8 inner screen, tablets): two panes, Today on the left and the other pages on the right.
            // Only when the screen is tall enough too: a landscape phone (or a short window) stays one pane.
            val wide = maxWidth >= 600.dp && maxHeight >= 600.dp
            val workPage: @Composable (Tab, HazeState) -> Unit = { shown, h ->
                Column(Modifier.fillMaxSize().hazeSource(h, zIndex = 0f).stagePage(LocalAccent.current, dark = true).statusBarsPadding()
                    // Clear of the floating nav (28 + 80 dp above the system bar) with room to spare.
                    .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 150.dp)) {
                    when (shown) {
                        Tab.CALENDARS -> { PageTitle("Calendars"); CalendarsList(m, allow) }
                        else -> SettingsScreen(m) { setupStep = 0; m.restartSetup() }
                    }
                }
            }
            when {
                !m.setupDone -> SetupFlow(m, setupStep, allow)   // full-bleed; it handles the system bars itself
                about && !wide -> { BackHandler { about = false }; AboutScreen(m.colourway, onBack = { about = false }) { about = false; setupStep = 0; m.restartSetup() } }
                wide -> {
                    // Right pane: Watchface (a 2-column grid), Calendars or Settings, with its own three-tab nav.
                    val right = if (tab == Tab.TODAY) Tab.WATCHFACE else tab
                    val rightHaze = rememberHazeState()
                    BackHandler(enabled = right != Tab.WATCHFACE || watchStatus) { if (watchStatus) watchStatus = false else tab = Tab.WATCHFACE }
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            HomeScreen(m, haze, watchMode = false, onWatchStatus = { watchStatus = true }, onAbout = { about = true })
                            // The watch status card opens over Today's pane only, under its clock button.
                            if (watchStatus) WatchStatusPanel(m, haze, allow, install) { watchStatus = false }
                        }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .06f)))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            AnimatedContent(
                                targetState = right,
                                transitionSpec = {
                                    val forward = targetState.ordinal > initialState.ordinal
                                    val ease = tween<IntOffset>(420, easing = FastOutSlowInEasing)
                                    (slideInHorizontally(ease) { w -> if (forward) w else -w } togetherWith
                                        slideOutHorizontally(ease) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(420)))
                                },
                                label = "rightPane",
                            ) { shown -> if (shown == Tab.WATCHFACE) WatchfaceGrid(m, rightHaze) else workPage(shown, rightHaze) }
                            // About opens over the right pane, with a close button; Today stays on the left.
                            androidx.compose.animation.AnimatedVisibility(about, enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut()) {
                                BackHandler { about = false }
                                AboutScreen(m.colourway, onBack = { about = false }) { about = false; setupStep = 0; m.restartSetup() }
                            }
                            if (!about) NavBar(right, rightHaze, null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
                                tabs = listOf(Tab.WATCHFACE, Tab.CALENDARS, Tab.SETTINGS)) { tab = it }
                        }
                    }
                }
                else -> {
                    BackHandler(enabled = tab != Tab.TODAY || watchStatus) { if (watchStatus) watchStatus = false else tab = Tab.TODAY }
                    // Pages slide in from the side they sit on in the nav bar (RULES §12 A10). Today and Watchface are
                    // one stage (same content key), so between them the stage's own animation plays instead (A2, A5).
                    AnimatedContent(
                        targetState = tab,
                        contentKey = { if (it == Tab.TODAY || it == Tab.WATCHFACE) "stage" else it.name },
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            val ease = tween<IntOffset>(420, easing = FastOutSlowInEasing)
                            (slideInHorizontally(ease) { w -> if (forward) w else -w } togetherWith
                                slideOutHorizontally(ease) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(420)))
                        },
                        label = "tabs",
                    ) { shown ->
                        if (shown == Tab.TODAY || shown == Tab.WATCHFACE)
                            HomeScreen(m, haze, watchMode = shown == Tab.WATCHFACE, onWatchStatus = { watchStatus = true }, onAbout = { about = true })
                        else workPage(shown, haze)
                    }
                    NavBar(tab, haze, m.previewColourway.takeIf { tab == Tab.WATCHFACE }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp)) { tab = it }
                    if (watchStatus) WatchStatusPanel(m, haze, allow, install) { watchStatus = false }
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
private fun NavBar(current: Tab, haze: HazeState, preview: String?, modifier: Modifier, tabs: List<Tab> = Tab.entries, onSelect: (Tab) -> Unit) {
    // While browsing Watchface, the selected icon takes the colour of the colourway on show.
    val selectedTint by animateColorAsState(preview?.let(::accentFor) ?: LocalAccent.current, tween(300), label = "navTint")
    Row(
        modifier.width((tabs.size * 77 + 24).dp).height(80.dp).clip(RoundedCornerShape(40.dp))
            // Frosted glass: whatever scrolls or sits behind shows through, blurred and lightly tinted.
            .hazeEffect(haze, HazeStyle(Color(0xFF191919), HazeTint(Color(0xFF2C2C30).copy(alpha = .55f)), blurRadius = 26.dp, noiseFactor = .12f))
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(40.dp))
            .clickable(enabled = false) {}.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            val selected = t == current
            // The selected icon has a soft glow of its colour behind it (fades in and out as tabs change).
            val glow by animateFloatAsState(if (selected) 1f else 0f, tween(300), label = "navGlow")
            Box(
                Modifier.weight(1f).height(68.dp)
                    // The whole cell is the tap area; the touch ripple is a circle round the icon.
                    .clickable(interactionSource = null, indication = ripple(bounded = false, radius = 28.dp), role = Role.Tab) { onSelect(t) }
                    .semantics { contentDescription = t.label; this.selected = selected }
                    .drawBehind {
                        if (glow > 0f) drawCircle(Brush.radialGradient(
                            listOf(selectedTint.copy(alpha = .16f * glow), selectedTint.copy(alpha = .05f * glow), Color.Transparent),
                            center = center, radius = 22.dp.toPx(),
                        ), radius = 22.dp.toPx())
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(t.icon, null, tint = if (selected) selectedTint else Palette.muted, modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun Toast(text: String, modifier: Modifier) = Text(
    text, color = Color(0xFF111111), fontSize = 14.sp, fontFamily = GoogleSans,
    modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8E8EA)).padding(horizontal = 16.dp, vertical = 12.dp),
)
