import Foundation
import MorningGuardCore

extension SettingsStore {
    func recordMorningOutcome(
        dateKey: String,
        outcome: MorningDayOutcome,
        trackGoalTime: Bool = true,
        stepsOnGoal: Int = 0
    ) {
        let seconds = trackGoalTime ? goalTimeForDate(dateKey: dateKey) : 0
        updateGoalProgress { store in
            var copy = store
            copy.morningHistory = MorningHistoryMerge.mergeMorningRecord(
                history: store.morningHistory,
                dateKey: dateKey,
                outcome: outcome,
                timeOnGoalSeconds: seconds,
                stepsOnGoal: stepsOnGoal
            )
            return copy
        }
    }

    func recordGoalStepsForDate(dateKey: String, steps: Int) {
        guard steps > 0 else { return }
        updateGoalProgress { store in
            let merged = MorningHistoryMerge.mergeGoalSteps(
                map: store.goalStepsByDate,
                dateKey: dateKey,
                steps: steps
            )
            guard merged != store.goalStepsByDate else { return store }
            var copy = store
            copy.goalStepsByDate = merged
            return copy
        }
    }

    func recordGoalScreenSeconds(dateKey: String, seconds: Int) {
        guard seconds > 0 else { return }
        updateGoalProgress { store in
            let merged = MorningHistoryMerge.mergeGoalTime(
                map: store.goalTimeSecondsByDate,
                dateKey: dateKey,
                seconds: seconds
            )
            guard merged != store.goalTimeSecondsByDate else { return store }
            var copy = store
            copy.goalTimeSecondsByDate = merged
            return copy
        }
    }

    func goalTimeForDate(dateKey: String) -> Int {
        let fromMap = goalProgressStore.goalTimeSecondsByDate[dateKey] ?? 0
        let fromProgress = goalProgressStore.progress
            .flatMap { $0.dateKey == dateKey ? $0.onScreenSeconds : nil } ?? 0
        return max(fromMap, fromProgress)
    }

    func advanceBibleReadingAfterComplete() {
        let goal = userSettings.morningGoal
        guard goal.type == .bibleChapter else { return }
        let next = BibleCanon.advance(bookName: goal.bibleBook, chapter: goal.bibleChapter)
        updateUserSettings { settings in
            var copy = settings
            copy.morningGoal.bibleBook = next.0
            copy.morningGoal.bibleChapter = next.1
            return copy
        }
    }
}
