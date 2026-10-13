package com.morningguard.app.ui.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.domain.model.GoalType

@Composable
fun MorningGoalScreen(
    onBack: () -> Unit,
    viewModel: MorningGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Morning goal", style = MaterialTheme.typography.headlineSmall)
        Text("Type: ${state.config.type}")

        when (state.config.type) {
            GoalType.BIBLE_CHAPTER -> {
                if (state.loadingBible) {
                    CircularProgressIndicator()
                } else {
                    Text(state.bibleText.ifBlank { "Loading chapter…" })
                    Button(onClick = { viewModel.loadBibleIfNeeded() }) { Text("Reload chapter") }
                }
            }
            GoalType.TIMED_FOCUS -> {
                Text("Stay on this screen for ${state.config.focusMinutes} minutes.")
            }
            GoalType.STEP_COUNT -> {
                Text("Steps: ${state.currentSteps ?: "Sensor unavailable"}")
                Text("Target additional steps: ${state.config.stepTarget}")
            }
        }

        state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { viewModel.completeGoal() },
            enabled = state.canComplete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Mark goal complete")
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
