package io.github.karthikeyags07.scheduleclock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/**
 * Asks for calendar access (READ_CALENDAR), the one permission the face needs. Opened by a tap on the
 * ring while access is missing, or from the app list. Once answered, the face redraws and this closes.
 * If Android won't ask again (denied twice), a tap here opens the app's settings instead.
 */
class PermissionActivity : Activity() {
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = getString(R.string.permission_why)
        }
        setContentView(message)
        if (CalendarSource.hasPermission(this)) done() else ask()
    }

    private fun ask() = requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (CalendarSource.hasPermission(this)) return done()
        message.text = getString(R.string.permission_denied)
        message.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) ask()
            else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::message.isInitialized && CalendarSource.hasPermission(this)) done()   // back from settings
    }

    private fun done() {
        SpikeSchedule.requestUpdate(applicationContext)
        finish()
    }

    private companion object {
        const val REQUEST = 1
    }
}
