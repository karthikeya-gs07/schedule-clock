package io.github.karthikeyags07.scheduleclock.phone

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.Wearable
import kotlin.concurrent.thread

/**
 * The phone app's only screen: asks for calendar access, starts the background sync, and shows whether
 * it's working (last sync, events sent, watch connected). Most people open it once.
 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var action: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(24)
        status = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); setLineSpacing(0f, 1.3f) }
        action = Button(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = getString(R.string.app_name); setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f) })
            addView(TextView(context).apply {
                text = getString(R.string.intro)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(8), 0, dp(24))
            })
            addView(status)
            addView(action, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
        })
        if (!CalendarSync.hasPermission(this)) requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
    }

    override fun onResume() {
        super.onResume()
        if (CalendarSync.hasPermission(this)) SyncWorker.schedule(this)
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (CalendarSync.hasPermission(this)) SyncWorker.schedule(this)
        refresh()
    }

    private fun refresh() {
        if (!CalendarSync.hasPermission(this)) {
            status.text = getString(R.string.no_permission)
            action.text = getString(R.string.allow)
            action.setOnClickListener {
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
                    requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
                } else {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
            }
            return
        }
        action.text = getString(R.string.sync_now)
        action.setOnClickListener {
            action.isEnabled = false
            thread {
                val result = runCatching { CalendarSync.send(this) }
                runOnUiThread {
                    action.isEnabled = true
                    if (result.isFailure) status.text = getString(R.string.sync_failed, result.exceptionOrNull()?.message)
                    else refresh()
                }
            }
        }
        val prefs = getSharedPreferences("sync", Context.MODE_PRIVATE)
        val last = prefs.getLong("lastSync", 0L)
        val synced = if (last == 0L) getString(R.string.never_synced)
            else getString(R.string.last_sync, DateUtils.getRelativeTimeSpanString(last), prefs.getInt("lastCount", 0))
        status.text = synced
        Wearable.getNodeClient(this).connectedNodes.addOnCompleteListener { task ->
            val watches = task.result?.map { it.displayName }.orEmpty()
            status.text = synced + "\n" + if (watches.isEmpty()) getString(R.string.no_watch)
                else getString(R.string.watch_connected, watches.joinToString())
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
