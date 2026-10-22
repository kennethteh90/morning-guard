package com.morningguard.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WindowTimeAdjustRow(
    label: String,
    hour: Int,
    minute: Int,
    onHourMinus: () -> Unit,
    onHourPlus: () -> Unit,
    onMinuteMinus: () -> Unit,
    onMinutePlus: () -> Unit,
) {
    Text(label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    Text(
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onHourMinus, modifier = Modifier.weight(1f)) { Text("Hour −") }
        OutlinedButton(onClick = onHourPlus, modifier = Modifier.weight(1f)) { Text("Hour +") }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onMinuteMinus, modifier = Modifier.weight(1f)) { Text("Min −") }
        OutlinedButton(onClick = onMinutePlus, modifier = Modifier.weight(1f)) { Text("Min +") }
    }
}
