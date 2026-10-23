import Foundation

public enum PeaceSessionPolicy {
    public static let sessionDurationMs: Int64 = 60 * 60 * 1000

    public static func activeUntil(
        storedUntilEpochMs: Int64?,
        nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> Int64? {
        guard let until = storedUntilEpochMs, nowMs < until else { return nil }
        return until
    }

    public static func isMorningGoalActive(evaluation: GateEvaluation) -> Bool {
        evaluation.state == .locked && evaluation.reason == "morning_window"
    }

    public static func canOfferPeaceBreak(evaluation: GateEvaluation, peaceSessionActive: Bool) -> Bool {
        !peaceSessionActive && !isMorningGoalActive(evaluation: evaluation)
    }
}
