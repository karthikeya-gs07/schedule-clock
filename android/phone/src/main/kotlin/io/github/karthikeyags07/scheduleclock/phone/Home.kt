package io.github.karthikeyags07.scheduleclock.phone

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
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
    const val DESIGN_WIDTH = 393f          // the frame's size; the watch block scales to fit the screen
    const val DESIGN_HEIGHT = 852f
}

private val COLOURWAYS = Looks.IDS

/**
 * Today and Watchface share one stage (RULES A2, A5), so moving between them is one animation:
 *
 * - **Today:** the live face inside a watch, the sync pill (1) and sync button (2) under it, the watch status (7)
 *   and About (6) buttons at the top, and the drawer of today's meetings (5) under the floating navigation (4).
 * - **Watchface:** the watch glides to the middle, the drawer slides away and the buttons dissolve. Tabs above the
 *   watch split the colourways into Solids and Gradients; swipe left and right to see each one in the tab on the
 *   busy sample day; **Use watch face** sends the one shown to the watch.
 *
 * [watchMode] picks the screen; the change animates both ways.
 */
@Composable
fun HomeScreen(m: AppModel, haze: HazeState, watchMode: Boolean, onWatchStatus: () -> Unit, onAbout: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Home.background)) {
        // Design px → dp: by width, or by height on a short screen (small phones, a tablet's left pane), so the
        // watch, buttons and drawer always fit; the block is then centred across the width.
        val s = minOf(maxWidth.value / Home.DESIGN_WIDTH, maxHeight.value / Home.DESIGN_HEIGHT)
        fun d(v: Float) = (v * s).dp
        val left = (maxWidth - d(Home.DESIGN_WIDTH)) / 2
        val faceSize = d(234.3f)
        val pillTop = d(477f)
        val drawerTop = d(559f)
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        // 0 = Today, 1 = Watchface; everything below follows this one value.
        val p by animateFloatAsState(if (watchMode) 1f else 0f, tween(650, easing = FastOutSlowInEasing), label = "stage")
        val pager = rememberPagerState(initialPage = COLOURWAYS.indexOf(m.colourway).coerceAtLeast(0)) { COLOURWAYS.size }
        // Back on Today (or when the colourway changes there), the watch shows the colourway in use.
        LaunchedEffect(watchMode, m.colourway) {
            val inUse = COLOURWAYS.indexOf(m.colourway).coerceAtLeast(0)
            if (!watchMode && pager.currentPage != inUse) pager.animateScrollToPage(inUse)
        }
        val shown = if (watchMode) COLOURWAYS[pager.currentPage] else m.colourway
        // The tab the face on show belongs to, and the pages under it (Solids come first, then Gradients).
        val gradients = Looks.of(COLOURWAYS[pager.currentPage]).gradient
        val group = COLOURWAYS.indices.filter { Looks.of(COLOURWAYS[it]).gradient == gradients }
        LaunchedEffect(watchMode, shown) { m.previewColourway = if (watchMode) shown else null }
        // The underglow's colours, left to right: one colour, or a gradient face's colours (Solaris: its day). Five
        // stops either way, each easing on its own, so changing face blends the glow smoothly.
        val glowStops = (Looks.of(shown).palette?.map { Color(it) } ?: if (shown == "solaris") SOLARIS_COLOURS else null)
            ?.let { pal -> List(5) { pal[it * (pal.size - 1) / 4] } } ?: List(5) { accentFor(shown) }
        val glows = glowStops.mapIndexed { i, c -> animateColorAsState(c, tween(400), label = "glow$i").value }

        // Where the face sits: under the top buttons on Today, in the middle (above the name and button) on Watchface.
        val faceTopHome = d(158.8f)
        // Centred in the room between the tabs (status bar + 72 dp) and the dots, name and button (~300 dp).
        val tabsBottom = statusBar + 72.dp
        val faceTopCentre = tabsBottom + (maxHeight - tabsBottom - 300.dp - faceSize) / 2
        val dy = lerp(0.dp, faceTopCentre - faceTopHome, p)

        // Everything but the drawer is the "page" the drawer and the floating nav blur.
        Box(Modifier.fillMaxSize().hazeSource(haze, zIndex = 0f)) {
            // A soft glow of the colourway rising from the bottom of the page (the background colour stays). On
            // Watchface it's fainter and sits lower, so the face is the brightest thing on screen.
            // For a gradient face the glow carries its colours across, faded by the same round falloff.
            Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawBehind {
                val k = 1f - .55f * p
                drawRect(Brush.horizontalGradient(glows))
                drawRect(Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = .22f * k), Color.Black.copy(alpha = .08f * k), Color.Transparent),
                    center = Offset(size.width / 2, size.height * (.86f + .2f * p)), radius = size.width * (1.1f - .2f * p),
                ), blendMode = BlendMode.DstIn)
            })
            // The watch stays still (it only moves between Today and Watchface); only the face inside it swipes.
            WatchPhoto(Modifier.offset(x = left + d(29f), y = d(27f) + dy).size(d(334.5f), d(498.8f)))
            val sample = remember { SampleDay.meetings() }
            val live = !watchMode
            val meetings = if (live) m.meetings else sample
            val at = if (live && !m.demo) null else SampleDay.SAMPLE_MINUTE
            // Glow: a blurred copy of the face at rest (48 dp of room so the blur isn't cut square); hidden mid-swipe.
            if (!pager.isScrollInProgress && kotlin.math.abs(pager.currentPageOffsetFraction) < .01f) {
                val glowPad = 48.dp
                Box(Modifier.offset(x = left + d(79.2f) - glowPad, y = d(158.8f) + dy - glowPad).size(faceSize + glowPad * 2)
                    .graphicsLayer { renderEffect = BlurEffect(18.dp.toPx(), 18.dp.toPx(), TileMode.Decal); alpha = .7f; scaleX = 1.04f; scaleY = 1.04f },
                    contentAlignment = Alignment.Center) { WatchFacePreview(COLOURWAYS[pager.settledPage], meetings, faceSize, atMinute = at) }
            }
            // The faces, one per colourway, sliding through the watch's round screen. The band is full width, so a
            // swipe anywhere across the watch works; drawing is clipped to the screen.
            HorizontalPager(
                pager,
                Modifier.offset(y = d(158.8f) + dy).fillMaxWidth().height(faceSize).clip(CentredCircle),
                userScrollEnabled = false,   // the page-wide swipe layer below drives it
                // On Watchface the faces either side are loaded ahead, so a swipe never shows one still loading.
                beyondViewportPageCount = if (watchMode) 1 else 0,
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(faceSize)) {
                        WatchFacePreview(COLOURWAYS[page], meetings, faceSize, interactive = live, atMinute = at)
                        // On Watchface the face is a picture: swipes go to the pager, not into the face.
                        if (!live) Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
                    }
                }
            }
        }

        // ---- Today's controls: they dissolve away on Watchface ----
        val todayAlpha = (1f - p * 2f).coerceIn(0f, 1f)
        if (todayAlpha > 0f) {
            // (7) watch status and (6) About (frosted, like the pill and sync button)
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 44.dp, start = left + d(31f), end = left + d(31f)).graphicsLayer { alpha = todayAlpha },
                horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton("Watch status", haze, onWatchStatus) {
                    Icon(Icons.clock, null, tint = Palette.text, modifier = Modifier.size(24.dp))
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 13.dp).size(9.dp).clip(CircleShape)
                        .background(Color(0xFF3A3A3E)).padding(1.5.dp).clip(CircleShape).background(watchColour(m)))
                }
                RoundButton("About Calen-Time", haze, onAbout) { Icon(Icons.info, null, tint = Palette.text, modifier = Modifier.size(24.dp)) }
            }
            // (1) sync pill and (2) sync button
            Row(Modifier.fillMaxWidth().offset(y = pillTop).graphicsLayer { alpha = todayAlpha }, horizontalArrangement = Arrangement.Center) {
                SyncPill(m, haze, Modifier.width(184.dp))
                Spacer(Modifier.width(18.dp))
                SyncButton(m, haze)
            }
        }

        // ---- (5) the meetings drawer: slides off the bottom on Watchface, back up on Today ----
        // Pulled up, the drawer stops just under the top buttons (status bar + 44 + 57 + 16 dp).
        MeetingsDrawer(m, haze, collapsedTop = drawerTop, expandedTop = statusBar + 117.dp, height = maxHeight,
            away = lerp(0.dp, maxHeight, p))

        // ---- Watchface: swipe anywhere on the page to change the face (the pager band is the same width) ----
        if (watchMode && p > .98f) {
            val scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize().draggable(
                rememberDraggableState { delta -> pager.dispatchRawDelta(-delta) },
                Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val page = pager.currentPage + pager.currentPageOffsetFraction
                    val target = when {
                        velocity < -600 -> kotlin.math.floor(page).toInt() + 1
                        velocity > 600 -> kotlin.math.ceil(page).toInt() - 1
                        else -> kotlin.math.round(page).toInt()
                    }.coerceIn(group.first(), group.last())   // a swipe stays inside the tab
                    scope.launch { pager.animateScrollToPage(target) }
                },
            ))
        }

        // ---- Watchface: Solids / Gradients tabs above the watch ----
        val pickAlpha = ((p - .45f) / .55f).coerceIn(0f, 1f)
        if (pickAlpha > 0f) {
            val scope = rememberCoroutineScope()
            LookTabs(gradients, haze,
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 28.dp)
                    .graphicsLayer { alpha = pickAlpha; translationY = (1f - pickAlpha) * -16.dp.toPx() },
            ) { toGradients ->
                if (toGradients != gradients && watchMode) scope.launch {
                    // Open the tab on the colourway in use if it's there, otherwise on its first.
                    val pages = COLOURWAYS.indices.filter { Looks.of(COLOURWAYS[it]).gradient == toGradients }
                    pager.animateScrollToPage(pages.firstOrNull { COLOURWAYS[it] == m.colourway } ?: pages.first())
                }
            }
        }

        // ---- Watchface: the page dots, the colourway's name and Use watch face ----
        if (pickAlpha > 0f) Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 28.dp + 80.dp + 24.dp)
                .graphicsLayer { alpha = pickAlpha; translationY = (1f - pickAlpha) * 24.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { i ->
                    val id = COLOURWAYS[i]
                    val on = i == pager.currentPage
                    val dot = Modifier.height(6.dp).width(if (on) 18.dp else 6.dp).clip(CircleShape)
                    // Selected, Solaris's dot is its day in miniature (night blue → dawn → noon gold → sunset coral → violet)
                    // and a Palette theme's runs through its colours; unselected, every dot is the same plain grey.
                    val palette = Looks.of(id).palette
                    if (id == "solaris" && on) Box(dot.background(SOLARIS_DOT))
                    else if (palette != null && on) Box(dot.background(Brush.horizontalGradient(palette.map { Color(it) })))
                    else Box(dot.background(if (on) accentFor(id) else Color.White.copy(alpha = .25f)))
                }
            }
            Text(lookName(shown), style = TitleStyle)
            Text(Looks.of(shown).line, style = SubStyle, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            val face = m.link.face
            val canUse = m.link.watch != null && face?.installed == true
            // A plain frosted button, like Today's; it reads "On your watch" for the colourway in use.
            val inUse = shown == m.colourway
            Row(
                Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).frosted(haze, RoundedCornerShape(26.dp))
                    .clickable(enabled = !inUse && canUse && watchMode, role = Role.Button) { m.pickColourway(shown) }
                    .padding(horizontal = 28.dp).graphicsLayer { alpha = if (inUse || canUse) 1f else .45f },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (inUse) Icon(Icons.check, null, tint = Palette.text, modifier = Modifier.size(18.dp))
                Text(if (inUse) "On your watch" else "Use watch face", style = BodyStyle.copy(fontWeight = FontWeight.Medium))
            }
            if (!canUse) Text("Connect your watch with Calen-Time on it to change faces.", style = HintStyle, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Two frosted tabs, Solids and Gradients; the lit pill slides to the chosen one. */
@Composable
internal fun LookTabs(gradients: Boolean, haze: HazeState, modifier: Modifier, onSelect: (Boolean) -> Unit) {
    val at by animateFloatAsState(if (gradients) 1f else 0f, tween(300, easing = FastOutSlowInEasing), label = "lookTab")
    val tabWidth = 112.dp
    // A fixed frosted tint (not a live blur), so the tabs look the same folded and unfolded, whatever is behind them.
    Box(modifier.height(44.dp).width(tabWidth * 2 + 8.dp).clip(RoundedCornerShape(22.dp)).frostCard(RoundedCornerShape(22.dp)).padding(4.dp)) {
        Box(Modifier.offset(x = tabWidth * at).width(tabWidth).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .12f)))
        Row {
            listOf("Solids" to false, "Gradients" to true).forEach { (label, g) ->
                val on = g == gradients
                Box(Modifier.width(tabWidth).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Tab) { onSelect(g) }.semantics { selected = on },
                    contentAlignment = Alignment.Center) {
                    Text(label, style = BodyStyle.copy(fontWeight = FontWeight.Medium, color = if (on) Palette.text else Palette.muted))
                }
            }
        }
    }
}

/** The page colour of Today, Watchface and setup. */
internal val StageBackground = Color(0xFF191919)

/**
 * The watch photo (Figma): 40 %, darkened with a multiply on its own pixels, strap ends faded with a mask (so
 * nothing square shows over the glow). It fills its box; the design's box is 334.5 × 498.8.
 */
@Composable
internal fun WatchPhoto(modifier: Modifier) = Image(
    painterResource(R.drawable.watch_body), null, contentScale = ContentScale.FillBounds,
    colorFilter = ColorFilter.tint(StageBackground, BlendMode.Multiply),
    modifier = modifier
        .graphicsLayer { alpha = .4f; compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .2f to Color.Black, .78f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn)
        },
)

/** A circle the height of its box, centred: the watch's round screen inside the full-width face pager. */
private val CentredCircle = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val r = size.height / 2
    addOval(androidx.compose.ui.geometry.Rect(Offset(size.width / 2, r), r))
}

private val SOLARIS_COLOURS = listOf(Color(0xFF3F66E6), Color(0xFFE88AB2), Color(0xFFFFCC5C), Color(0xFFFF7438), Color(0xFFA957DC))
private val SOLARIS_DOT = Brush.horizontalGradient(SOLARIS_COLOURS)

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
private fun MeetingsDrawer(m: AppModel, haze: HazeState, collapsedTop: androidx.compose.ui.unit.Dp, expandedTop: androidx.compose.ui.unit.Dp,
                           height: androidx.compose.ui.unit.Dp, away: androidx.compose.ui.unit.Dp = 0.dp) {
    val awayPx = with(LocalDensity.current) { away.toPx() }
    val density = LocalDensity.current
    val down = with(density) { collapsedTop.toPx() }
    val up = with(density) { expandedTop.toPx() }
    // Where the drawer's top is, in px; set directly while dragging (so the list's own scrolling can hand its
    // drags over), animated to up or down when let go.
    var top by remember { mutableFloatStateOf(down) }
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun settle(velocity: Float = 0f, target: Float? = null) {
        settling?.cancel()
        val to = target ?: when {
            velocity < -800 -> up
            velocity > 800 -> down
            else -> if (top < (up + down) / 2) up else down
        }
        settling = scope.launch { animate(top, to, velocity, spring(dampingRatio = .85f, stiffness = 500f)) { v, _ -> top = v } }
    }
    fun dragBy(dy: Float): Float {   // moves the drawer, returns how much of dy it used
        settling?.cancel()
        val next = (top + dy).coerceIn(up, down)
        val used = next - top
        top = next
        return used
    }
    // Drags anywhere on the drawer move it: pulling up raises it before the list scrolls; pulling down with the
    // list at its top lowers it. A fling settles it open or closed.
    val drag = remember(up, down) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0 && top > up) Offset(0f, dragBy(available.y)) else Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0 && source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (top > up + 1 && top < down - 1) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }
    val now = m.now()
    val zone = ZoneId.systemDefault()
    val endOfToday = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val left = m.meetings.filter { !it.allDay && it.end > now && it.begin < endOfToday }

    Column(
        Modifier.offset { IntOffset(0, (top + awayPx).roundToInt()) }.fillMaxWidth().height(height - expandedTop)
            .nestedScroll(drag)
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
            // Strong blur of the watch and glow behind it; also a layer the floating nav blurs in turn.
            .hazeSource(haze, zIndex = 1f)
            .hazeEffect(haze, HazeStyle(Home.background, HazeTint(Color(0xFF080808).copy(alpha = .68f)), blurRadius = 48.dp, noiseFactor = .08f)),
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
                            dragBy(amount)
                        },
                    )
                }
                .clickable(role = Role.Button) { settle(target = if (top > (up + down) / 2) up else down) }
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

/**
 * One meeting in the drawer: the title on the left (up to two lines) and its time on the right, with the place
 * (or the private / clash note) under the title.
 */
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
            Text(meeting.title.ifEmpty { "Busy" }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = BodyStyle.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium))
            // The place, on its own line under the title, when the event has one.
            if (meeting.where.isNotEmpty()) Text(meeting.where, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = SubStyle.copy(color = Palette.muted.copy(alpha = .75f)))   // quieter than the title and time
            val note = listOfNotNull("Private on the watch".takeIf { meeting.private }, "Double booked".takeIf { clash }).joinToString(" · ")
            if (note.isNotEmpty()) Text(note, maxLines = 1, style = SubStyle.copy(color = if (clash) Color(0xFFFF9D82) else Palette.muted))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 1.dp)) {
            Text(t(meeting.begin), style = BodyStyle.copy(fontSize = 15.sp, color = if (live) LocalAccent.current else Palette.text))
            Text(t(meeting.end), style = SubStyle)
        }
    }
}

// ---------- (7) Watch status ----------
private data class StatusItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val value: String, val ok: Boolean?)

/**
 * What's going on with the watch (RULES A3): a frosted card that drops in under the clock button, with a
 * one-line verdict, the four things that have to be right, and the one fix that applies.
 */
@Composable
fun WatchStatusPanel(m: AppModel, haze: HazeState, onAllow: () -> Unit, onInstall: () -> Unit, onClose: () -> Unit) {
    val face = m.link.face
    val watch = m.link.watch
    val amber = Color(0xFFFFB547)
    val (title, line, colour) = when {
        watch == null -> Triple("Watch not connected", "Open Galaxy Wearable and check your watch is paired and nearby.", Palette.muted)
        !m.hasPermission -> Triple("Calendar access is off", "Allow it so your meetings can reach the watch.", amber)
        face == null -> Triple("Calen-Time isn't on your watch", "Get the watch app to put the face on.", amber)
        !face.supported -> Triple("Your watch needs an update", "Calen-Time needs Wear OS 6 or newer. Check for an update in Galaxy Wearable.", amber)
        !face.installed -> Triple("The face isn't on your watch", "Put it on, then confirm on the watch.", amber)
        !face.active -> Triple("Not your watch face right now", "Touch and hold your watch face, then swipe to Calen-Time and tap it.", amber)
        else -> Triple("All good", "Your meetings sync to your watch on their own.", Palette.ok)
    }
    val items = listOf(
        StatusItem(Icons.watch, "Connection", if (watch != null) "Connected" else "Not connected", watch != null),
        StatusItem(Icons.info, "Calen-Time on the watch",
            when { watch == null -> "Unknown"; face == null -> "Not installed"; !face.supported -> "Needs Wear OS 6"; else -> "Installed" },
            if (watch == null) null else face != null && face.supported),
        StatusItem(Icons.look, "Watch face",
            when { face == null || !face.supported -> "—"; !face.installed -> "Not on the watch"; face.active -> "In use · ${lookName(face.colourway ?: m.colourway)}"; else -> "On the watch, not in use" },
            if (face == null || !face.supported) null else face.active),
        StatusItem(Icons.calendar, "Calendar access", if (m.hasPermission) "Allowed" else "Off", m.hasPermission),
    )

    // Drops in: the scrim fades, the card fades and grows from just under the button.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = .8f, stiffness = 420f)) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }.background(Color(0x8C000000))
        .clickable(interactionSource = null, indication = null, onClick = onClose)) {
        Column(
            Modifier.statusBarsPadding().padding(top = 44.dp + 57.dp + 12.dp, start = 16.dp, end = 16.dp).fillMaxWidth()
                .graphicsLayer {
                    val k = .92f + .08f * shown.value; scaleX = k; scaleY = k
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.12f, 0f)
                }
                .clip(RoundedCornerShape(32.dp))
                .hazeEffect(haze, HazeStyle(Home.background, HazeTint(Color(0xFF1C1C20).copy(alpha = .8f)), blurRadius = 40.dp, noiseFactor = .1f))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(32.dp))
                .clickable(interactionSource = null, indication = null) {}
                .padding(22.dp),
        ) {
            // Verdict
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(colour.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(if (colour == Palette.ok) Icons.check else if (watch == null) Icons.watch else Icons.warn, null, tint = colour, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = BodyStyle.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium))
                    Text(watch?.name ?: "No watch", style = SubStyle)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center) { Icon(Icons.close, null, tint = Palette.muted, modifier = Modifier.size(20.dp)) }
            }
            Text(line, style = SubStyle.copy(color = Palette.body), modifier = Modifier.padding(top = 14.dp, bottom = 18.dp))

            // The four things that have to be right
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .05f))) {
                items.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .06f)))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = Palette.text, modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = BodyStyle)
                            Text(item.value, style = HintStyle)
                        }
                        Box(Modifier.size(22.dp).clip(CircleShape).background(when (item.ok) { true -> Palette.ok.copy(alpha = .18f); false -> amber.copy(alpha = .18f); null -> Color.White.copy(alpha = .06f) }),
                            contentAlignment = Alignment.Center) {
                            when (item.ok) {
                                true -> Icon(Icons.check, null, tint = Palette.ok, modifier = Modifier.size(14.dp))
                                false -> Text("!", color = amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                null -> {}
                            }
                        }
                    }
                }
            }

            // The one fix that applies
            val fix: Pair<String, () -> Unit>? = when {
                watch == null -> null
                !m.hasPermission -> "Allow calendar access" to onAllow
                face == null -> "Get the watch app" to { m.getWatchApp(); onClose() }
                !face.supported -> null
                !face.installed -> "Put the face on your watch" to { onClose(); onInstall() }
                else -> null
            }
            if (fix != null) { Spacer(Modifier.height(18.dp)); PrimaryButton(fix.first, Modifier.fillMaxWidth(), onClick = fix.second) }
            // When the watch isn't reachable or the face can't get there: shortcuts to the phone's connection settings,
            // to reconnect the watch (Bluetooth) or check the network it can also use (Wi-Fi).
            if (watch == null || face == null || !face.installed) {
                val context = androidx.compose.ui.platform.LocalContext.current
                fun open(action: String) = runCatching { context.startActivity(android.content.Intent(action)) }
                Spacer(Modifier.height(if (fix != null) 10.dp else 18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsShortcut(Icons.bluetooth, "Bluetooth", Modifier.weight(1f)) { open(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS) }
                    SettingsShortcut(Icons.wifi, "Wi-Fi", Modifier.weight(1f)) { open(android.provider.Settings.ACTION_WIFI_SETTINGS) }
                }
            }
        }
    }
}

// ---------- (6) About ----------
/**
 * About Calen-Time (RULES A2): what it is, how to read the face, buzzes, privacy and credits. In the look of Today
 * and setup: the grey stage with the colourway's glow, a large heading, close-ups of the face in your colourway
 * with the part's name and meaning under each, round icons beside the buzzes, and a frosted button at the end.
 */
@Composable
fun AboutScreen(colourway: String, onBack: () -> Unit, onRunSetup: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    Box(Modifier.fillMaxSize().stagePage(LocalAccent.current)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp).navigationBarsPadding()) {
            // The title, and close (×) on the far right, level with Today's info button (status bar + 44 dp, 57 dp
            // round): the same folded and in the unfolded right pane.
            Row(Modifier.fillMaxWidth().padding(top = 32.dp).height(57.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Calen-Time", style = TitleStyle, modifier = Modifier.weight(1f))
                Box(Modifier.size(57.dp).clip(CircleShape).frostCard(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.close, null, tint = Palette.text, modifier = Modifier.size(22.dp))
                }
            }
            Text("Version $version", style = SubStyle, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            Text("Your day, around the clock. Calen-Time turns your watch face into a picture of the next 12 hours: every meeting is an arc on the ring, so one glance tells you how busy you are, what's next and how much time you have before it.",
                style = PageBody)

            // Each part of the face as a close-up of the face in your colourway, on the sample day (plus a late call,
            // so the inner line has something on it); the arcs at 11:10 so one meeting is under way.
            val day = remember { SampleDay.meetings() + SampleDay.lateCall() }
            PageHeading("Reading the face")
            FacePart(colourway, "arcs", day, 670, "Arcs",
                "Each meeting is an arc at its time on the 12-hour ring. The part already gone is dimmer.")
            FacePart(colourway, "now", day, SampleDay.SAMPLE_MINUTE, "The dot",
                "Now. The faint trail behind it fades over the last two hours.")
            FacePart(colourway, "lane", day, SampleDay.SAMPLE_MINUTE, "Inner line",
                "Meetings later today that don't fit the next 12 hours yet. They move onto the ring 30 minutes after their slot frees up.")
            FacePart(colourway, "clash", day, SampleDay.SAMPLE_MINUTE, "Two thin stripes",
                "A double booking: two meetings at the same time. When a third joins, one stripe splits into two thin lines; at four or more, both do.")
            FacePart(colourway, "private", day, SampleDay.SAMPLE_MINUTE, "Striped arc",
                "A private meeting. Its title and place never leave your phone.")
            FacePart(colourway, "middle", day, SampleDay.SAMPLE_MINUTE, "Middle",
                "The time, and what's next. Tap an hour on the ring to see what's on then; tap the details to go back.")
            IconItem(Icons.calendar, "All-day events", "Not shown on the watch, and they never count as a double booking: your meetings that day look as usual.")

            ItemDivider(Modifier.padding(top = 8.dp))
            PageHeading("Buzzes")
            IconItem(Icons.buzz, "2 min before", "One short buzz to wrap up and join.")
            IconItem(Icons.clock, "Halfway", "One long buzz, for meetings of 15 minutes or more.")
            IconItem(Icons.buzz, "5 min before the end", "Two short buzzes, for meetings of 10 minutes or more.")
            ItemDivider(Modifier.padding(top = 8.dp))
            PageHeading("Privacy")
            IconItem(Icons.lock, "Phone to watch only", "Your calendar goes from your phone to your own watch and nowhere else. Calen-Time has no internet access, no accounts, no ads and no tracking.")
            ItemDivider(Modifier.padding(top = 8.dp))
            PageHeading("Credits")
            IconItem(Icons.info, "Fonts", "Google Sans Flex and Anta, under the SIL Open Font License.")
            ItemDivider(Modifier.padding(top = 8.dp))
            PageHeading("Share")
            IconItem(Icons.share, "Tell a friend", "Send the Play Store link to anyone with a Wear OS watch.")
            ShareButton(Modifier.fillMaxWidth().padding(bottom = 12.dp))

            Spacer(Modifier.height(20.dp))
            RunSetupButton(onRunSetup, Modifier.fillMaxWidth())
        }
    }
}


/** One part of the face: a close-up on top, its name and what it means below. */
@Composable
private fun FacePart(colourway: String, detail: String, day: List<Meeting>, at: Int, title: String, text: String, divider: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(24.dp))) {
            FaceDetail(colourway, detail, day, at)
        }
        Text(title, style = BodyStyle.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = 14.dp))
        Text(text, style = ItemTextStyle, modifier = Modifier.padding(top = 2.dp))
    }
    if (divider) ItemDivider(Modifier.padding(bottom = 22.dp))
}

/** Calen-Time's Play Store page (live once the app is published there; PRD §6.5). */
const val PLAY_LINK = "https://play.google.com/store/apps/details?id=io.github.karthikeyags07.scheduleclock"

/** Opens Android's share sheet with the Play Store link (About). */
@Composable
private fun ShareButton(modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).frostCard(RoundedCornerShape(26.dp)).clickable(role = Role.Button) {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "Calen-Time")
                .putExtra(android.content.Intent.EXTRA_TEXT, "Calen-Time puts your meetings on your Wear OS watch face: $PLAY_LINK")
            context.startActivity(android.content.Intent.createChooser(send, "Share Calen-Time"))
        },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.share, null, tint = Palette.text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Share Calen-Time", style = BodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
    }
}

/** A frosted button that opens one of the phone's settings screens (watch status). */
@Composable
private fun SettingsShortcut(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) = Row(
    modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).frostCard(RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onClick),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
) {
    Icon(icon, null, tint = Palette.text, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, style = BodyStyle.copy(fontWeight = FontWeight.Medium))
}

/** Run setup again: a full-width frosted button (About and Settings). */
@Composable
fun RunSetupButton(onClick: () -> Unit, modifier: Modifier = Modifier) = Row(
    modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).frostCard(RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onClick),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
) {
    Icon(Icons.sync, null, tint = Palette.text, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(10.dp))
    Text("Run setup again", style = BodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
}
