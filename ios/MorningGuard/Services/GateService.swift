import Foundation
import MorningGuardCore
import Observation

@MainActor
@Observable
final class GateService {
    private let settingsStore: SettingsStore
    private let evaluator = MorningGateEvaluator()
    private let completionVerifier = GoalCompletionVerifier()
    private let calendar: Calendar

    var evaluation: GateEvaluation

    init(settingsStore: SettingsStore, timeZone: TimeZone = .current) {
        self.settingsStore = settingsStore
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        self.calendar = cal
        self.evaluation = Self.evaluate(settingsStore: settingsStore, evaluator: evaluator)
    }

    func refresh() {
        evaluation = Self.evaluate(settingsStore: settingsStore, evaluator: evaluator)
        settingsStore.mirrorGateEvaluation(evaluation)
    }

    func startGoalIfNeeded() -> MorningGoalProgress {
        let dateKey = Self.dateKey(for: Date(), calendar: calendar)
        let store = settingsStore.goalProgressStore
        if let existing = store.progress, existing.dateKey == dateKey {
            let synced = syncProgressWithSettings(existing)
            if synced != existing {
                settingsStore.updateGoalProgress { $0.withProgress(synced) }
            }
            refresh()
            return synced
        }
        let fresh = MorningGoalProgress(
            dateKey: dateKey,
            config: settingsStore.userSettings.morningGoal,
            startedAtEpochMs: Self.nowMs()
        )
        settingsStore.updateGoalProgress { $0.withProgress(fresh) }
        refresh()
        return fresh
    }

    func incrementOnScreenSeconds() {
        guard settingsStore.userSettings.morningGoal.type != .stepCount else { return }
        let progress = startGoalIfNeeded()
        guard !progress.isComplete else { return }
        let updated = progress.withOnScreenSeconds(progress.onScreenSeconds + 1)
        settingsStore.updateGoalProgress { $0.withProgress(updated) }
        tryAutoComplete(progress: updated, currentSteps: nil)
    }

    func syncStepCount(_ totalToday: Int) {
        guard settingsStore.userSettings.morningGoal.type == .stepCount else { return }
        var progress = startGoalIfNeeded()
        guard !progress.isComplete else { return }

        if progress.stepsAtStart == nil {
            progress.stepsAtStart = totalToday
        }
        let baseline = progress.stepsAtStart ?? totalToday
        let delta = max(0, totalToday - baseline)
        let merged = progress.withStepsAccumulated(max(progress.stepsAccumulated, delta))
        if merged != progress {
            settingsStore.updateGoalProgress { $0.withProgress(merged) }
            tryAutoComplete(progress: merged, currentSteps: totalToday)
        }
    }

    func canComplete(currentSteps: Int?) -> Bool {
        guard let progress = settingsStore.goalProgressStore.progress else { return false }
        return completionVerifier.canMarkComplete(
            progress: progress,
            activeConfig: settingsStore.userSettings.morningGoal,
            currentSteps: currentSteps
        )
    }

    func markComplete() {
        guard let progress = settingsStore.goalProgressStore.progress, !progress.isComplete else { return }
        let completed = progress.withCompleted(at: Self.nowMs())
        settingsStore.updateGoalProgress { $0.withProgress(completed) }
        refresh()
    }

    func activateEmergencyBypass(expiresAtMs: Int64) {
        settingsStore.updateGoalProgress { store in
            var copy = store
            copy.emergencyBypassUntilEpochMs = expiresAtMs
            return copy
        }
        refresh()
    }

    private func tryAutoComplete(progress: MorningGoalProgress, currentSteps: Int?) {
        if completionVerifier.canMarkComplete(
            progress: progress,
            activeConfig: settingsStore.userSettings.morningGoal,
            currentSteps: currentSteps
        ) {
            markComplete()
        } else {
            refresh()
        }
    }

    private func syncProgressWithSettings(_ progress: MorningGoalProgress) -> MorningGoalProgress {
        let config = settingsStore.userSettings.morningGoal
        if progress.config == config { return progress }
        return progress.withConfig(config)
    }

    private static func evaluate(settingsStore: SettingsStore, evaluator: MorningGateEvaluator) -> GateEvaluation {
        evaluator.evaluate(
            window: settingsStore.userSettings.morningWindow,
            progress: settingsStore.goalProgressStore.progress,
            emergencyBypassActiveUntilEpochMs: settingsStore.goalProgressStore.emergencyBypassUntilEpochMs
        )
    }

    private static func dateKey(for date: Date, calendar: Calendar) -> String {
        MorningGateEvaluator.dateKey(for: date, calendar: calendar)
    }

    private static func nowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}

private extension GoalProgressStore {
    func withProgress(_ progress: MorningGoalProgress?) -> GoalProgressStore {
        var copy = self
        copy.progress = progress
        return copy
    }
}

private extension MorningGoalProgress {
    func withOnScreenSeconds(_ seconds: Int) -> MorningGoalProgress {
        var copy = self
        copy.onScreenSeconds = seconds
        return copy
    }

    func withStepsAccumulated(_ steps: Int) -> MorningGoalProgress {
        var copy = self
        copy.stepsAccumulated = steps
        return copy
    }

    func withCompleted(at epochMs: Int64) -> MorningGoalProgress {
        var copy = self
        copy.completedAtEpochMs = epochMs
        return copy
    }

    func withConfig(_ config: MorningGoalConfig) -> MorningGoalProgress {
        var copy = self
        copy.config = config
        return copy
    }
}
