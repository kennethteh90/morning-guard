import XCTest
@testable import MorningGuardCore

final class MorningGateEvaluatorTests: XCTestCase {
    private let timeZone = TimeZone(secondsFromGMT: 0)!

    private func date(year: Int, month: Int, day: Int, hour: Int, minute: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let components = DateComponents(
            calendar: calendar,
            timeZone: timeZone,
            year: year,
            month: month,
            day: day,
            hour: hour,
            minute: minute
        )
        return calendar.date(from: components)!
    }

    func testUnlockWhenGoalCompleteToday() {
        let sampleInstant = date(year: 2026, month: 10, day: 13, hour: 7, minute: 0)
        let evaluator = MorningGateEvaluator(timeZone: timeZone) { sampleInstant }
        let progress = MorningGoalProgress(
            dateKey: "2026-10-13",
            config: MorningGoalConfig(type: .timedFocus),
            completedAtEpochMs: Int64(sampleInstant.timeIntervalSince1970 * 1000)
        )
        let result = evaluator.evaluate(window: MorningWindow(), progress: progress, emergencyBypassActiveUntilEpochMs: nil)
        XCTAssertEqual(result.state, .unlocked)
    }

    func testLockedOvernightWindowBeforeGoalComplete() {
        let earlyMorning = date(year: 2026, month: 10, day: 13, hour: 2, minute: 30)
        let evaluator = MorningGateEvaluator(timeZone: timeZone) { earlyMorning }
        let result = evaluator.evaluate(
            window: MorningWindow(startHour: 1, endHour: 10),
            progress: MorningGoalProgress(
                dateKey: "2026-10-13",
                config: MorningGoalConfig(type: .timedFocus)
            ),
            emergencyBypassActiveUntilEpochMs: nil
        )
        XCTAssertEqual(result.state, .locked)
    }

    func testLockedDuringMorningWindowWithoutCompletion() {
        let sampleInstant = date(year: 2026, month: 10, day: 13, hour: 7, minute: 0)
        let evaluator = MorningGateEvaluator(timeZone: timeZone) { sampleInstant }
        let result = evaluator.evaluate(
            window: MorningWindow(startHour: 6, endHour: 9),
            progress: MorningGoalProgress(
                dateKey: "2026-10-13",
                config: MorningGoalConfig(type: .timedFocus)
            ),
            emergencyBypassActiveUntilEpochMs: nil
        )
        XCTAssertEqual(result.state, .locked)
    }

    func testEmergencyBypassUnlocks() {
        let sampleInstant = date(year: 2026, month: 10, day: 13, hour: 7, minute: 0)
        let sampleMs = Int64(sampleInstant.timeIntervalSince1970 * 1000)
        let evaluator = MorningGateEvaluator(timeZone: timeZone) { sampleInstant }
        let result = evaluator.evaluate(
            window: MorningWindow(startHour: 6, endHour: 9),
            progress: nil,
            emergencyBypassActiveUntilEpochMs: sampleMs + 1000
        )
        XCTAssertEqual(result.state, .unlocked)
    }
}
