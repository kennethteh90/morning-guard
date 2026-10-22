package com.morningguard.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.bible.BibleVerseCopyFormat

@Composable
fun ConfirmRemoveHighlightDialog(
    highlight: BibleHighlight,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ref = BibleVerseCopyFormat.referenceLine(highlight)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove highlight?") },
        text = {
            Text(
                "This removes the highlight and any note for $ref. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
