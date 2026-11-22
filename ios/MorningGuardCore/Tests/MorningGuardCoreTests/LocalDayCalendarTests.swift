import XCTest
@testable import MorningGuardCore

final class LocalDayCalendarTests: XCTestCase {
    func testDateKeyDiffers_whenTravelingFromSingaporeToVienna() {
        let singapore = TimeZone(identifier: "Asia/Singapore")!
        let vienna = TimeZone(identifier: "Europe/Vienna")!
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        let instant = utc.date(from: DateComponents(year: 2026, month: 11, day: 21, hour: 18))!

        XCTAssertEqual(LocalDayCalendar.dateKey(for: instant, timeZone: singapore), "2026-11-22")
        XCTAssertEqual(LocalDayCalendar.dateKey(for: instant, timeZone: vienna), "2026-11-21")
    }

    func testParseAndPreviousDay_respectsDstBoundary() {
        let vienna = TimeZone(identifier: "Europe/Vienna")!
        guard let day = LocalDayCalendar.parseDateKey("2026-03-29", timeZone: vienna) else {
            XCTFail("expected parse")
            return
        }
        XCTAssertEqual(LocalDayCalendar.dateKey(for: day, timeZone: vienna), "2026-03-29")
        let previous = LocalDayCalendar.previousDay(before: day, timeZone: vienna)
        XCTAssertEqual(LocalDayCalendar.dateKey(for: previous!, timeZone: vienna), "2026-03-28")
    }
}