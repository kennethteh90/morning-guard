package com.morningguard.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.ui.components.ScreenScaffold
import com.morningguard.domain.history.MorningDayOutcome
import com.morningguard.domain.history.MorningDayRecord
import com.morningguard.domain.history.MorningGoalTimeFormat

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Streak & stats", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        when {
                            state.showStepsStat ->
                                "Days you finish the morning goal count toward your streak. Step counts update while the goal screen is open."
                            else ->
                                "Days you finish the morning goal count toward your streak. Time adds up while the goal screen is open (Bible and focus goals)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.showTimeOnGoalStat) {
                        StatCard(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Time on morning goal",
                            value = MorningGoalTimeFormat.format(state.totalGoalTimeSeconds),
                        )
                    }
                    StatCard(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Total Bible reading",
                        value = MorningGoalTimeFormat.format(state.totalBibleReadingSeconds),
                    )
                    if (state.showStepsStat) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = "Steps today",
                                value = state.todaySteps.toString(),
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = "Total steps logged",
                                value = state.totalGoalSteps.toString(),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "Current streak",
                            value = "${state.currentStreak} day(s)",
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "Best streak",
                            value = "${state.bestStreak} day(s)",
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "Goals completed",
                            value = state.completedDays.toString(),
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "Emergency bypass",
                            value = state.bypassDays.toString(),
                        )
                    }
                }
            }
            if (state.recent.isEmpty()) {
                item {
                    Text(
                        "No history yet — complete a morning goal or use emergency bypass to start tracking.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                item {
                    Text(
                        "History",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(state.recent, key = { "${it.dateKey}-${it.outcome}" }) { record ->
                    HistoryRow(record = record, showTime = state.showTimeOnGoalStat, showSteps = state.showStepsStat)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun HistoryRow(
    record: MorningDayRecord,
    showTime: Boolean,
    showSteps: Boolean,
) {
    val outcomeLabel = when (record.outcome) {
        MorningDayOutcome.GOAL_COMPLETED -> "Goal completed"
        MorningDayOutcome.EMERGENCY_BYPASS -> "Emergency bypass"
    }
    val timeLabel = MorningGoalTimeFormat.format(record.timeOnGoalSeconds)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(record.dateKey, style = MaterialTheme.typography.titleMedium)
                if (showTime && record.timeOnGoalSeconds > 0) {
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showSteps && record.stepsOnGoal > 0) {
                    Text(
                        "${record.stepsOnGoal} steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(outcomeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
