import Foundation

/// Local civil-day helpers. Resolves the device zone at call time for travel and DST.
public enum LocalDayCalendar {
    public static func currentTimeZone() -> TimeZone {
        .current
    }

    public static func gregorianCalendar(timeZone: TimeZone = currentTimeZone()) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar
    }

    public static func dateKey(for date: Date, timeZone: TimeZone = currentTimeZone()) -> String {
        MorningGateEvaluator.dateKey(for: date, calendar: gregorianCalendar(timeZone: timeZone))
    }

    public static func parseDateKey(_ key: String, timeZone: TimeZone = currentTimeZone()) -> Date? {
        let parts = key.split(separator: "-")
        guard parts.count == 3,
              let year = Int(parts[0]),
              let month = Int(parts[1]),
              let day = Int(parts[2]) else { return nil }
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = 12
        return gregorianCalendar(timeZone: timeZone).date(from: components)
    }

    public static func previousDay(before date: Date, timeZone: TimeZone = currentTimeZone()) -> Date? {
        gregorianCalendar(timeZone: timeZone).date(byAdding: .day, value: -1, to: date)
    }

    public static func isNextCalendarDay(after previous: Date, _ next: Date, timeZone: TimeZone = currentTimeZone()) -> Bool {
        guard let expected = gregorianCalendar(timeZone: timeZone).date(byAdding: .day, value: 1, to: previous) else {
            return false
        }
        return gregorianCalendar(timeZone: timeZone).isDate(expected, inSameDayAs: next)
    }
}