import XCTest
@testable import MorningGuardCore

final class MorningGoalConfigTests: XCTestCase {
    func testTimedFocusDefaultDurationIsTwoMinutes() {
        let config = MorningGoalConfig(type: .timedFocus)
        XCTAssertEqual(config.focusMinutes, 2)
    }
}
