package io.github.karthikeyags07.scheduleclock

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Base for the face's fixed slots: logs each request and answers only its own type. */
abstract class SlotDataSourceService(
    private val slot: String,
    private val type: ComplicationType,
) : SuspendingComplicationDataSourceService() {

    abstract fun data(): ComplicationData

    /** The answer to a request. Slots answer on the main thread; the ring image draws off it (see below). */
    protected open suspend fun produce(): ComplicationData = data()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(TAG, "$slot request id=${request.complicationInstanceId} type=${request.complicationType} t=${System.currentTimeMillis()}")
        if (request.complicationType != type) return null
        return produce().also { FaceLoad.served(slot) }
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

    // Drawing and encoding the ring takes 40–350 ms (about 1 s the first time after an update). All the slots answer
    // on one thread, so drawing it there held back the text slots behind it, and they reached the face at different
    // moments: the place line moved before the title and time range and ran over them (and vice versa). Drawn on a
    // background thread, the text slots answer at once and move together.
    override suspend fun produce(): ComplicationData = withContext(Dispatchers.Default) { data() }
}

/** Slot 4 `status`: VALUE = selected (0/1); the date, clock, title and time range live inside this slot. */
class StatusDataSourceService : SlotDataSourceService("status", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.status(this)
}

/** Slot 3 `details`: the line under the time range (place, clash, tomorrow, or "Details hidden"). */
class DetailsDataSourceService : SlotDataSourceService("details", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.detailsLine(this)
}

/** Slot 7 `zone` (R13): the second time zone's difference, for the triangle on the ring. */
class ZoneDataSourceService : SlotDataSourceService("zone", ComplicationType.RANGED_VALUE) {
    override fun data() = ZoneMarker.data(this)
}

/** Slot 5 `ring_promote` (M3): the arc settling from the later lane onto the ring. */
class PromoteDataSourceService : SlotDataSourceService("promote", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.promoteArc(this)
}

/** Slot 4 `title_scroll` (C9a): the full title, scrolled by the face, in the window without the arrow. */
class TitleScrollDataSourceService : SlotDataSourceService("title scroll", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.titleScroll(this, withArrow = false)
}

/** Slot 6 `title_scroll_arrow` (C9a): the same, in the narrower window beside the look-ahead arrow. */
class TitleScrollArrowDataSourceService : SlotDataSourceService("title scroll arrow", ComplicationType.RANGED_VALUE) {
    override fun data() = SpikeSchedule.titleScroll(this, withArrow = true)
}

/** The Focus face's only slot (RULES A26): what it counts down to, and the meeting. */
class FocusDataSourceService : SlotDataSourceService("focus", ComplicationType.RANGED_VALUE) {
    override fun data() = Focus.data(this)
}

/**
 * When the face last received its slots, so the colour change on the watch (ColourSwapActivity) can lift as soon
 * as the new face has its ring and its text, instead of after a fixed wait.
 */
object FaceLoad {
    @Volatile var ringAt = 0L
        private set
    @Volatile var statusAt = 0L
        private set

    fun served(slot: String) {
        val now = SystemClock.elapsedRealtime()
        when (slot) { "ring image" -> ringAt = now; "status" -> statusAt = now }
    }

    /** True once the face has asked for (and been given) its ring and its text since [since] (elapsedRealtime). */
    fun loadedSince(since: Long) = ringAt > since && statusAt > since
}
