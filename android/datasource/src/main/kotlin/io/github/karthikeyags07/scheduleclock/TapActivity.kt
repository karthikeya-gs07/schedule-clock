package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Invisible handler for the face's hour hotspots: `app://<package>/select?hour=N` (PRD §6.3 risk 1).
 * Toggles the meeting in that hour, asks for fresh complication data and finishes before it is shown.
 */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val received = System.currentTimeMillis()
        val hour = intent?.data?.getQueryParameter("hour")?.toIntOrNull()
        Log.i(TAG, "tap received: ${intent?.data} t=$received")

        val hit = hour?.let { SpikeSchedule.meetingInHour(it) } ?: -1
        val next = if (hit < 0 || hit == SpikeSchedule.selected(this)) -1 else hit
        SpikeSchedule.setSelected(this, next)
        SpikeSchedule.requestUpdate(this)
        Log.i(TAG, "selected=$next, update requested after ${System.currentTimeMillis() - received} ms")

        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}
