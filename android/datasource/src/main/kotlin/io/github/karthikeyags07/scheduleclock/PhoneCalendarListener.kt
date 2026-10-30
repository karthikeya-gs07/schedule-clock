package io.github.karthikeyags07.scheduleclock

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives what the phone app sends over the Data Layer:
 * - `/calendar`: the meetings; kept, the face redrawn and the buzzes re-armed.
 * - `/settings`: the buzz switches and colourway (RULES A5, A6); the buzzes re-armed, and if the face shows
 *   another colourway (the phone changed it while the watch was away), that colourway is swapped in.
 */
class PhoneCalendarListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (event.dataItem.uri.path) {
                "/calendar" -> {
                    CalendarSource.savePhone(this, map)
                    SpikeSchedule.requestUpdate(applicationContext)
                    MeetingBuzz.schedule(this)
                }
                PhoneSettings.PATH -> {
                    PhoneSettings.save(this, map)
                    MeetingBuzz.schedule(this)
                    catchUpColourway()
                }
            }
        }
    }

    // onDataChanged runs on a background thread, so waiting for the swap is fine, but never for long: every
    // later calendar update waits behind this call.
    private fun catchUpColourway() = runBlocking {
        val wanted = PhoneSettings.colourway(applicationContext) ?: return@runBlocking
        if (!FacePush.supported()) return@runBlocking
        try {
            val done = withTimeoutOrNull(20_000) {
                if (FacePush.status(applicationContext).installed) FacePush.install(applicationContext, wanted)
            }
            if (done == null) Log.w(TAG, "settings: swap to $wanted took too long; left for the next sync")
        } catch (e: Exception) {
            Log.w(TAG, "settings: couldn't swap to $wanted", e)
        }
    }
}
