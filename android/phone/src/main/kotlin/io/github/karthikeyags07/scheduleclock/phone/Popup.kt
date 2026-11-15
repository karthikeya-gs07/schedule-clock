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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The app's pop-up (RULES §12): the watch status card's look — a dark rounded card with a hairline border, the
 * title, the text and content, and buttons at the bottom (no × in the corner). Used instead of the system's Material dialog.
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
            Text(title, style = BodyStyle.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium))
            text?.let { Text(it, style = SubStyle.copy(color = Palette.body), modifier = Modifier.padding(top = 10.dp)) }
            content()
        }
    }

/**
 * A pop-up with buttons at the bottom. With nothing to do ([later] null), one full-width **Done**-style button; with
 * an action, two side by side: the quiet close button on the left, the action in the accent on the right.
 */
@Composable
fun CalenPrompt(title: String, text: String, confirm: String, onConfirm: () -> Unit, onClose: () -> Unit, later: String? = "Later") =
    CalenPopup(title, onClose, text) {
        Spacer(Modifier.height(20.dp))
        if (later == null) PrimaryButton(confirm, Modifier.fillMaxWidth(), onClick = onConfirm)
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(later, Modifier.weight(1f), onClose)
            PrimaryButton(confirm, Modifier.weight(1f), onClick = onConfirm)
        }
    }

/** The quiet button beside an action: a soft grey pill the height of [PrimaryButton]. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = .08f))
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = BodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium))
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
