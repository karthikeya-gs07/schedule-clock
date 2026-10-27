package io.github.karthikeyags07.scheduleclock.phone

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration

/**
 * Sends the calendar to the watch. Runs every 15 minutes (the shortest period Android allows, and how a
 * day rolls into the next), and a few seconds after any calendar change. A content trigger fires once,
 * so each run arms the next one.
 */
class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        CalendarSync.send(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "sync failed: ${e.message}")
        Result.retry()
    } finally {
        armChangeTrigger(applicationContext)
    }

    companion object {
        private const val PERIODIC = "calendar-periodic"
        private const val ON_CHANGE = "calendar-changed"

        /** Called when the app opens: keeps the periodic sync, re-arms the change trigger, syncs now. */
        fun schedule(context: Context) {
            val work = WorkManager.getInstance(context)
            work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofMinutes(15)).build())
            work.enqueue(OneTimeWorkRequestBuilder<SyncWorker>().build())
        }

        private fun armChangeTrigger(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelAllWorkByTag(ON_CHANGE)
            work.enqueue(OneTimeWorkRequestBuilder<SyncWorker>()
                .addTag(ON_CHANGE)
                .setConstraints(Constraints.Builder()
                    .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                    .setTriggerContentMaxDelay(Duration.ofSeconds(30))
                    .build())
                .build())
        }
    }
}
