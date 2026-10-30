package io.github.karthikeyags07.scheduleclock

import android.graphics.Color
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Solaris face (RULES §10, S1–S3), ported from the prototype: sunrise, solar noon and sunset for the
 * day (the standard solar formula, NOAA / SunCalc, about a minute accurate, no network), and the colour
 * of the light at any minute, blended in OKLab between colour stops anchored to those times.
 */
object SunColors {
    private const val RAD = PI / 180
    private const val E = RAD * 23.4397
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val DAY_MS = 86_400_000.0

    /** Minutes from local midnight; NaN rise/set on a polar day or night. */
    data class Sun(val rise: Double, val set: Double, val noon: Double, val polarDay: Boolean)

    /** Where the sun is computed for (S1): for now always a guess from the time zone. */
    data class Place(val lat: Double, val lon: Double, val label: String)

    private val TZ_PLACES = mapOf(
        "Asia/Kolkata" to Place(20.59, 78.96, "central India"), "Asia/Calcutta" to Place(20.59, 78.96, "central India"),
        "America/New_York" to Place(40.71, -74.01, "New York"), "America/Chicago" to Place(41.88, -87.63, "Chicago"),
        "America/Denver" to Place(39.74, -104.99, "Denver"), "America/Los_Angeles" to Place(34.05, -118.24, "Los Angeles"),
        "Europe/London" to Place(51.51, -0.13, "London"), "Europe/Berlin" to Place(52.52, 13.40, "Berlin"),
        "Europe/Paris" to Place(48.86, 2.35, "Paris"), "Asia/Dubai" to Place(25.20, 55.27, "Dubai"),
        "Asia/Singapore" to Place(1.35, 103.82, "Singapore"), "Asia/Tokyo" to Place(35.68, 139.69, "Tokyo"),
        "Australia/Sydney" to Place(-33.87, 151.21, "Sydney"),
    )

    fun place(zone: ZoneId = ZoneId.systemDefault()): Place = TZ_PLACES[zone.id]
        ?: Place(30.0, ZonedDateTime.now(zone).offset.totalSeconds / 240.0, "estimated from the time zone")

    fun sunTimes(day: LocalDate, place: Place, zone: ZoneId = ZoneId.systemDefault()): Sun {
        val noonMs = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val toDays = noonMs / DAY_MS - 0.5 + J1970 - J2000
        val lw = RAD * -place.lon
        val phi = RAD * place.lat
        val n = Math.round(toDays - 0.0009 - lw / (2 * PI)).toDouble()
        val ds = 0.0009 + lw / (2 * PI) + n
        val m = RAD * (357.5291 + 0.98560028 * ds)
        val l = m + RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)) + RAD * 102.9372 + PI
        val dec = asin(sin(E) * sin(l))
        fun transit(j: Double) = J2000 + j + 0.0053 * sin(m) - 0.0069 * sin(2 * l)
        fun local(j: Double): Double {
            val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(((j + 0.5 - J1970) * DAY_MS).roundToLong()), zone)
            return t.hour * 60 + t.minute + t.second / 60.0
        }
        val noon = transit(ds)
        val w = acos((sin(-0.833 * RAD) - sin(phi) * sin(dec)) / (kotlin.math.cos(phi) * kotlin.math.cos(dec)))
        if (w.isNaN()) return Sun(Double.NaN, Double.NaN, local(noon), sin(phi) * sin(dec) > 0)
        val set = transit(0.0009 + (w + lw) / (2 * PI) + n)
        return Sun(local(noon - (set - noon)), local(set), local(noon), false)
    }

    // S3 (revised 2026-10-28 so each part of the day is its own colour): indigo midnight → cobalt (3 AM) →
    // pre-dawn azure → dawn lavender → dawn rose → sunrise apricot → warm gold → bright lemon (mid-morning) →
    // pale cream noon → honey → amber → tangerine → red-orange → sunset coral-red → hot pink → purple →
    // blue-violet → royal blue (9 PM) → indigo midnight. The night stops are clock times, the rest follow the sun.
    private enum class Anchor { SR, NOON, SS, CLOCK }
    private val STOPS = listOf(
        Triple(Anchor.CLOCK, 180, "#2f72cf"), Triple(Anchor.SR, -80, "#3a9ad0"), Triple(Anchor.SR, -40, "#8f7ad8"),
        Triple(Anchor.SR, -12, "#e88ab2"), Triple(Anchor.SR, 15, "#ffa86b"), Triple(Anchor.SR, 70, "#ffcc5c"),
        Triple(Anchor.SR, 180, "#fff06a"), Triple(Anchor.NOON, 0, "#fff6cc"), Triple(Anchor.NOON, 105, "#ffe08a"),
        Triple(Anchor.SS, -150, "#ffbb4d"), Triple(Anchor.SS, -80, "#ff983c"), Triple(Anchor.SS, -30, "#ff7438"),
        Triple(Anchor.SS, 0, "#ff5257"), Triple(Anchor.SS, 20, "#ec4f92"), Triple(Anchor.SS, 45, "#a957dc"),
        Triple(Anchor.SS, 80, "#6c5ae8"), Triple(Anchor.CLOCK, 21 * 60, "#3f66e6"),
    )
    private const val MIDNIGHT = "#4a3ccf"
    private const val POLAR_DAY = "#fff6cc"

    /** The day's colour stops as (minute, OKLab), indigo at both midnights. */
    fun stops(sun: Sun): List<Pair<Double, DoubleArray>> {
        if (sun.rise.isNaN()) return listOf(0.0 to lab(if (sun.polarDay) POLAR_DAY else MIDNIGHT), 1440.0 to lab(if (sun.polarDay) POLAR_DAY else MIDNIGHT))
        val at = mapOf(Anchor.SR to sun.rise, Anchor.NOON to sun.noon, Anchor.SS to sun.set, Anchor.CLOCK to 0.0)
        val inner = STOPS.map { (a, off, c) -> at.getValue(a) + off to lab(c) }.filter { it.first > 0 && it.first < 1440 }.sortedBy { it.first }
        return listOf(0.0 to lab(MIDNIGHT)) + inner + listOf(1440.0 to lab(MIDNIGHT))
    }

    /** The colour of the light at a minute of the day (0–1440), from [stops]. */
    fun colorAt(stops: List<Pair<Double, DoubleArray>>, minute: Double): Int {
        var i = 1
        while (i < stops.size - 1 && stops[i].first < minute) i++
        val (m0, a) = stops[i - 1]
        val (m1, b) = stops[i]
        val f = if (m1 > m0) ((minute - m0) / (m1 - m0)).coerceIn(0.0, 1.0) else 0.0
        return rgb(DoubleArray(3) { a[it] + (b[it] - a[it]) * f })
    }

    // OKLab, so blends stay vivid (no muddy midpoints between orange and violet).
    private fun toLin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun toSrgb(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    private fun lab(hex: String): DoubleArray {
        val c = Color.parseColor(hex)
        val r = toLin(Color.red(c) / 255.0); val g = toLin(Color.green(c) / 255.0); val b = toLin(Color.blue(c) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun rgb(lab: DoubleArray): Int {
        val (ll, a, b) = lab.toList()
        val l = (ll + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (ll - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (ll - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        fun ch(v: Double) = (toSrgb(maxOf(0.0, v)).coerceIn(0.0, 1.0) * 255).toInt()
        return Color.rgb(
            ch(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            ch(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            ch(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
        )
    }
}
