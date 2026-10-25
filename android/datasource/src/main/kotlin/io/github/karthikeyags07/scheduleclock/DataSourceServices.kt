package io.github.karthikeyags07.scheduleclock

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Slot `ring` as one image (spike 2). Replaces the weighted-elements ring, which was capped at
 * 7 segments (spike 1, PRD §6.3a risk 9), and also carries the lane, clash and private layers.
 */
class RingImageDataSourceService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(TAG, "ring image request id=${request.complicationInstanceId} type=${request.complicationType} t=${System.currentTimeMillis()}")
        if (request.complicationType != ComplicationType.SMALL_IMAGE) return null
        return SpikeSchedule.ringImage(SpikeSchedule.selected(this))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SMALL_IMAGE) SpikeSchedule.ringImage(-1) else null
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
