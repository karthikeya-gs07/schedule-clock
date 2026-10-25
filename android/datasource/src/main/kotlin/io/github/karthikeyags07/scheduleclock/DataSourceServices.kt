package io.github.karthikeyags07.scheduleclock

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/** Slot `ring` (RULES §8 slot 1). */
class RingDataSourceService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(TAG, "ring request id=${request.complicationInstanceId} type=${request.complicationType} t=${System.currentTimeMillis()}")
        if (request.complicationType != ComplicationType.WEIGHTED_ELEMENTS) return null
        return SpikeSchedule.ring(SpikeSchedule.selected(this))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.WEIGHTED_ELEMENTS) SpikeSchedule.ring(-1) else null
}

/** Slot `status` (RULES §8 slot 4). */
class StatusDataSourceService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(TAG, "status request id=${request.complicationInstanceId} type=${request.complicationType} t=${System.currentTimeMillis()}")
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null
        return SpikeSchedule.status(SpikeSchedule.selected(this))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) SpikeSchedule.status(-1) else null
}
