package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * What the watch app shows when opened from the app list (RULES W-home): CalenTime is a companion that brings
 * the calendar to the watch face, so there is nothing to use here. It says whether the CalenTime face is the
 * watch face, and offers the one next step: go back to it, make it the watch face, or put it on the watch.
 * Calendar access off: a line and a button to turn it on ([PermissionActivity]).
 */
class WatchHomeActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var status: TextView
    private lateinit var action: Button
    private lateinit var access: Button
    private var askedAccess = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accent = Themes.of(FacePush.colourway(this)).accent
        val px = { dp: Int -> (dp * resources.displayMetrics.density).toInt() }
        // Round screens: generous side padding so text stays inside the circle.
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(28), px(36), px(28), px(48))
        }
        fun text(size: Float, colour: Int, bold: Boolean = false) = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(colour)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        fun button() = Button(this).apply {
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply { cornerRadius = px(24).toFloat(); setColor(accent) }
            backgroundTintList = ColorStateList.valueOf(accent)
            minHeight = px(44)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = px(12) }
        }
        column.addView(text(18f, Color.WHITE, bold = true).apply { text = getString(R.string.app_name) })
        column.addView(text(13f, Color.rgb(0xA0, 0xA0, 0xA0)).apply { text = getString(R.string.home_companion); setPadding(0, px(4), 0, 0) })
        status = text(15f, Color.WHITE).apply { setPadding(0, px(10), 0, 0) }
        column.addView(status)
        action = button().apply { visibility = View.GONE }
        column.addView(action)
        access = button().apply {
            visibility = View.GONE
            text = getString(R.string.home_access_button)
            setOnClickListener { startActivity(Intent(this@WatchHomeActivity, PermissionActivity::class.java)) }
        }
        column.addView(access)
        column.addView(text(13f, Color.rgb(0xA0, 0xA0, 0xA0)).apply {
            text = getString(R.string.home_about)
            setPadding(0, px(18), 0, 0)
        })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(column) })
    }

    override fun onResume() {
        super.onResume()
        val hasAccess = CalendarSource.hasPermission(this)
        // First open with access off: ask straight away, as the app list did before this screen.
        if (!hasAccess && !askedAccess) {
            askedAccess = true
            startActivity(Intent(this, PermissionActivity::class.java))
        }
        access.visibility = if (hasAccess) View.GONE else View.VISIBLE
        launch { refresh() }
    }

    private suspend fun refresh() {
        if (!FacePush.supported()) return show(R.string.face_needs_wear6, null)
        val s = try { FacePush.status(applicationContext) } catch (e: Exception) {
            Log.w(TAG, "home: status failed", e); FacePush.Status(supported = true)
        }
        when {
            s.active -> show(R.string.home_active, R.string.home_go_to_face) { goToFace() }
            s.installed && !s.activateUsed -> show(R.string.home_installed, R.string.home_set_face) { setUp(s.colourway) }
            s.installed -> show(R.string.home_installed_pick, R.string.home_go_to_face) { goToFace() }
            else -> show(R.string.home_missing, R.string.home_install_face) { setUp(s.colourway) }
        }
    }

    private fun show(message: Int, button: Int?, onClick: (() -> Unit)? = null) {
        status.text = getString(message)
        action.visibility = if (button == null) View.GONE else View.VISIBLE
        if (button != null) { action.text = getString(button); action.setOnClickListener { onClick?.invoke() } }
    }

    /** Puts the face on (in its last colours, else Amber) and makes it the watch face, if Android still allows that. */
    private fun setUp(colourway: String?) = startActivity(Intent(this, FaceSetupActivity::class.java)
        .putExtra(FaceSetupActivity.EXTRA_COLOUR, colourway ?: "amber")
        .putExtra(FaceSetupActivity.EXTRA_ACTIVATE, true))

    /** On Wear OS the home screen is the watch face. */
    private fun goToFace() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }
}
