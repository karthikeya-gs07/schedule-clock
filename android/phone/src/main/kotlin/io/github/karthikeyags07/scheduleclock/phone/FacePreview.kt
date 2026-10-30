package io.github.karthikeyags07.scheduleclock.phone

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
fun WatchFacePreview(colourway: String, meetings: List<Meeting>, size: Dp, interactive: Boolean = false) {
    val url = previewUrl(colourway, meetings)
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
                    if (!interactive) setOnTouchListener { _, _ -> true }
                    loadUrl(url)
                    tag = url
                }
            },
            update = { view -> if (view.tag != url) { view.tag = url; view.loadUrl(url) } },
        )
    }
}

/** index.html?embed&theme=…&face=…&v=…#<meetings>. Live time; `v` changes with the meetings so a change reloads. */
private fun previewUrl(colourway: String, meetings: List<Meeting>): String {
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
    val theme = if (colourway == "solaris") "Amber" else colourway.replaceFirstChar(Char::uppercase)
    val face = if (colourway == "solaris") "timeshift" else "calen"
    return "https://appassets.androidplatform.net/assets/face/index.html?embed&theme=$theme&face=$face" +
        "&v=${json.hashCode()}#${URLEncoder.encode(json, "UTF-8").replace("+", "%20")}"
}

