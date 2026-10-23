import XCTest
@testable import MorningGuardCore

final class GoalCompletionVerifierTests: XCTestCase {
    private let verifier = GoalCompletionVerifier()

    func testTimedFocusRequiresOnScreenSecondsNotWallClock() {
        let progress = MorningGoalProgress(
            dateKey: "2026-10-13",
            config: MorningGoalConfig(type: .timedFocus, focusMinutes: 5),
            startedAtEpochMs: 0,
            onScreenSeconds: 4 * 60 + 59
        )
        XCTAssertFalse(verifier.canMarkComplete(progress: progress, activeConfig: progress.config, currentSteps: nil))
        let done = MorningGoalProgress(
            dateKey: progress.dateKey,
            config: progress.config,
            startedAtEpochMs: progress.startedAtEpochMs,
            onScreenSeconds: 5 * 60
        )
        XCTAssertTrue(verifier.canMarkComplete(progress: done, activeConfig: done.config, currentSteps: nil))
    }

    func testBibleChapterRequiresOnScreenSeconds() {
        let progress = MorningGoalProgress(
            dateKey: "2026-10-13",
            config: MorningGoalConfig(type: .bibleChapter),
            onScreenSeconds: 59
        )
        XCTAssertFalse(verifier.canMarkComplete(progress: progress, activeConfig: progress.config, currentSteps: nil))
        let done = MorningGoalProgress(
            dateKey: progress.dateKey,
            config: progress.config,
            onScreenSeconds: 60
        )
        XCTAssertTrue(verifier.canMarkComplete(progress: done, activeConfig: progress.config, currentSteps: nil))
    }

    func testStepCountUsesAccumulatedStepsOnProgress() {
        let progress = MorningGoalProgress(
            dateKey: "2026-10-13",
            config: MorningGoalConfig(type: .stepCount, stepTarget: 500),
            stepsAccumulated: 499
        )
        XCTAssertFalse(verifier.canMarkComplete(progress: progress, activeConfig: progress.config, currentSteps: nil))
        let done = MorningGoalProgress(
            dateKey: progress.dateKey,
            config: progress.config,
            stepsAccumulated: 500
        )
        XCTAssertTrue(verifier.canMarkComplete(progress: done, activeConfig: progress.config, currentSteps: nil))
    }
}
