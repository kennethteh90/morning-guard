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
        settingsStore.recordGoalScreenSeconds(dateKey: updated.dateKey, seconds: updated.onScreenSeconds)
        refresh()
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
            settingsStore.recordGoalStepsForDate(dateKey: merged.dateKey, steps: merged.stepsAccumulated)
            refresh()
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
        let config = settingsStore.userSettings.morningGoal
        let now = Self.nowMs()
        if config.type == .stepCount {
            let completed = progress.withCompleted(at: now)
            settingsStore.updateGoalProgress { $0.withProgress(completed) }
            settingsStore.recordMorningOutcome(
                dateKey: progress.dateKey,
                outcome: .goalCompleted,
                trackGoalTime: false,
                stepsOnGoal: progress.stepsAccumulated
            )
        } else {
            let completed = progress.withCompleted(at: now).withConfig(config)
            settingsStore.updateGoalProgress { $0.withProgress(completed) }
            settingsStore.recordMorningOutcome(dateKey: progress.dateKey, outcome: .goalCompleted)
        }
        refresh()
    }

    /// Advances the assigned Bible chapter after the user leaves the goal screen (keeps today's chapter while they keep reading).
    func finalizeBibleReadingAfterGoalSessionIfNeeded() {
        guard let progress = settingsStore.goalProgressStore.progress,
              progress.isComplete,
              progress.config.type == .bibleChapter else { return }
        let goal = settingsStore.userSettings.morningGoal
        guard goal.type == .bibleChapter,
              goal.bibleBook.caseInsensitiveCompare(progress.config.bibleBook) == .orderedSame,
              goal.bibleChapter == progress.config.bibleChapter else { return }
        settingsStore.advanceBibleReadingAfterComplete()
    }

    func activateEmergencyBypass(expiresAtMs: Int64) {
        settingsStore.updateGoalProgress { store in
            var copy = store
            copy.emergencyBypassUntilEpochMs = expiresAtMs
            return copy
        }
        refresh()
    }

    private func syncProgressWithSettings(_ progress: MorningGoalProgress) -> MorningGoalProgress {
        let config = settingsStore.userSettings.morningGoal
        if progress.config == config { return progress }
        return progress.withConfig(config)
    }

    private static func evaluate(settingsStore: SettingsStore, evaluator: MorningGateEvaluator) -> GateEvaluation {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let peaceUntil = PeaceSessionPolicy.activeUntil(
            storedUntilEpochMs: settingsStore.goalProgressStore.peaceSessionUntilEpochMs,
            nowMs: nowMs
        )
        return evaluator.evaluate(
            window: settingsStore.userSettings.morningWindow,
            progress: settingsStore.goalProgressStore.progress,
            emergencyBypassActiveUntilEpochMs: settingsStore.goalProgressStore.emergencyBypassUntilEpochMs,
            peaceSessionUntilEpochMs: peaceUntil
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
