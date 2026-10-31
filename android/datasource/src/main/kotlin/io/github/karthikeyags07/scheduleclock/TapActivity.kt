package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Invisible handler for the face's deep links (PRD §6.3 risk 1):
 * - `app://<package>/select?hour=N`: cycles the meetings in that dial hour (I2–I6).
 * - `app://<package>/release`: a tap on the details; back to the normal view (I8).
 * - `app://<package>/buzz?kind=start|middle|end`: debug, plays a meeting buzz pattern now.
 * - `app://<package>/promote`: plays the lane → ring settle (spike 3).
 * - `app://<package>/time?offset=M`: debug time shift in minutes for testing.
 * - `app://<package>/day?name=busy|example`: debug switch of the sample day.
 * - `app://<package>/face?colour=mint&activate=1`: debug, puts the face on the watch (Watch Face Push).
 * - `app://<package>/colour?name=mint`: the phone app's colourway change, shown as a transition (ColourSwapActivity).
 * - `app://<package>/swap?colour=mint`: debug, swaps the colourway silently (no transition), as the /settings catch-up does.
 * Asks for fresh complication data and finishes before anything is shown. With no calendar yet
 * (nothing from the phone, no access on the watch), any tap on the ring opens [PermissionActivity].
 */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val received = System.currentTimeMillis()
        val uri = intent?.data
        Log.i(TAG, "tap received: $uri t=$received")
        // Test links (buzz, settle, clock offset, silent swap, sample day) only in debug builds: this activity is
        // exported, so in a release build no other app can change the face's data through it.
        val test = BuildConfig.DEBUG

        if (uri?.path == "/select" && SpikeSchedule.needsCalendarSetup(this)) {
            startActivity(Intent(this, PermissionActivity::class.java))
        } else if (uri?.path == "/release") {
            SpikeSchedule.release(this)
        } else if (test && uri?.path == "/buzz") {
            MeetingBuzz.Kind.entries.firstOrNull { it.name.equals(uri.getQueryParameter("kind"), true) }
                ?.let { MeetingBuzz.play(this, it) }
        } else if (test && uri?.path == "/promote") {
            SpikeSchedule.runPromotion(this)
        } else if (test && uri?.path == "/time") {
            SpikeSchedule.setOffset(this, uri.getQueryParameter("offset")?.toIntOrNull() ?: 0)
        } else if (uri?.path == "/colour") {   // the phone app picked a colourway: swap it in behind a transition
            startActivity(Intent(this, ColourSwapActivity::class.java)
                .putExtra(ColourSwapActivity.EXTRA_COLOUR, uri.getQueryParameter("name")))
        } else if (test && uri?.path == "/swap") {   // debug: swap colourway silently, as the phone app does
            val colourway = uri.getQueryParameter("colour") ?: "amber"
            val app = applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { FacePush.install(app, colourway) }
        } else if (uri?.path == "/face") {
            startActivity(Intent(this, FaceSetupActivity::class.java)
                .putExtra(FaceSetupActivity.EXTRA_COLOUR, uri.getQueryParameter("colour"))
                .putExtra(FaceSetupActivity.EXTRA_ACTIVATE, uri.getQueryParameter("activate") == "1"))
        } else if (test && uri?.path == "/day") {
            SpikeSchedule.setDay(this, uri.getQueryParameter("name") ?: "busy")
        } else if (uri?.path == "/select") {
            uri.getQueryParameter("hour")?.toIntOrNull()?.let { SpikeSchedule.tapHour(this, it) }
            Log.i(TAG, "update requested after ${System.currentTimeMillis() - received} ms")
        }

        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}
