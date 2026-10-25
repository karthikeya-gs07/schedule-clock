package io.github.karthikeyags07.scheduleclock

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/** Base for the face's fixed slots: logs each request and answers only its own type. */
abstract class SlotDataSourceService(
    private val slot: String,
    private val type: ComplicationType,
) : SuspendingComplicationDataSourceService() {

    abstract fun data(): ComplicationData

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(TAG, "$slot request id=${request.complicationInstanceId} type=${request.complicationType} t=${System.currentTimeMillis()}")
        return if (request.complicationType == type) data() else null
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == this.type) data() else null
}

/**
 * Slot 1 `ring` as one image (spike 2). Replaces the weighted-elements ring, which was capped at
 * 7 segments (spike 1, PRD §6.3a risk 9), and also carries the lane, clash and private layers.
 */
class RingImageDataSourceService : SlotDataSourceService("ring image", ComplicationType.SMALL_IMAGE) {
    override fun data() = SpikeSchedule.ringImage(this)
}

/** Slot 2 `ring_selected` (R9): the selected arc, drawn by the face on top of the image. */
class SelectedDataSourceService : SlotDataSourceService("selected", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.selectedArc(this)
}

/** Slot 4 `status`: VALUE = selected (0/1); the date, clock, title and time range live inside this slot. */
class StatusDataSourceService : SlotDataSourceService("status", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.status(this)
}

/** Slot 3 `details`: the line under the time range (place, clash, tomorrow, or "Details hidden"). */
class DetailsDataSourceService : SlotDataSourceService("details", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.detailsLine(this)
}

/** Slot 5 `ring_promote` (M3): the arc settling from the later lane onto the ring. */
class PromoteDataSourceService : SlotDataSourceService("promote", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.promoteArc(this)
}
