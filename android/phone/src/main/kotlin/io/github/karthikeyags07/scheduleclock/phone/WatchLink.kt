package io.github.karthikeyags07.scheduleclock.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * The phone's side of the face on the watch (Watch Face Push, PRD §6.5): which watch is connected, whether
 * Calen-Time's face is on it and active, putting it there, and switching its colourway. The watch app's
 * `FaceRequestService` answers `/face/status` and `/face/colour`; the first install opens its
 * `FaceSetupActivity` on the watch, since the watch may need to ask a permission on its own screen.
 */
object WatchLink {
    data class Watch(val id: String, val name: String)

    /** What the phone knows about the watch. [face] is null when the watch app didn't answer. */
    data class State(val watch: Watch?, val face: FaceStatus?)

    data class FaceStatus(
        val supported: Boolean, val installed: Boolean, val active: Boolean,
        val colourway: String?, val activateUsed: Boolean,
    )

    suspend fun watch(context: Context): Watch? = try {
        Wearable.getNodeClient(context).connectedNodes.await()
            .sortedByDescending(Node::isNearby).firstOrNull()?.let { Watch(it.id, it.displayName) }
    } catch (e: Exception) {
        Log.w(TAG, "no watch: ${e.message}"); null
    }

    suspend fun state(context: Context): State {
        val watch = watch(context) ?: return State(null, null)
        return State(watch, request(context, watch, "/face/status", ByteArray(0)))
    }

    /** Swaps the face on the watch to [colourway]; does nothing if the face isn't on the watch yet. */
    suspend fun setColourway(context: Context, colourway: String): FaceStatus? {
        val watch = watch(context) ?: return null
        return request(context, watch, "/face/colour", colourway.toByteArray())
    }

    /** Opens the watch app's face setup on the watch: puts the face on in [colourway] and makes it active. */
    suspend fun install(context: Context, colourway: String): Boolean {
        val watch = watch(context) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("app://io.github.karthikeyags07.scheduleclock/face?colour=$colourway&activate=1"))
        return try {
            RemoteActivityHelper(context).startRemoteActivity(intent, watch.id).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "couldn't open face setup on the watch: ${e.message}"); false
        }
    }

    /** Opens the watch's colour change (a short transition that covers the face swap) for [colourway]. */
    suspend fun openColourChange(context: Context, colourway: String): Boolean {
        val watch = watch(context) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("app://io.github.karthikeyags07.scheduleclock/colour?name=$colourway"))
        return try {
            RemoteActivityHelper(context).startRemoteActivity(intent, watch.id).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "couldn't open the colour change on the watch: ${e.message}"); false
        }
    }

    /** Opens Calen-Time's Play Store page on the watch, for when the watch app isn't installed there. */
    suspend fun openPlayOnWatch(context: Context): Boolean {
        val watch = watch(context) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("market://details?id=${context.packageName}"))
        return try {
            RemoteActivityHelper(context).startRemoteActivity(intent, watch.id).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "couldn't open the Play Store on the watch: ${e.message}"); false
        }
    }

    private suspend fun request(context: Context, watch: Watch, path: String, body: ByteArray): FaceStatus? = try {
        val bytes = withTimeout(8_000) { Wearable.getMessageClient(context).sendRequest(watch.id, path, body).await() }
        val j = JSONObject(bytes.decodeToString())
        FaceStatus(j.getBoolean("supported"), j.getBoolean("installed"), j.getBoolean("active"),
            j.optString("colourway").ifEmpty { null }, j.getBoolean("activateUsed"))
    } catch (e: Exception) {
        Log.w(TAG, "watch didn't answer $path: ${e.message}"); null
    }
}
