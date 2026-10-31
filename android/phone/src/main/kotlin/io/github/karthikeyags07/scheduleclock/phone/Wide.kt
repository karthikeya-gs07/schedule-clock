package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Watchface in the wide (unfolded) view (RULES A11): the colourways as a 2-column grid of cards under the Solids /
 * Gradients tabs, each the real face on the sample day with its name, one line and its own Use watch face button.
 * The card of the colourway in use has an edge in the accent and reads On your watch.
 */
@Composable
fun WatchfaceGrid(m: AppModel, haze: HazeState) {
    var gradients by rememberSaveable { mutableStateOf(Looks.of(m.colourway).gradient) }
    val sample = remember { SampleDay.meetings() }
    Column(Modifier.fillMaxSize().hazeSource(haze, zIndex = 0f).stagePage(LocalAccent.current, dark = true).statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            PageTitle("Watchface", "Pick the look for your watch.")
            Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), contentAlignment = Alignment.CenterStart) {
                LookTabs(gradients, haze, Modifier) { gradients = it }
            }
        }
        // A new list per tab, so each opens at its top.
        key(gradients) { LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(Looks.ALL.filter { it.gradient == gradients }, key = { it.id }) { look -> FaceCard(m, look, sample) }
        } }
    }
}

@Composable
private fun FaceCard(m: AppModel, look: Look, sample: List<Meeting>) {
    val inUse = look.id == m.colourway
    val canUse = m.link.watch != null && m.link.face?.installed == true
    val edge = if (inUse) LocalAccent.current else Color.White.copy(alpha = .07f)
    Column(
        Modifier.clip(RoundedCornerShape(24.dp)).frostCard(RoundedCornerShape(24.dp), edge).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WatchFacePreview(look.id, sample, maxWidth * .86f, atMinute = SampleDay.SAMPLE_MINUTE)
        }
        Text(look.name, style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = 12.dp))
        Text(look.line, style = SubStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
                .frostCard(RoundedCornerShape(22.dp))
                .clickable(enabled = !inUse && canUse, role = Role.Button) { m.pickColourway(look.id) }
                .graphicsLayer { alpha = if (inUse || canUse) 1f else .45f },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inUse) Icon(Icons.check, null, tint = Palette.text, modifier = Modifier.size(16.dp).padding(end = 2.dp))
            Text(if (inUse) " On your watch" else "Use watch face", style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
    }
}
