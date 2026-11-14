package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager

/**
 * The flashlight (RULES A21), opened by tapping the 9 on the face (`app://<package>/flashlight`) when Settings →
 * Tap 9 for flashlight is on: the whole screen white at full brightness, kept on while it's open. A tap anywhere,
 * or back, turns it off and returns to the face; the watch's own brightness comes back as it was.
 */
class FlashlightActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        setContentView(View(this).apply {
            setBackgroundColor(Color.WHITE)
            contentDescription = "Flashlight on. Tap to turn off"
            setOnClickListener { finish() }
        })
    }

    // Leaving (the watch going to sleep, another app) turns it off rather than keeping the screen lit.
    override fun onPause() {
        super.onPause()
        finish()
    }
}
