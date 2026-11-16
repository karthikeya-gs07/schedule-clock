package io.github.karthikeyags07.scheduleclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * After the watch app updates, puts its new face on the watch in the same colourway. The face pushed earlier
 * is a copy, so without this an update (new clock formats, always-on look) would never reach the watch.
 */
class FaceUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED || !FacePush.supported()) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Right after an update the watch's Watch Face Push service is often not ready yet ("could not be
                // accessed"), which used to leave the watch on Amber: try again a few seconds later, up to 3 times.
                for (attempt in 1..3) {
                    try {
                        withTimeoutOrNull(20_000) {
                            val s = FacePush.status(app)
                            // An old or unknown saved name falls back to what the phone wants, then to Amber.
                            val colourway = FacePush.known(s.colourway) ?: PhoneSettings.faceNow(app) ?: "amber"
                            Log.i(TAG, "update: face ${if (s.installed) "installed" else "not installed"}, colourway $colourway")
                            // Always put it on again: an update (even one with the same version, as in testing) swaps
                            // the pushed face back to the app's default (Amber).
                            if (s.installed) FacePush.install(app, colourway, force = true)
                        }
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "update: couldn't refresh the face (try $attempt of 3)", e)
                        if (attempt < 3) kotlinx.coroutines.delay(4_000L * attempt)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
