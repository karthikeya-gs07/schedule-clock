package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * All events (RULES A20), opened by tapping the 6 on the face (`app://<package>/agenda`) when Settings → Tap 6 for
 * all events is on: every meeting the watch has from now to the end of tomorrow, under Today and Tomorrow, in the
 * colourway. Each row: the time range (in the accent while it's on now), the title ("Private" for private ones) and
 * the place. All-day events sit at the top of their day. Scrolls with the crown.
 */
class AgendaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = Themes.of(FacePush.colourway(this) ?: PhoneSettings.colourway(this))
        val h24 = PhoneSettings.clock24(this)
        val anta = resources.getFont(R.font.anta_regular)
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        val events = CalendarSource.meetings(this).filter { it.endMin > now && it.startMin < 2 * 1440 }
            .sortedWith(compareBy({ it.startMin >= 1440 }, { !it.allDay }, { it.startMin }))

        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
        fun text(s: String, size: Float, colour: Int, bold: Boolean = false, font: android.graphics.Typeface? = null) = TextView(this).apply {
            text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, size); setTextColor(colour)
            gravity = Gravity.CENTER; typeface = font ?: android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val grey = Color.parseColor("#8e8e93")
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28f), dp(36f), dp(28f), dp(56f))   // room for the round screen's top and bottom
        }
        list.addView(text("All events", 16f, Color.WHITE, font = anta).apply { setPadding(0, 0, 0, dp(6f)) })
        if (events.isEmpty()) list.addView(text("Nothing else today or tomorrow.", 14f, grey).apply { setPadding(0, dp(18f), 0, 0) })
        var day = -1
        for (m in events) {
            val d = if (m.startMin >= 1440) 1 else 0
            if (d != day) {
                day = d
                list.addView(text(if (d == 0) "Today" else "Tomorrow" + " · " + java.time.LocalDate.now().plusDays(1)
                    .format(DateTimeFormatter.ofPattern("EEE d MMM")), 13f, theme.accent, bold = true).apply { setPadding(0, dp(14f), 0, dp(4f)) })
            }
            val live = m.startMin <= now && !m.allDay
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
                background = GradientDrawable().apply { cornerRadius = dp(18f).toFloat(); setColor(Color.argb(if (live) 34 else 20, 255, 255, 255)) }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) }
            }
            row.addView(text(if (m.allDay) "All day" else ScheduleRules.formatRange(m, h24), 13f, if (live) theme.accent else grey))
            row.addView(text(if (m.private) "Private" else m.title.ifEmpty { "Busy" }, 15f, Color.WHITE, bold = true))
            if (!m.private && m.where.isNotEmpty()) row.addView(text(m.where, 12f, grey))
            list.addView(row)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(theme.background); isVerticalScrollBarEnabled = true; isFocusableInTouchMode = true
            addView(list)
        }
        setContentView(scroll)
        scroll.requestFocus()   // so the crown scrolls it
    }

    // Leaving by home (or anything else) closes it, so the next tap on the face opens what was tapped.
    override fun onStop() {
        super.onStop()
        finish()
    }
}
