package io.github.karthikeyags07.scheduleclock

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

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

    // onDataChanged runs on a background thread, so waiting for the swap here is fine.
    private fun catchUpColourway() = runBlocking {
        val wanted = PhoneSettings.colourway(applicationContext) ?: return@runBlocking
        try {
            val status = if (FacePush.supported()) FacePush.status(applicationContext) else return@runBlocking
            if (status.installed && status.colourway != wanted) FacePush.install(applicationContext, wanted)
        } catch (e: Exception) {
            Log.w(TAG, "settings: couldn't swap to $wanted", e)
        }
    }
}
