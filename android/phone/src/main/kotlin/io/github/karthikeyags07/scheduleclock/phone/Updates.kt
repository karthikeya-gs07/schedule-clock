package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Update prompts (RULES A14), shown when the app opens: first a newer phone app on Play, then a watch app older
 * than the one released with this phone app. "Later" hides a prompt until the app is opened again.
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
        m.watchUpdateReady && !m.watchUpdateDismissed -> UpdateDialog(
            title = "Watch app update",
            text = "The latest version of CalenTime for your watch is available. Install it from here: " +
                "the Play Store opens on your watch, then tap Update there.",
            confirm = "Update on watch",
            onConfirm = { m.updateWatchApp() },
            onLater = { m.watchUpdateDismissed = true },
        )
    }
}

@Composable
private fun UpdateDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onLater: () -> Unit) = AlertDialog(
    onDismissRequest = onLater,
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, style = BodyStyle.copy(color = LocalAccent.current, fontWeight = FontWeight.Medium)) } },
    dismissButton = { TextButton(onClick = onLater) { Text("Later", style = BodyStyle.copy(color = Palette.muted)) } },
    title = { Text(title, style = BodyStyle.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)) },
    text = { Text(text, style = SubStyle.copy(color = Palette.body)) },
    containerColor = Palette.surface,
    shape = RoundedCornerShape(28.dp),
)
