package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

/** Colours and type, from the phone design prototype (prototype/phone.html). */
object Palette {
    val screen = Color(0xFF0B0B0D)
    val surface = Color(0xFF17181B)
    val surface2 = Color(0xFF202125)
    val divider = Color(0xFF222327)
    val nav = Color(0xFF121316)
    val text = Color(0xFFF2F2F2)
    val body = Color(0xFFC9C9CE)
    val muted = Color(0xFF8E8E93)
    val faint = Color(0xFF5C5E64)
    val onAccent = Color(0xFF1A1206)
    val danger = Color(0xFFFF7A59)
    val ok = Color(0xFF5FD4A0)
}

// Google Sans Flex (SIL Open Font License, licence in assets/licenses): one variable font file, used at
// these weights for all of the app's text. The watch face keeps its own font (Anta) in the previews.
@OptIn(ExperimentalTextApi::class)   // FontVariation: picks the weight from the variable font
private fun sans(weight: Int) = Font(R.font.google_sans_flex, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
val GoogleSans = FontFamily(sans(400), sans(500), sans(600), sans(700))
val TitleStyle = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, color = Palette.text)
val BodyStyle = TextStyle(fontFamily = GoogleSans, fontSize = 15.sp, lineHeight = 21.sp, color = Palette.text)
val HintStyle = TextStyle(fontFamily = GoogleSans, fontSize = 13.sp, lineHeight = 18.sp, color = Palette.muted)
val SubStyle = TextStyle(fontFamily = GoogleSans, fontSize = 14.sp, lineHeight = 20.sp, color = Palette.muted)

/** The app's accent follows the colourway (RULES A7); Solaris takes the colour of the light now. */
val LocalAccent = compositionLocalOf { Color(0xFFFFB547) }

fun accentFor(colourway: String): Color =
    if (colourway == "solaris") sunColour(LocalTime.now().toSecondOfDay() / 60) else Color(Looks.of(colourway).accent)

// Solaris accent: the day's colours (RULES S3) at typical sun times (sunrise ~6:15, noon ~12:00, sunset ~17:45),
// blended in sRGB. The face itself computes the real sun times; this only tints the app.
private val SUN = listOf(0 to 0x4A3CCF, 180 to 0x2F72CF, 294 to 0x3A9AD0, 334 to 0x8F7AD8, 362 to 0xE88AB2,
    389 to 0xFFA86B, 444 to 0xFFCC5C, 554 to 0xFFF06A, 719 to 0xFFF6CC, 824 to 0xFFE08A, 913 to 0xFFBB4D,
    983 to 0xFF983C, 1033 to 0xFF7438, 1063 to 0xFF5257, 1083 to 0xEC4F92, 1108 to 0xA957DC, 1143 to 0x6C5AE8,
    1260 to 0x3F66E6, 1440 to 0x4A3CCF)

private fun sunColour(minute: Int): Color {
    val i = SUN.indexOfFirst { it.first >= minute }.coerceAtLeast(1)
    val (m0, a) = SUN[i - 1]
    val (m1, b) = SUN[i]
    val f = if (m1 > m0) (minute - m0).toFloat() / (m1 - m0) else 0f
    fun ch(v: Int, s: Int) = ((v shr s) and 0xFF)
    fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * f) / 255f
    return Color(mix(16), mix(8), mix(0))
}

// ---------- Icons (stroke paths from the prototype, 24 × 24) ----------
private fun strokeIcon(vararg paths: String, filled: String? = null) = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    paths.forEach {
        addPath(addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
    filled?.let { addPath(addPathNodes(it), fill = SolidColor(Color.White)) }
}.build()

object Icons {
    val home = strokeIcon("M12 3 3 10v11h6v-6h6v6h6V10z")
    val calendar = strokeIcon("M6.5 5h11a3 3 0 0 1 3 3v9.5a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M3.5 10h17M8 3v4M16 3v4")
    val look = strokeIcon("M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z", filled = "M12 3.5a8.5 8.5 0 0 1 0 17z")
    val settings = strokeIcon("M4 7h8.5M17.5 7H20M4 17h2.5M11.5 17H20",   // sliders: a calmer "settings" mark
        "M15 4.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z", "M9 14.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z")
    val sync = strokeIcon("M20 12a8 8 0 0 1-14.3 4.9M4 12A8 8 0 0 1 18.3 7.1M18.5 3v4.3h-4.3M5.5 21v-4.3h4.3")
    val warn = strokeIcon("M12 3 2 20h20zM12 10v4.5M12 17.2v.1")
    val check = strokeIcon("m5 12.5 4.5 4.5L19 7.5")
    val lock = strokeIcon("M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 18v-5a2.5 2.5 0 0 1 2.5-2.5z", "M8 10.5V8a4 4 0 0 1 8 0v2.5")
    val buzz = strokeIcon("M10.5 4h3A2.5 2.5 0 0 1 16 6.5v11a2.5 2.5 0 0 1-2.5 2.5h-3A2.5 2.5 0 0 1 8 17.5v-11A2.5 2.5 0 0 1 10.5 4z", "M4.5 9v6M19.5 9v6")
    val watch = strokeIcon("M12 6a6 6 0 1 1 0 12a6 6 0 1 1 0-12z", "M9 6.5 9.6 3h4.8l.6 3.5M9 17.5l.6 3.5h4.8l.6-3.5M12 9.5V12l1.6 1.2")
    val chevron = strokeIcon("m6 9 6 6 6-6")
    val back = strokeIcon("M19 12H5M11 6l-6 6 6 6")
    val close = strokeIcon("M6 6l12 12M18 6 6 18")
    val clock = strokeIcon("M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z", "M12 7.5V12l3 2")
    val info = strokeIcon("M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z", "M12 11v5.5M12 7.8v.1")
}

// ---------- Shared pieces ----------
@Composable
fun ScreenTitle(text: String) = Text(text, style = TitleStyle, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).frostCard(RoundedCornerShape(20.dp))) { content() }

/**
 * The frosted look of Today's buttons, for cards on the other screens: a light grey tint and a hairline edge.
 * (Over a plain background there's nothing to blur, so it's the tint and edge alone.)
 */
fun Modifier.frostCard(shape: androidx.compose.ui.graphics.Shape, edge: Color = Color.White.copy(alpha = .07f)) =
    background(Color(0xFF3A3A3E).copy(alpha = .45f)).border(1.dp, edge, shape)

@Composable
fun SectionLabel(text: String, trailing: String = "") = Row(
    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(text, style = HintStyle)
    if (trailing.isNotEmpty()) Text(trailing, style = HintStyle)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (enabled) accent else accent.copy(alpha = .4f))
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) { CircularProgressIndicator(Modifier.size(16.dp), color = Palette.onAccent, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Palette.onAccent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun TonalButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) = Row(
    modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(Palette.surface2)
        .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick).padding(horizontal = 22.dp),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
) {
    val color = if (enabled) Palette.text else Palette.text.copy(alpha = .4f)
    if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
    else icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(18.dp)) }
    if (busy || icon != null) Spacer(Modifier.width(8.dp))
    Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
}

@Composable
fun TextButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) = Box(
    modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
        .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(text, color = LocalAccent.current, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }

/** A warning or info banner with one optional action (RULES A3). */
@Composable
fun Banner(title: String, text: String, info: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    val accent = LocalAccent.current
    val tint = if (info) accent else Palette.danger
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(20.dp))
            .background(lerpColor(Palette.surface, tint, if (info) .10f else .12f)).padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (info) Icons.watch else Icons.warn, null, tint = tint, modifier = Modifier.size(22.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BodyStyle, fontWeight = FontWeight.SemiBold)
            Text(text, style = SubStyle.copy(color = Palette.body), modifier = Modifier.padding(top = 2.dp))
            if (action != null) {
                Spacer(Modifier.height(10.dp))
                PrimaryButton(action, Modifier.height(40.dp), onClick = onAction)
            }
        }
    }
}

fun lerpColor(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f)

/** A row with a switch; the whole row toggles it. */
@Composable
fun SwitchRow(label: String, hint: String? = null, checked: Boolean, enabled: Boolean = true, dot: Color? = null, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        dot?.let { Box(Modifier.size(12.dp).clip(CircleShape).background(it)) }
        Column(Modifier.weight(1f)) {
            Text(label, style = BodyStyle.copy(color = if (enabled) Palette.text else Palette.faint))
            hint?.let { Text(it, style = HintStyle) }
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent, checkedThumbColor = Palette.onAccent, checkedBorderColor = accent,
                uncheckedTrackColor = Color(0xFF34353A), uncheckedThumbColor = Palette.muted, uncheckedBorderColor = Color(0xFF34353A),
            ),
        )
    }
}

/** Rows inside one rounded card, with thin dividers. */
@Composable
fun Rows(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(20.dp)).frostCard(RoundedCornerShape(20.dp)),
) { content() }

@Composable
fun RowDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))

@Composable
fun animatedAccent(colourway: String): Color = animateColorAsState(accentFor(colourway), label = "accent").value



// ---------- Page look (About, Calendars, Settings; RULES A9) ----------
// Today's grey stage with the colourway's glow at the top, a large title, 22 sp section headings, and items as a
// round frosted icon beside a name and text, without cards.

/** The grey stage with a soft glow of [glow] from the top. */
fun Modifier.stagePage(glow: Color) = background(StageBackground).drawBehind {
    drawRect(Brush.radialGradient(listOf(glow.copy(alpha = .16f), glow.copy(alpha = .05f), Color.Transparent),
        center = Offset(size.width / 2, 0f), radius = size.width * 1.1f))
}

val PageBody = BodyStyle.copy(color = Palette.body, fontSize = 16.sp, lineHeight = 23.sp)
private val ItemTitle = BodyStyle.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Text(title, style = TitleStyle.copy(fontSize = 34.sp, lineHeight = 40.sp), modifier = Modifier.padding(top = 14.dp))
    subtitle?.let { Text(it, style = SubStyle.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 4.dp)) }
}

@Composable
fun PageHeading(text: String, sub: String? = null) = Column(Modifier.padding(top = 34.dp, bottom = 16.dp)) {
    Text(text, style = TitleStyle.copy(fontSize = 22.sp, lineHeight = 28.sp))
    sub?.let { Text(it, style = SubStyle.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 2.dp)) }
}

/** The round frosted icon of an item: an icon, or a calendar's colour as a dot. */
@Composable
private fun ItemIcon(icon: ImageVector?, dot: Color?, dim: Boolean = false) =
    Box(Modifier.size(42.dp).clip(CircleShape).frostCard(CircleShape), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = if (dim) Palette.faint else Palette.text, modifier = Modifier.size(20.dp))
        dot?.let { Box(Modifier.size(14.dp).clip(CircleShape).background(if (dim) it.copy(alpha = .4f) else it)) }
    }

/** A round frosted icon beside a name and its text. */
@Composable
fun IconItem(icon: ImageVector, title: String, text: String) = Row(
    Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
) {
    ItemIcon(icon, null)
    Column(Modifier.weight(1f).padding(top = 1.dp)) {
        Text(title, style = ItemTitle)
        Text(text, style = PageBody, modifier = Modifier.padding(top = 2.dp))
    }
}

/** An item with a switch: the round icon (or calendar dot), name and optional text, the switch at the end. */
@Composable
fun ToggleItem(title: String, text: String? = null, checked: Boolean, icon: ImageVector? = null, dot: Color? = null,
               enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ItemIcon(icon, dot, dim = !enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = ItemTitle.copy(color = if (enabled) Palette.text else Palette.faint))
            text?.let { Text(it, style = PageBody.copy(fontSize = 15.sp, lineHeight = 21.sp, color = if (enabled) Palette.body else Palette.faint)) }
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent, checkedThumbColor = Palette.onAccent, checkedBorderColor = accent,
                uncheckedTrackColor = Color(0xFF34353A), uncheckedThumbColor = Palette.muted, uncheckedBorderColor = Color(0xFF34353A),
            ),
        )
    }
}
