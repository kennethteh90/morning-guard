import MorningGuardCore
import SwiftUI

struct StatsView: View {
    @Environment(AppState.self) private var appState

    private var state: StatsUiState {
        StatsUiState.make(
            settings: appState.settingsStore.userSettings,
            goalStore: appState.settingsStore.goalProgressStore
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(state.leadCopy)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                if state.showTimeOnGoalStat {
                    statCard(label: "Time on morning goal", value: MorningGoalTimeFormat.format(seconds: state.totalGoalTimeSeconds))
                }

                if state.showStepsStat {
                    HStack(spacing: 12) {
                        statCard(label: "Steps today", value: "\(state.todaySteps)")
                        statCard(label: "Total steps logged", value: "\(state.totalGoalSteps)")
                    }
                }

                HStack(spacing: 12) {
                    statCard(label: "Current streak", value: "\(state.currentStreak) day(s)")
                    statCard(label: "Best streak", value: "\(state.bestStreak) day(s)")
                }

                HStack(spacing: 12) {
                    statCard(label: "Goals completed", value: "\(state.completedDays)")
                    statCard(label: "Emergency bypass", value: "\(state.bypassDays)")
                }

                if state.recent.isEmpty {
                    Text("No history yet — complete a morning goal or use emergency bypass to start tracking.")
                        .foregroundStyle(.secondary)
                } else {
                    Text("History")
                        .font(.title2.bold())
                    ForEach(state.recent) { record in
                        historyRow(record)
                    }
                }
            }
            .padding(MGTheme.standardPadding)
        }
        .background(MGTheme.background)
        .navigationTitle("Streak & stats")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func statCard(label: String, value: String) -> some View {
        MGCard {
            VStack(alignment: .leading, spacing: 4) {
                Text(label)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.title3.bold())
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func historyRow(_ record: MorningDayRecord) -> some View {
        MGCard {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(record.dateKey)
                        .font(.headline)
                    if state.showTimeOnGoalStat, record.timeOnGoalSeconds > 0 {
                        Text(MorningGoalTimeFormat.format(seconds: record.timeOnGoalSeconds))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if state.showStepsStat, record.stepsOnGoal > 0 {
                        Text("\(record.stepsOnGoal) steps")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Text(record.outcome == .goalCompleted ? "Goal completed" : "Emergency bypass")
                    .font(.subheadline)
                    .foregroundStyle(MGTheme.accent)
            }
        }
    }
}

private struct StatsUiState {
    var morningGoalType: GoalType
    var currentStreak: Int
    var bestStreak: Int
    var completedDays: Int
    var bypassDays: Int
    var totalGoalTimeSeconds: Int
    var todaySteps: Int
    var totalGoalSteps: Int
    var recent: [MorningDayRecord]

    var showTimeOnGoalStat: Bool { morningGoalType != .stepCount }
    var showStepsStat: Bool { morningGoalType == .stepCount }

    var leadCopy: String {
        if showStepsStat {
            return "Days you finish the morning goal count toward your streak. Step counts update while the goal screen is open."
        }
        return "Days you finish the morning goal count toward your streak. Time adds up while the goal screen is open (Bible and focus goals)."
    }

    static func make(settings: UserSettings, goalStore: GoalProgressStore) -> StatsUiState {
        let todayKey = MorningGateEvaluator.dateKey(for: Date(), calendar: .current)
        let history = goalStore.morningHistory
        let progress = goalStore.progress
        let goalType = settings.morningGoal.type
        let activeProgress = progress.flatMap { !$0.isComplete && $0.dateKey == todayKey ? $0 : nil }
        let activeSeconds = goalType != .stepCount ? activeProgress?.onScreenSeconds : nil
        let activeSteps = goalType == .stepCount ? activeProgress?.stepsAccumulated : nil

        return StatsUiState(
            morningGoalType: goalType,
            currentStreak: MorningStreakCalculator.currentCompletedStreak(history: history, todayKey: todayKey),
            bestStreak: MorningStreakCalculator.bestCompletedStreak(history: history),
            completedDays: MorningStreakCalculator.countOutcome(history: history, outcome: .goalCompleted),
            bypassDays: MorningStreakCalculator.countOutcome(history: history, outcome: .emergencyBypass),
            totalGoalTimeSeconds: goalType == .stepCount ? 0 : MorningStreakCalculator.totalGoalTimeSeconds(
                dailySeconds: goalStore.goalTimeSecondsByDate,
                history: history,
                activeProgressSeconds: activeSeconds,
                activeDateKey: activeSeconds != nil ? todayKey : nil
            ),
            todaySteps: goalType == .stepCount
                ? (activeSteps ?? goalStore.goalStepsByDate[todayKey]
                    ?? history.first { $0.dateKey == todayKey }?.stepsOnGoal ?? 0)
                : 0,
            totalGoalSteps: goalType == .stepCount
                ? MorningStreakCalculator.totalGoalSteps(
                    dailySteps: goalStore.goalStepsByDate,
                    history: history,
                    activeProgressSteps: activeSteps,
                    activeDateKey: activeSteps != nil ? todayKey : nil
                )
                : 0,
            recent: history.sorted { $0.dateKey > $1.dateKey }
        )
    }
}
