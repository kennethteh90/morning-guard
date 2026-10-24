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
                    .accessibilityLabel(L10n.tr("a11y.stats.lead"))

                if state.showTimeOnGoalStat {
                    statCard(label: L10n.tr("stats.time_on_goal"), value: MorningGoalTimeFormat.format(seconds: state.totalGoalTimeSeconds))
                }

                if state.showStepsStat {
                    HStack(spacing: 12) {
                        statCard(label: L10n.tr("stats.steps_today"), value: "\(state.todaySteps)")
                        statCard(label: L10n.tr("stats.steps_total"), value: "\(state.totalGoalSteps)")
                    }
                }

                HStack(spacing: 12) {
                    statCard(
                        label: L10n.tr("stats.current_streak"),
                        value: L10n.tr("stats.streak_days_format", state.currentStreak)
                    )
                    statCard(
                        label: L10n.tr("stats.best_streak"),
                        value: L10n.tr("stats.streak_days_format", state.bestStreak)
                    )
                }

                HStack(spacing: 12) {
                    statCard(label: L10n.tr("stats.goals_completed"), value: "\(state.completedDays)")
                    statCard(label: L10n.tr("stats.emergency_bypass"), value: "\(state.bypassDays)")
                }

                if state.recent.isEmpty {
                    Text(L10n.tr("stats.empty_history"))
                        .foregroundStyle(.secondary)
                } else {
                    Text(L10n.tr("stats.history"))
                        .font(.title2.bold())
                        .accessibilityAddTraits(.isHeader)
                    ForEach(state.recent) { record in
                        historyRow(record)
                    }
                }
            }
            .padding(MGTheme.standardPadding)
        }
        .background(MGTheme.background)
        .navigationTitle(L10n.tr("stats.title"))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityLabel(L10n.tr("a11y.stats.title"))
        .accessibilityHint(L10n.tr("a11y.stats.title.hint"))
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
        .accessibilityElement(children: .combine)
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
                        Text(L10n.tr("stats.steps_row_format", record.stepsOnGoal))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Text(
                    record.outcome == .goalCompleted
                        ? L10n.tr("stats.outcome.completed")
                        : L10n.tr("stats.outcome.bypass")
                )
                .font(.subheadline)
                .foregroundStyle(MGTheme.accent)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(L10n.tr("a11y.stats.history_row"))
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
            return L10n.tr("stats.lead.steps")
        }
        return L10n.tr("stats.lead.time")
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
