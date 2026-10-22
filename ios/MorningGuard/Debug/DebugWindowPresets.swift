import Foundation
import MorningGuardCore

/// Mirrors Android `DebugWindowPresets` for gate / shield testing.
enum DebugWindowPresets {
    static let defaultProduction = MorningWindow(startHour: 1, startMinute: 0, endHour: 10, endMinute: 0)

    /// Window that includes the current clock time (± buffer).
    static func includingNow(
        now: Date = Date(),
        minutesBefore: Int = 30,
        hoursAfter: Int = 3,
        calendar: Calendar = .current
    ) -> MorningWindow {
        let start = calendar.date(byAdding: .minute, value: -minutesBefore, to: now) ?? now
        let end = calendar.date(byAdding: .hour, value: hoursAfter, to: now) ?? now
        let startParts = calendar.dateComponents([.hour, .minute], from: start)
        let endParts = calendar.dateComponents([.hour, .minute], from: end)
        return MorningWindow(
            startHour: startParts.hour ?? 0,
            startMinute: startParts.minute ?? 0,
            endHour: endParts.hour ?? 23,
            endMinute: endParts.minute ?? 59
        )
    }

    /// Gate stays active almost all day until goal complete (debug only).
    static func fullDayTest() -> MorningWindow {
        MorningWindow(startHour: 0, startMinute: 0, endHour: 23, endMinute: 59)
    }
}

enum DebugWindowPreset: String, CaseIterable, Identifiable {
    case includingNow
    case fullDay
    case defaultProduction

    var id: String { rawValue }

    var label: String {
        switch self {
        case .includingNow: return "Active now (30 min ago → 3 hr ahead)"
        case .fullDay: return "Full day (0:00 – 23:59)"
        case .defaultProduction: return "Reset to default (1:00 – 10:00)"
        }
    }

    func window() -> MorningWindow {
        switch self {
        case .includingNow: return DebugWindowPresets.includingNow()
        case .fullDay: return DebugWindowPresets.fullDayTest()
        case .defaultProduction: return DebugWindowPresets.defaultProduction
        }
    }
}
