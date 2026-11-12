package io.github.karthikeyags07.scheduleclock.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The app's pop-up (RULES §12): the watch status card's look — a dark rounded card with a hairline border, the
 * title and close (×) on one row, then the text and content. Used instead of the system's Material dialog.
 */
@Composable
fun CalenPopup(title: String, onClose: () -> Unit, text: String? = null, content: @Composable ColumnScope.() -> Unit = {}) =
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 460.dp).fillMaxWidth()
                .clip(RoundedCornerShape(32.dp)).background(Color(0xFF1C1C20))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(32.dp))
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = BodyStyle.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center) { Icon(Icons.close, null, tint = Palette.muted, modifier = Modifier.size(20.dp)) }
            }
            text?.let { Text(it, style = SubStyle.copy(color = Palette.body), modifier = Modifier.padding(top = 10.dp)) }
            content()
        }
    }

/** A pop-up with one main action (accent button) and an optional quieter one under it. */
@Composable
fun CalenPrompt(title: String, text: String, confirm: String, onConfirm: () -> Unit, onClose: () -> Unit, later: String? = "Later") =
    CalenPopup(title, onClose, text) {
        Spacer(Modifier.height(20.dp))
        PrimaryButton(confirm, Modifier.fillMaxWidth(), onClick = onConfirm)
        if (later != null) Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(44.dp).clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
            Text(later, style = BodyStyle.copy(fontSize = 15.sp, color = Palette.muted))
        }
    }

/** Rows in a soft grouped block, with hairlines between them (as in the watch status card). */
@Composable
fun PopupRows(count: Int, row: @Composable (Int) -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .05f)),
        verticalArrangement = Arrangement.Top) {
        repeat(count) { i ->
            if (i > 0) Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .06f)))
            row(i)
        }
    }
