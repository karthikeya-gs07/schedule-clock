package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.runtime.Composable

/**
 * Update prompts (RULES A14), shown when the app opens: first a newer phone app on Play, then a watch app older
 * than the one released with this phone app; and the answer to Settings → Check for updates. "Later" hides a prompt until the app is opened again.
 */
@Composable
fun UpdatePrompts(m: AppModel, updateApp: () -> Unit) {
    when {
        m.appUpdateReady && !m.appUpdateDismissed -> UpdateDialog(
            title = "Update available",
            text = "A new version of CalenTime is ready. Update now to get the latest fixes and features.",
            confirm = "Update",
            onConfirm = { m.appUpdateDismissed = true; updateApp() },
            onLater = { m.appUpdateDismissed = true },
        )
        m.notice != null -> m.notice?.let { (title, text) ->
            CalenPrompt(title, text, "Done", onConfirm = { m.notice = null }, onClose = { m.notice = null }, later = null)
        }
        m.watchUpdateReady && !m.watchUpdateDismissed -> UpdateDialog(
            title = "Watch app update",
            text = "The latest version of CalenTime for your watch is available. Install it from here: " +
                "the Play Store opens on your watch, then tap Update there.",
            confirm = "Update",
            onConfirm = { m.updateWatchApp() },
            onLater = { m.watchUpdateDismissed = true },
        )
    }
}

@Composable
private fun UpdateDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onLater: () -> Unit) =
    CalenPrompt(title, text, confirm, onConfirm, onLater)
