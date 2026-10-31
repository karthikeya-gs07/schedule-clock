package io.github.karthikeyags07.scheduleclock

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/**
 * Watch Face Push (PRD §6.5): this app installs the Calen-Time face itself instead of it having its own
 * store listing. It carries one face per colourway (assets/faces/<colourway>.apk, all with the same id) and
 * the validator's token for each; picking a colourway swaps that face into our one slot (RULES §9).
 * Making it the active face is allowed once, with the user's permission; after that the phone app shows
 * how to pick it on the watch (RULES A1).
 */
object FacePush {
    val COLOURWAYS = listOf("amber", "mint", "violet", "rose", "solaris")
    const val FACE_PACKAGE = "io.github.karthikeyags07.scheduleclock.watchfacepush.calentime"
    const val ACTIVATE_PERMISSION = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

    /** What the phone app needs to know: is the face on the watch, is it the watch face, in which colours. */
    data class Status(
        val supported: Boolean,
        val installed: Boolean = false,
        val active: Boolean = false,
        val colourway: String? = null,
        val activateUsed: Boolean = false,
    )

    fun supported() = WatchFacePushManagerFactory.isSupported()

    private fun manager(context: Context) = WatchFacePushManagerFactory.createWatchFacePushManager(context)
    private fun prefs(context: Context) = context.getSharedPreferences("face_push", Context.MODE_PRIVATE)

    suspend fun status(context: Context): Status {
        if (!supported()) return Status(supported = false)
        val slot = manager(context).listWatchFaces().installedWatchFaceDetails.firstOrNull()
        val prefs = prefs(context)
        // Just after a swap the active check can fail for a moment (Google's docs say not to ask then); a swap
        // keeps the face active, so the last known answer stands in.
        val active = slot != null && try {
            manager(context).isWatchFaceActive(slot.packageName).also { prefs.edit().putBoolean("active", it).apply() }
        } catch (e: Exception) {
            prefs.getBoolean("active", false)
        }
        return Status(
            supported = true,
            installed = slot != null,
            active = active,
            colourway = if (slot != null) prefs.getString("colourway", null) else null,
            activateUsed = prefs.getBoolean("activateUsed", false),
        )
    }

    // One change at a time: two swaps at once (the phone's instant swap and the /settings catch-up) left one
    // of them never answering, which stuck the phone listener and every calendar update behind it.
    private val busy = Mutex()

    /** Puts the face in [colourway] on the watch: added the first time, swapped into our slot after that. */
    suspend fun install(context: Context, colourway: String): Status = busy.withLock {
        require(colourway in COLOURWAYS) { "unknown colourway $colourway" }
        // Already showing it (often because the other path just swapped it): answer from what we know rather
        // than asking "is it active?" so soon after a swap, which can stall past the phone's timeout.
        if (prefs(context).getString("colourway", null) == colourway &&
            manager(context).listWatchFaces().installedWatchFaceDetails.isNotEmpty()) return@withLock cachedStatus(context, colourway)
        val manager = manager(context)
        val slot = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull()
        val token = JSONObject(context.assets.open("faces/tokens.json").bufferedReader().readText()).getString(colourway)
        apk(context, colourway).use { fd ->
            if (slot == null) manager.addWatchFace(fd, token) else manager.updateWatchFace(slot.slotId, fd, token)
        }
        prefs(context).edit().putString("colourway", colourway).apply()
        Log.i(TAG, "face push: ${if (slot == null) "added" else "swapped to"} $colourway")
        if (slot == null) status(context) else cachedStatus(context, colourway)   // no active check right after a swap
    }

    private fun cachedStatus(context: Context, colourway: String) = Status(supported = true, installed = true,
        active = prefs(context).getBoolean("active", false), colourway = colourway, activateUsed = prefs(context).getBoolean("activateUsed", false))

    fun canActivate(context: Context) =
        context.checkSelfPermission(ACTIVATE_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Makes our face the watch face. Android allows this once per app; later calls (or a denied permission)
     * return false and the phone app shows how to pick it by hand.
     */
    suspend fun activate(context: Context): Boolean {
        val prefs = prefs(context)
        val slot = manager(context).listWatchFaces().installedWatchFaceDetails.firstOrNull() ?: return false
        if (prefs.getBoolean("activateUsed", false) || !canActivate(context)) return false
        return try {
            manager(context).setWatchFaceAsActive(slot.slotId)
            prefs.edit().putBoolean("active", true).apply()
            true
        } catch (e: WatchFacePushManager.SetWatchFaceAsActiveException) {
            Log.w(TAG, "face push: couldn't make it the watch face (${e.errorCode})")
            false
        } finally {
            prefs.edit().putBoolean("activateUsed", true).apply()
        }
    }

    /** The bundled face as a file descriptor (assets may be compressed, so it's copied out first). */
    private fun apk(context: Context, colourway: String): ParcelFileDescriptor {
        val file = File(context.cacheDir, "face-$colourway.apk")
        context.assets.open("faces/$colourway.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
