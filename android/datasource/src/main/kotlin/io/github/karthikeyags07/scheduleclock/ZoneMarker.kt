package io.github.karthikeyags07.scheduleclock

import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The second time zone (RULES R13), chosen in the phone app's Settings: the face draws a triangle on the ring at
 * that place's time, pointing clockwise when it's ahead of the watch's own time and anticlockwise when behind.
 * VALUE carries the difference in minutes plus [BASE]; no data (nothing drawn) when none is chosen or it's the same.
 * Worked out here from the time-zone rules, so daylight saving on either side is followed.
 */
object ZoneMarker {
    const val BASE = 2000f

    fun offsetMinutes(context: Context): Int? {
        val id = PhoneSettings.secondZone(context) ?: return null
        val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return null
        val now = ZonedDateTime.now()
        val diff = (zone.rules.getOffset(now.toInstant()).totalSeconds - now.offset.totalSeconds) / 60
        return diff.takeIf { it != 0 }
    }

    fun data(context: Context): ComplicationData {
        val diff = offsetMinutes(context) ?: return NoDataComplicationData()
        val label = PlainComplicationText.Builder(PhoneSettings.secondZone(context) ?: "").build()
        return RangedValueComplicationData.Builder(BASE + diff, 0f, 2 * BASE, label).setText(label).build()
    }
}
