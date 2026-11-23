package io.github.karthikeyags07.scheduleclock

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/**
 * Watch Face Push (PRD §6.5): this app installs the CalenTime face itself instead of it having its own
 * store listing. It carries one face per colourway (assets/faces/<colourway>.apk, all with the same id) and
 * the validator's token for each; picking a colourway swaps that face into our one slot (RULES §9).
 * Making it the active face is allowed once, with the user's permission; after that the phone app shows
 * how to pick it on the watch (RULES A1).
 */
object FacePush {
    val COLOURWAYS = Themes.IDS
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

    /** When the last swap finished (elapsedRealtime), or 0. */
    @Volatile var swappedAt = 0L
        private set

    /** Is the face on the watch (in our slot)? Doesn't ask whether it's active, which is slow and unreliable mid-swap. */
    suspend fun installed(context: Context) = supported() && manager(context).listWatchFaces().installedWatchFaceDetails.isNotEmpty()

    suspend fun status(context: Context): Status {
        if (!supported()) return Status(supported = false)
        val slot = manager(context).listWatchFaces().installedWatchFaceDetails.firstOrNull()
        val prefs = prefs(context)
        // Just after a swap the active check can fail, stall or answer "no" for a moment (Google's docs say not to ask
        // then). A swap keeps the face active, so for a while after one the last known answer stands in. Saving a
        // passing "no" made the phone think the face had been taken off, and its next colour change then opened the
        // face setup on the watch ("put it on, then pick it yourself") instead of the quick swap.
        val settling = SystemClock.elapsedRealtime() - swappedAt < SETTLE_AFTER_SWAP_MS && swappedAt > 0
        val active = slot != null && if (settling) prefs.getBoolean("active", false) else try {
            manager(context).isWatchFaceActive(slot.packageName).also { prefs.edit().putBoolean("active", it).apply() }
        } catch (e: Exception) {
            prefs.getBoolean("active", false)
        }
        return Status(
            supported = true,
            installed = slot != null,
            active = active,
            colourway = if (slot != null) known(prefs.getString("colourway", null)) else null,
            activateUsed = prefs.getBoolean("activateUsed", false),
        )
    }

    // One change at a time: two swaps at once (the phone's instant swap and the /settings catch-up) left one
    // of them never answering, which stuck the phone listener and every calendar update behind it.
    private val busy = Mutex()

    /** Puts the face in [colourway] on the watch: added the first time, swapped into our slot after that. */
    suspend fun install(context: Context, colourway: String, force: Boolean = false): Status = busy.withLock {
        require(colourway in COLOURWAYS) { "unknown colourway $colourway" }
        // Already showing it (often because the other path just swapped it): answer from what we know rather
        // than asking "is it active?" so soon after a swap, which can stall past the phone's timeout.
        // An app update carries a new face, so the same colourway is put on again after one (see FaceUpdateReceiver).
        if (!force && prefs(context).getString("colourway", null) == colourway && prefs(context).getLong("appVersion", 0) == appVersion(context) &&
            manager(context).listWatchFaces().installedWatchFaceDetails.isNotEmpty()) return@withLock cachedStatus(context, colourway)
        val manager = manager(context)
        val slot = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull()
        val token = JSONObject(context.assets.open("faces/tokens.json").bufferedReader().readText()).getString(colourway)
        // Saved first: the new face asks for its ring image as it loads, and the ring is drawn in its colours.
        val before = prefs(context).getString("colourway", null)
        prefs(context).edit().putString("colourway", colourway).putLong("appVersion", appVersion(context)).commit()
        // Draw the new ring meanwhile, so the new face's first ask finds it ready instead of waiting ~1 s for it.
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { SpikeSchedule.warmRing(app) }
        try {
            apk(context, colourway).use { fd ->
                if (slot == null) manager.addWatchFace(fd, token) else manager.updateWatchFace(slot.slotId, fd, token)
            }
        } catch (e: Exception) {
            prefs(context).edit().putString("colourway", before).commit()
            throw e
        }
        swappedAt = SystemClock.elapsedRealtime()
        SpikeSchedule.requestUpdate(context.applicationContext)
        Log.i(TAG, "face push: ${if (slot == null) "added" else "swapped to"} $colourway")
        if (slot == null) status(context) else cachedStatus(context, colourway)   // no active check right after a swap
    }

    private fun appVersion(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    private fun cachedStatus(context: Context, colourway: String) = Status(supported = true, installed = true,
        active = prefs(context).getBoolean("active", false), colourway = colourway, activateUsed = prefs(context).getBoolean("activateUsed", false))

    /** The colourway of the face on the watch (the last one put there), for drawing the ring in its colours. */
    fun colourway(context: Context): String? = known(prefs(context).getString("colourway", null))

    /**
     * [name] as a face this app carries, or null. Focus mode used to be faces of their own ("focusEmber"); those
     * names, saved before the update that made it a layer, mean their colourway now (RULES A26).
     */
    fun known(name: String?): String? =
        (if (name != null && name.startsWith("focus")) FocusEnd.base(name) else name)?.takeIf { it in COLOURWAYS }

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

    /**
     * The bundled face as a file descriptor (assets may be compressed, so it's copied out first). The copy is kept for
     * this install of the app, so going back to a colourway doesn't unpack it again; copies from earlier installs are
     * removed. Keyed by the install time, not the version code: test builds keep the same version code, and a face
     * from an earlier build doesn't match the new build's token, so Watch Face Push refuses it.
     */
    private fun apk(context: Context, colourway: String): ParcelFileDescriptor {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val file = File(context.cacheDir, "face-$colourway-$version.apk")
        if (!file.exists() || file.length() == 0L) {
            context.cacheDir.listFiles { f -> f.name.startsWith("face-") && f.name.endsWith(".apk") && !f.name.endsWith("-$version.apk") }
                ?.forEach { it.delete() }
            val part = File(context.cacheDir, "${file.name}.part")
            context.assets.open("faces/$colourway.apk").use { input -> part.outputStream().use { input.copyTo(it) } }
            part.renameTo(file)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private const val SETTLE_AFTER_SWAP_MS = 20_000L
}
