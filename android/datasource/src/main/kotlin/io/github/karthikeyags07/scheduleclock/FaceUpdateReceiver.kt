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
                withTimeoutOrNull(20_000) {
                    val s = FacePush.status(app)
                    val colourway = s.colourway ?: PhoneSettings.colourway(app)
                    if (s.installed && colourway != null) FacePush.install(app, colourway)
                }
            } catch (e: Exception) {
                Log.w(TAG, "update: couldn't refresh the face", e)
            } finally {
                pending.finish()
            }
        }
    }
}
