import Foundation

public struct GoalCompletionVerifier: Sendable {
    private let now: () -> Date

    public init(now: @escaping () -> Date = Date.init) {
        self.now = now
    }

    public func bibleChapterComplete(progress: MorningGoalProgress, minimumReadSeconds: Int) -> Bool {
        progress.onScreenSeconds >= minimumReadSeconds
    }

    public func timedFocusComplete(progress: MorningGoalProgress, requiredMinutes: Int) -> Bool {
        progress.onScreenSeconds >= requiredMinutes * 60
    }

    public func stepCountComplete(progress: MorningGoalProgress, target: Int) -> Bool {
        progress.stepsAccumulated >= target
    }

    public func canMarkComplete(
        progress: MorningGoalProgress,
        activeConfig: MorningGoalConfig,
        currentSteps: Int?
    ) -> Bool {
        if progress.isComplete {
            return false
        }
        switch activeConfig.type {
        case .bibleChapter:
            return bibleChapterComplete(progress: progress, minimumReadSeconds: 60)
        case .timedFocus:
            return timedFocusComplete(progress: progress, requiredMinutes: activeConfig.focusMinutes)
        case .stepCount:
            return stepCountComplete(progress: progress, target: activeConfig.stepTarget)
        }
    }
}
