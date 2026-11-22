import XCTest
@testable import MorningGuardCore

final class MorningGateEvaluatorTimeZoneTests: XCTestCase {
    func testMorningWindowUsesLocalWallClockAfterZoneChange() {
        let singapore = TimeZone(identifier: "Asia/Singapore")!
        let vienna = TimeZone(identifier: "Europe/Vienna")!
        var viennaCalendar = Calendar(identifier: .gregorian)
        viennaCalendar.timeZone = vienna
        let sampleInstant = viennaCalendar.date(
            from: DateComponents(year: 2026, month: 11, day: 22, hour: 7, minute: 30)
        )!
        var currentZone: TimeZone = singapore
        let evaluator = MorningGateEvaluator(timeZone: { currentZone }) { sampleInstant }
        let window = MorningWindow(startHour: 6, startMinute: 0, endHour: 9, endMinute: 0)

        XCTAssertEqual(
            evaluator.evaluate(window: window, progress: nil, emergencyBypassActiveUntilEpochMs: nil).state,
            .inactive
        )

        currentZone = vienna
        let travel = evaluator.evaluate(window: window, progress: nil, emergencyBypassActiveUntilEpochMs: nil)
        XCTAssertEqual(travel.state, .locked)
        XCTAssertEqual(travel.dateKey, "2026-11-22")
        XCTAssertEqual(travel.reason, "morning_window")
    }
}