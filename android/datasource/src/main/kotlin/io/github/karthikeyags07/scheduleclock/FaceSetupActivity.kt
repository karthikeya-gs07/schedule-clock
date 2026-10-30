package io.github.karthikeyags07.scheduleclock

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Puts the Calen-Time face on the watch (Watch Face Push, PRD §6.5) in a colourway, and with [EXTRA_ACTIVATE]
 * also makes it the watch face, asking for that permission first. It shows a line of progress, since the
 * permission prompt needs a visible screen. For now it's opened by the debug link
 * `app://<package>/face?colour=mint&activate=1`; the phone app will open it during setup (RULES A1).
 */
class FaceSetupActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        setContentView(message)
        if (!FacePush.supported()) return finishWith(getString(R.string.face_needs_wear6))
        val colourway = intent.getStringExtra(EXTRA_COLOUR)?.takeIf { it in FacePush.COLOURWAYS } ?: "amber"
        message.text = getString(R.string.face_installing)
        launch {
            try {
                FacePush.install(applicationContext, colourway)
            } catch (e: Exception) {
                Log.w(TAG, "face push: install failed", e)
                return@launch finishWith(getString(R.string.face_failed))
            }
            when {
                !intent.getBooleanExtra(EXTRA_ACTIVATE, false) -> finishWith(getString(R.string.face_installed))
                FacePush.canActivate(this@FaceSetupActivity) -> activate()
                else -> requestPermissions(arrayOf(FacePush.ACTIVATE_PERMISSION), REQUEST)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        launch { activate() }
    }

    private suspend fun activate() {
        val active = FacePush.activate(applicationContext)
        Log.i(TAG, "face push: ${FacePush.status(applicationContext)}")
        finishWith(getString(if (active) R.string.face_active else R.string.face_pick_yourself))
    }

    private fun finishWith(text: String) {
        message.text = text
        CoroutineScope(Dispatchers.Main).launch { delay(2500); finish() }
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_COLOUR = "colour"
        const val EXTRA_ACTIVATE = "activate"
        private const val REQUEST = 2
    }
}
