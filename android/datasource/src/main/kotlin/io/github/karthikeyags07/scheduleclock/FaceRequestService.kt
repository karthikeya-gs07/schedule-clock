package io.github.karthikeyags07.scheduleclock

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Answers the phone app about the face (Watch Face Push, PRD §6.5), over the Data Layer's request/reply:
 * - `/face/status`: is it on the watch, is it the watch face, which colourway, has "make active" been used.
 * - `/face/colour` (body: a colourway): swaps that colourway's face into our slot, if the face is on the watch.
 * Both reply with the status as JSON. Putting the face on the watch the first time goes through
 * [FaceSetupActivity] instead, since it may need to ask a permission on the watch's screen.
 */
class FaceRequestService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onRequest(nodeId: String, path: String, data: ByteArray): Task<ByteArray>? {
        val reply = TaskCompletionSource<ByteArray>()
        scope.launch {
            try {
                val status = when (path) {
                    STATUS -> FacePush.status(applicationContext)
                    COLOUR -> {
                        val colourway = data.decodeToString()
                        if (FacePush.supported() && FacePush.status(applicationContext).installed) FacePush.install(applicationContext, colourway)
                        else FacePush.status(applicationContext)
                    }
                    else -> return@launch reply.setException(IllegalArgumentException("unknown path $path"))
                }
                reply.setResult(json(status).toByteArray())
            } catch (e: Exception) {
                Log.w(TAG, "face request $path failed", e)
                reply.setException(e)
            }
        }
        return reply.task
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun json(s: FacePush.Status) = JSONObject()
        .put("supported", s.supported).put("installed", s.installed).put("active", s.active)
        .put("colourway", s.colourway ?: "").put("activateUsed", s.activateUsed).toString()

    companion object {
        const val STATUS = "/face/status"
        const val COLOUR = "/face/colour"
    }
}
