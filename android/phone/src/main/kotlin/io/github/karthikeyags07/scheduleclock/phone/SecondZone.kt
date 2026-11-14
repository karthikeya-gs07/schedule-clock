package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The second time zone (RULES R13): one place besides the watch's own, shown on the face as a triangle on the ring
 * at that place's time. The cities people most often keep a second clock for, by name; each maps to its time zone,
 * so daylight saving follows on its own.
 */
object Cities {
    val ALL = listOf(
        "Auckland" to "Pacific/Auckland", "Sydney" to "Australia/Sydney", "Tokyo" to "Asia/Tokyo",
        "Seoul" to "Asia/Seoul", "Shanghai" to "Asia/Shanghai", "Hong Kong" to "Asia/Hong_Kong",
        "Singapore" to "Asia/Singapore", "Bangkok" to "Asia/Bangkok", "Kolkata" to "Asia/Kolkata",
        "Dubai" to "Asia/Dubai", "Moscow" to "Europe/Moscow", "Istanbul" to "Europe/Istanbul",
        "Cairo" to "Africa/Cairo", "Johannesburg" to "Africa/Johannesburg", "Berlin" to "Europe/Berlin",
        "Paris" to "Europe/Paris", "Amsterdam" to "Europe/Amsterdam", "London" to "Europe/London",
        "Lisbon" to "Europe/Lisbon", "São Paulo" to "America/Sao_Paulo", "New York" to "America/New_York",
        "Toronto" to "America/Toronto", "Chicago" to "America/Chicago", "Mexico City" to "America/Mexico_City",
        "Denver" to "America/Denver", "Los Angeles" to "America/Los_Angeles", "Vancouver" to "America/Vancouver",
        "Honolulu" to "Pacific/Honolulu",
    )

    fun name(id: String?) = ALL.firstOrNull { it.second == id }?.first

    /** Minutes that place is ahead of the phone's own time now (negative when behind), or null for none. */
    fun minutes(id: String?): Int? = id?.let {
        val now = ZonedDateTime.now()
        runCatching { (ZoneId.of(it).rules.getOffset(now.toInstant()).totalSeconds - now.offset.totalSeconds) / 60 }.getOrNull()
    }?.takeIf { it != 0 }

    /** "+5:30" / "−8" against the phone's own time now. */
    fun difference(id: String): String {
        val now = ZonedDateTime.now()
        val m = (ZoneId.of(id).rules.getOffset(now.toInstant()).totalSeconds - now.offset.totalSeconds) / 60
        if (m == 0) return "Same time"
        val sign = if (m > 0) "+" else "−"; val a = kotlin.math.abs(m)
        return sign + (a / 60) + (if (a % 60 != 0) ":" + (a % 60).toString().padStart(2, '0') else "") + " h"
    }
}

/** Settings row: the chosen city (or None); tapping opens the picker. */
@Composable
fun SecondZoneItem(m: AppModel) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { picking = true }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Second time zone", style = ItemTitle)
            Text("A triangle on the ring at that place's time, pointing forward if it's ahead and back if it's behind",
                style = ItemTextStyle, modifier = Modifier.padding(top = 1.dp))
        }
        Text(Cities.name(m.secondZone) ?: "None", style = ItemTextStyle.copy(color = Palette.text), modifier = Modifier.padding(start = 12.dp))
    }
    if (picking) CalenPopup("Second time zone", { picking = false }, "Pick one place to show on the ring.") {
        Spacer(Modifier.height(16.dp))
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            PopupRows(Cities.ALL.size + 1) { i ->
                val (name, id) = if (i == 0) "None" to "" else Cities.ALL[i - 1]
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { m.chooseSecondZone(id.ifEmpty { null }); picking = false }
                    .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = BodyStyle.copy(color = if ((m.secondZone ?: "") == id) LocalAccent.current else Palette.text))
                    if (id.isNotEmpty()) Text(Cities.difference(id), style = HintStyle)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Cancel", Modifier.fillMaxWidth()) { picking = false }
    }
}
