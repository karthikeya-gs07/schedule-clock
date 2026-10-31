package io.github.karthikeyags07.scheduleclock.phone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import android.widget.RemoteViews
import java.util.Date
import kotlin.concurrent.thread

/**
 * Home-screen widget (RULES A12): CalenTime, when it last synced, and a round Sync button that sends the calendar
 * to the watch right away, without opening the app. Before setup (no calendar access) a tap opens the app.
 * The time shown is the clock time of the last sync, so it never goes stale; every sync (here, in the app or
 * in the background) refreshes it.
 */
class SyncWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_SYNC) return
        val app = context.applicationContext
        show(app, app.getString(R.string.widget_syncing))
        val done = goAsync()
        thread {
            try {
                CalendarSync.send(app)   // refreshes the widget itself when it's sent
            } catch (e: Exception) {
                Log.w(TAG, "widget sync failed: ${e.message}")
                show(app, app.getString(R.string.widget_failed))
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        private const val ACTION_SYNC = "io.github.karthikeyags07.scheduleclock.phone.WIDGET_SYNC"

        /** Shows the last sync time (or that setup is needed) on every CalenTime widget. */
        fun refresh(context: Context) {
            val last = context.getSharedPreferences("sync", Context.MODE_PRIVATE).getLong("lastSync", 0L)
            val text = when {
                !CalendarSync.hasPermission(context) -> context.getString(R.string.widget_setup)
                last == 0L -> context.getString(R.string.widget_never)
                else -> context.getString(R.string.widget_synced, DateFormat.getTimeFormat(context).format(Date(last)))
            }
            show(context, text)
        }

        private fun show(context: Context, status: String) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SyncWidget::class.java))
            if (ids.isEmpty()) return
            val ready = CalendarSync.hasPermission(context)
            val tap = if (ready) PendingIntent.getBroadcast(context, 0,
                Intent(context, SyncWidget::class.java).setAction(ACTION_SYNC), PendingIntent.FLAG_IMMUTABLE)
            else PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.widget_sync).apply {
                setTextViewText(R.id.widget_status, status)
                setOnClickPendingIntent(R.id.widget_sync, tap)
                setOnClickPendingIntent(R.id.widget_root, tap)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
