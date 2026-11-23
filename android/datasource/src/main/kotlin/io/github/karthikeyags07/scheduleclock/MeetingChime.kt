package io.github.karthikeyags07.scheduleclock

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.Settings
import android.util.Log

/**
 * The meeting chime (RULES H7, 2026-11-20): "Felt piano", two muted piano notes going up (E5 then A5, 170 ms apart,
 * ~1 s), synthesised for CalenTime (chosen after the "Tubular" wind chime and "Pulse" the same day). It plays 2 min
 * before a meeting starts and 5 min before it ends (the phone app's Settings → Sounds, on by default), and on the
 * debug link `app://<package>/chime`.
 *
 * Played as an alarm sound: on the Galaxy Watch8 a notification-event sound from an app went to the speaker but
 * was never heard, while alarm and media sounds were. An alarm sound ignores the watch's sound mode, so the chime
 * checks it itself and stays quiet on silent or vibrate, in Do Not Disturb and in theatre mode.
 */
object MeetingChime {
    fun play(context: Context, usage: Int = AudioAttributes.USAGE_ALARM, force: Boolean = false) {
        if (!force && !soundAllowed(context)) return Log.i(TAG, "chime: held back (watch on silent, vibrate, Do Not Disturb or theatre mode)").let { }
        val player = MediaPlayer.create(context.applicationContext, R.raw.chime_felt,
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(), 0) ?: run { Log.w(TAG, "chime: couldn't load the sound"); return }
        player.setOnCompletionListener { it.release() }
        player.start()
        Log.i(TAG, "chime: playing felt piano (usage $usage)")
    }

    /** The watch's sound is on: ringer normal, no Do Not Disturb, not in theatre mode. */
    fun soundAllowed(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val dnd = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val theatre = Settings.Global.getInt(context.contentResolver, "theater_mode_on", 0) == 1
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && !dnd && !theatre
    }
}
