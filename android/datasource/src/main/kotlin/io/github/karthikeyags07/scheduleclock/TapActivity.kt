package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Invisible handler for the face's deep links (PRD §6.3 risk 1):
 * - `app://<package>/select?hour=N`: toggles the meeting in that hour.
 * - `app://<package>/promote`: plays the lane → ring settle (spike 3), or resets it.
 * Asks for fresh complication data and finishes before anything is shown.
 */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val received = System.currentTimeMillis()
        val uri = intent?.data
        Log.i(TAG, "tap received: $uri t=$received")

        if (uri?.path == "/promote") {
            SpikeSchedule.runPromotion(this)
        } else {
            val hour = uri?.getQueryParameter("hour")?.toIntOrNull()
            val hit = hour?.let { SpikeSchedule.meetingInHour(it) } ?: -1
            val next = if (hit < 0 || hit == SpikeSchedule.selected(this)) -1 else hit
            SpikeSchedule.select(this, next)
            Log.i(TAG, "selected=$next, update requested after ${System.currentTimeMillis() - received} ms")
        }

        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}
