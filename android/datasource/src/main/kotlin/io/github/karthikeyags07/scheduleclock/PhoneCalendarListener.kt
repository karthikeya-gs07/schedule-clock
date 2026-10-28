package io.github.karthikeyags07.scheduleclock

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives the calendar from the phone app (Data Layer path `/calendar`), keeps it, and redraws the face. */
class PhoneCalendarListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/calendar") {
                CalendarSource.savePhone(this, DataMapItem.fromDataItem(event.dataItem).dataMap)
                SpikeSchedule.requestUpdate(applicationContext)
                MeetingBuzz.schedule(this)
            }
        }
    }
}
