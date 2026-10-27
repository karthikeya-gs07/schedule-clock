package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Invisible handler for the face's deep links (PRD §6.3 risk 1):
 * - `app://<package>/select?hour=N`: cycles the meetings in that dial hour (I2–I6).
 * - `app://<package>/release`: a tap on the details; back to the normal view (I8).
 * - `app://<package>/promote`: plays the lane → ring settle (spike 3).
 * - `app://<package>/time?offset=M`: debug time shift in minutes for testing.
 * - `app://<package>/day?name=busy|example`: debug switch of the sample day.
 * Asks for fresh complication data and finishes before anything is shown. With no calendar yet
 * (nothing from the phone, no access on the watch), any tap on the ring opens [PermissionActivity].
 */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val received = System.currentTimeMillis()
        val uri = intent?.data
        Log.i(TAG, "tap received: $uri t=$received")

        if (uri?.path == "/select" && SpikeSchedule.needsCalendarSetup(this)) {
            startActivity(Intent(this, PermissionActivity::class.java))
        } else if (uri?.path == "/release") {
            SpikeSchedule.release(this)
        } else if (uri?.path == "/promote") {
            SpikeSchedule.runPromotion(this)
        } else if (uri?.path == "/time") {
            SpikeSchedule.setOffset(this, uri.getQueryParameter("offset")?.toIntOrNull() ?: 0)
        } else if (uri?.path == "/day") {
            SpikeSchedule.setDay(this, uri.getQueryParameter("name") ?: "busy")
        } else {
            uri?.getQueryParameter("hour")?.toIntOrNull()?.let { SpikeSchedule.tapHour(this, it) }
            Log.i(TAG, "update requested after ${System.currentTimeMillis() - received} ms")
        }

        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}
