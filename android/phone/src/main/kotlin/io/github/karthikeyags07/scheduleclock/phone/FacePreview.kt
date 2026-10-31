package io.github.karthikeyags07.scheduleclock.phone

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

/**
 * The real watch face, live: the watch prototype (assets/face/index.html, embed mode) in a WebView, with
 * today's and tomorrow's meetings and the chosen colourway (RULES A2). Served from the app's assets through
 * [WebViewAssetLoader]; the app has no internet access, so the face uses its bundled font.
 * [interactive] lets taps through, so an hour on the ring can be tapped like on the watch.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WatchFacePreview(
    colourway: String, meetings: List<Meeting>, size: Dp, interactive: Boolean = false,
    atMinute: Int? = null, onTap: (() -> Unit)? = null,
) {
    val url = previewUrl(colourway, meetings, atMinute)
    val onTapState = rememberUpdatedState(onTap)
    Box(Modifier.size(size).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            modifier = Modifier.size(size),
            factory = { context ->
                val assets = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                WebView(context).apply {
                    setBackgroundColor(Color.BLACK)
                    settings.javaScriptEnabled = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            assets.shouldInterceptRequest(request.url)
                    }
                    webChromeClient = object : WebChromeClient() {   // the face's console errors, in logcat
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Log.i(TAG, "face preview: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                            return true
                        }
                    }
                    // Not interactive: the WebView would still swallow taps, so it hands them to [onTap] instead.
                    if (!interactive) setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_UP) onTapState.value?.invoke(); true }
                    loadUrl(url)
                    tag = url
                }
            },
            update = { view -> if (view.tag != url) { view.tag = url; view.loadUrl(url) } },
        )
    }
}

/**
 * index.html?embed&theme=…&face=…&v=…#<meetings>. Live time unless [atMinute] fixes it (minutes from midnight);
 * `v` changes with the meetings so a change reloads.
 */
private fun previewUrl(colourway: String, meetings: List<Meeting>, atMinute: Int?): String {
    val zone = ZoneId.systemDefault()
    val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    fun hhmm(minutes: Long) = "%02d:%02d".format(minutes / 60, minutes % 60)
    val events = JSONArray()
    meetings.forEach { m ->
        val day = Math.floorDiv(m.begin - midnight, 86_400_000L)
        if (day !in 0..1) return@forEach   // the face shows today and tomorrow
        val dayStart = midnight + day * 86_400_000L
        val startMin = (m.begin - dayStart) / 60_000
        events.put(JSONObject().apply {
            put("day", day)
            if (m.allDay) put("allDay", true)
            else {
                put("start", hhmm(startMin))
                put("end", hhmm(maxOf(startMin, (m.end - dayStart) / 60_000)))   // past midnight: "25:00"
            }
            put("title", m.title); put("where", m.where); put("private", m.private)
        })
    }
    val json = events.toString()
    // The page's themes are keyed by name; Solaris and the Palette themes are its sweeping ("timeshift") face.
    val look = Looks.of(colourway)
    val theme = URLEncoder.encode(if (colourway == "solaris") "Amber" else look.name, "UTF-8").replace("+", "%20")
    val face = if (colourway == "solaris" || look.palette != null) "timeshift" else "calen"
    return "https://appassets.androidplatform.net/assets/face/index.html?embed&theme=$theme&face=$face" +
        (atMinute?.let { "&time=$it" } ?: "") +
        "&v=${json.hashCode()}#${URLEncoder.encode(json, "UTF-8").replace("+", "%20")}"
}


/**
 * A busy sample day for showing what each colourway looks like (RULES A5), the same day as the design
 * prototype: shown at [SAMPLE_MINUTE] (10:13 AM), whatever is in the real calendar.
 */
object SampleDay {
    const val SAMPLE_MINUTE = 10 * 60 + 13

    fun meetings(): List<Meeting> {
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        fun m(start: Int, end: Int, title: String, where: String, calendar: Long, private: Boolean = false) =
            Meeting(midnight + start * 60_000L, midnight + end * 60_000L, title, where, allDay = false, private = private, calendarId = calendar)
        return listOf(
            m(540, 570, "Standup", "Google Meet", WORK),
            m(645, 705, "Roadmap planning", "Atlas room", WORK),
            m(750, 810, "Lunch with Priya", "", PERSONAL, private = true),
            m(840, 885, "Candidate interview", "Teams", WORK),
            m(855, 885, "Recruiter sync", "Teams", WORK),
            m(960, 1020, "Design review", "Figma", WORK),
            m(1080, 1110, "Gym", "Cult, Indiranagar", FAMILY),
            m(1140, 1200, "Client call", "Zoom", WORK),
        )
    }

    private const val WORK = -11L
    private const val PERSONAL = -12L
    private const val FAMILY = -13L

    /** Calendars to go with the sample day (demo mode only). */
    fun calendars() = listOf(
        PhoneCalendar(WORK, "Calendar", "alex@company.com", "com.microsoft.office.outlook", 0xFF4F8FF7.toInt()),
        PhoneCalendar(PERSONAL, "Personal", "alex@gmail.com", "com.google", 0xFF5FD4A0.toInt()),
        PhoneCalendar(-14L, "Holidays", "alex@gmail.com", "com.google", 0xFF9AA0A6.toInt()),
        PhoneCalendar(FAMILY, "Family", "alex@gmail.com", "com.google", 0xFFB48CFF.toInt()),
    )
}
