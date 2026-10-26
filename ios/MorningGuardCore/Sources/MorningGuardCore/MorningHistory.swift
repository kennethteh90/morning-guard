import Foundation

public enum MorningDayOutcome: String, Codable, Sendable, Equatable {
    case goalCompleted = "GOAL_COMPLETED"
    case emergencyBypass = "EMERGENCY_BYPASS"
}

public struct MorningDayRecord: Codable, Sendable, Equatable, Identifiable {
    public var dateKey: String
    public var outcome: MorningDayOutcome
    public var recordedAtEpochMs: Int64
    public var timeOnGoalSeconds: Int
    public var stepsOnGoal: Int

    public var id: String { "\(dateKey)-\(outcome.rawValue)" }

    public init(
        dateKey: String,
        outcome: MorningDayOutcome,
        recordedAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        timeOnGoalSeconds: Int = 0,
        stepsOnGoal: Int = 0
    ) {
        self.dateKey = dateKey
        self.outcome = outcome
        self.recordedAtEpochMs = recordedAtEpochMs
        self.timeOnGoalSeconds = timeOnGoalSeconds
        self.stepsOnGoal = stepsOnGoal
    }
}

public enum MorningGoalTimeFormat {
    public static func format(seconds: Int) -> String {
        if seconds <= 0 { return "0m" }
        if seconds < 60 { return "\(seconds)s" }
        let totalMinutes = seconds / 60
        if totalMinutes < 60 {
            let remSec = seconds % 60
            return remSec == 0 ? "\(totalMinutes)m" : "\(totalMinutes)m \(remSec)s"
        }
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        return minutes == 0 ? "\(hours)h" : "\(hours)h \(minutes)m"
    }
}

public enum MorningStreakCalculator {
    public static func currentCompletedStreak(history: [MorningDayRecord], todayKey: String) -> Int {
        let completedDays = Set(
            history.filter { $0.outcome == .goalCompleted }.map(\.dateKey)
        )
        guard let today = parseDateKey(todayKey) else { return 0 }
        var streak = 0
        var day = today
        let formatter = dateKeyFormatter
        while completedDays.contains(formatter.string(from: day)) {
            streak += 1
            guard let previous = Calendar.current.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return streak
    }

    public static func bestCompletedStreak(history: [MorningDayRecord]) -> Int {
        let completed = Set(history.filter { $0.outcome == .goalCompleted }.map(\.dateKey))
        if completed.isEmpty { return 0 }
        let sorted = completed.compactMap(parseDateKey).sorted()
        var best = 0
        var run = 0
        var prev: Date?
        for day in sorted {
            if let prev, Calendar.current.date(byAdding: .day, value: 1, to: prev) == day {
                run += 1
            } else {
                run = 1
            }
            best = max(best, run)
            prev = day
        }
        return best
    }

    public static func countOutcome(history: [MorningDayRecord], outcome: MorningDayOutcome) -> Int {
        history.count { $0.outcome == outcome }
    }

    public static func totalGoalTimeSeconds(
        dailySeconds: [String: Int],
        history: [MorningDayRecord],
        activeProgressSeconds: Int?,
        activeDateKey: String?
    ) -> Int {
        var merged = dailySeconds
        for record in history {
            merged[record.dateKey] = max(merged[record.dateKey] ?? 0, record.timeOnGoalSeconds)
        }
        if let key = activeDateKey, let seconds = activeProgressSeconds {
            merged[key] = max(merged[key] ?? 0, seconds)
        }
        return merged.values.reduce(0, +)
    }

    /// Sum of per-day seconds with the Bible reader open (morning goal or Read Bible).
    public static func totalBibleReadingSeconds(dailySeconds: [String: Int]) -> Int {
        dailySeconds.values.reduce(0, +)
    }

    public static func totalGoalSteps(
        dailySteps: [String: Int],
        history: [MorningDayRecord],
        activeProgressSteps: Int?,
        activeDateKey: String?
    ) -> Int {
        var merged = dailySteps
        for record in history {
            merged[record.dateKey] = max(merged[record.dateKey] ?? 0, record.stepsOnGoal)
        }
        if let key = activeDateKey, let steps = activeProgressSteps {
            merged[key] = max(merged[key] ?? 0, steps)
        }
        return merged.values.reduce(0, +)
    }

    private static let dateKeyFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private static func parseDateKey(_ key: String) -> Date? {
        dateKeyFormatter.date(from: key)
    }
}

public enum MorningHistoryMerge {
    public static func mergeMorningRecord(
        history: [MorningDayRecord],
        dateKey: String,
        outcome: MorningDayOutcome,
        recordedAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        timeOnGoalSeconds: Int = 0,
        stepsOnGoal: Int = 0
    ) -> [MorningDayRecord] {
        let existing = history.first { $0.dateKey == dateKey }
        if existing?.outcome == .goalCompleted {
            let betterTime = timeOnGoalSeconds > existing!.timeOnGoalSeconds
            let betterSteps = stepsOnGoal > existing!.stepsOnGoal
            if !betterTime && !betterSteps { return history }
            return history.map {
                guard $0.dateKey == dateKey else { return $0 }
                return MorningDayRecord(
                    dateKey: $0.dateKey,
                    outcome: $0.outcome,
                    recordedAtEpochMs: $0.recordedAtEpochMs,
                    timeOnGoalSeconds: max($0.timeOnGoalSeconds, timeOnGoalSeconds),
                    stepsOnGoal: max($0.stepsOnGoal, stepsOnGoal)
                )
            }
        }
        if let existing, existing.outcome == outcome {
            let betterTime = timeOnGoalSeconds > existing.timeOnGoalSeconds
            let betterSteps = stepsOnGoal > existing.stepsOnGoal
            if !betterTime && !betterSteps { return history }
            return history.map {
                guard $0.dateKey == dateKey else { return $0 }
                return MorningDayRecord(
                    dateKey: $0.dateKey,
                    outcome: $0.outcome,
                    recordedAtEpochMs: $0.recordedAtEpochMs,
                    timeOnGoalSeconds: max($0.timeOnGoalSeconds, timeOnGoalSeconds),
                    stepsOnGoal: max($0.stepsOnGoal, stepsOnGoal)
                )
            }
        }
        return history.filter { $0.dateKey != dateKey } + [
            MorningDayRecord(
                dateKey: dateKey,
                outcome: outcome,
                recordedAtEpochMs: recordedAtEpochMs,
                timeOnGoalSeconds: timeOnGoalSeconds,
                stepsOnGoal: stepsOnGoal
            ),
        ]
    }

    public static func mergeGoalTime(map: [String: Int], dateKey: String, seconds: Int) -> [String: Int] {
        guard seconds > 0 else { return map }
        let merged = max(map[dateKey] ?? 0, seconds)
        if merged == map[dateKey] { return map }
        var copy = map
        copy[dateKey] = merged
        return copy
    }

    public static func mergeGoalSteps(map: [String: Int], dateKey: String, steps: Int) -> [String: Int] {
        guard steps > 0 else { return map }
        let merged = max(map[dateKey] ?? 0, steps)
        if merged == map[dateKey] { return map }
        var copy = map
        copy[dateKey] = merged
        return copy
    }
}
