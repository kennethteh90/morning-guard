import Foundation

public enum GateState: String, Codable, Sendable, Equatable {
    case inactive = "INACTIVE"
    case locked = "LOCKED"
    case unlocked = "UNLOCKED"
}

public struct GateEvaluation: Codable, Sendable, Equatable {
    public var state: GateState
    public var dateKey: String
    public var reason: String

    public init(state: GateState, dateKey: String, reason: String) {
        self.state = state
        self.dateKey = dateKey
        self.reason = reason
    }
}

public struct MorningGateEvaluator: Sendable {
    private let timeZone: TimeZone
    private let now: () -> Date

    public init(timeZone: TimeZone = .current, now: @escaping () -> Date = Date.init) {
        self.timeZone = timeZone
        self.now = now
    }

    public func evaluate(
        window: MorningWindow,
        progress: MorningGoalProgress?,
        emergencyBypassActiveUntilEpochMs: Int64?,
        peaceSessionUntilEpochMs: Int64? = nil
    ) -> GateEvaluation {
        let nowDate = now()
        let nowMs = Int64(nowDate.timeIntervalSince1970 * 1000)
        let calendar = Self.calendar(for: timeZone)
        let dateKey = Self.dateKey(for: nowDate, calendar: calendar)

        if let bypassUntil = emergencyBypassActiveUntilEpochMs, nowMs < bypassUntil {
            return GateEvaluation(state: .unlocked, dateKey: dateKey, reason: "emergency_bypass")
        }

        if let peaceUntil = peaceSessionUntilEpochMs, nowMs < peaceUntil {
            return GateEvaluation(state: .locked, dateKey: dateKey, reason: "peace_session")
        }

        if let progress, progress.dateKey == dateKey, progress.isComplete {
            return GateEvaluation(state: .unlocked, dateKey: dateKey, reason: "goal_complete")
        }

        let nowMinutes = Self.minutesSinceMidnight(for: nowDate, calendar: calendar)
        let start = Self.minutesSinceMidnight(hour: window.startHour, minute: window.startMinute)
        let end = Self.minutesSinceMidnight(hour: window.endHour, minute: window.endMinute)
        let inWindow = Self.isInMorningWindow(nowMinutes: nowMinutes, start: start, end: end)

        if inWindow {
            return GateEvaluation(state: .locked, dateKey: dateKey, reason: "morning_window")
        }
        return GateEvaluation(state: .inactive, dateKey: dateKey, reason: "outside_window")
    }

    static func calendar(for timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar
    }

    public static func dateKey(for date: Date, calendar: Calendar) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        let year = components.year ?? 0
        let month = components.month ?? 0
        let day = components.day ?? 0
        return String(format: "%04d-%02d-%02d", year, month, day)
    }

    static func minutesSinceMidnight(hour: Int, minute: Int) -> Int {
        hour * 60 + minute
    }

    static func minutesSinceMidnight(for date: Date, calendar: Calendar) -> Int {
        let components = calendar.dateComponents([.hour, .minute], from: date)
        return minutesSinceMidnight(hour: components.hour ?? 0, minute: components.minute ?? 0)
    }

    /// Inclusive start and end, including overnight windows where start > end.
    static func isInMorningWindow(nowMinutes: Int, start: Int, end: Int) -> Bool {
        if start <= end {
            return nowMinutes >= start && nowMinutes <= end
        }
        return nowMinutes >= start || nowMinutes <= end
    }
}
