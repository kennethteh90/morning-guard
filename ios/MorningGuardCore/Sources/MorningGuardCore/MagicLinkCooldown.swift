import Foundation

/// Pure cooldown math shared with the iOS client rate-limit store (Android uses the same 60s default).
public enum MagicLinkCooldown {
    public static let defaultCooldownMs: Int64 = 60_000

    public static func secondsUntilResend(
        lastSentEpochMs: Int64,
        nowEpochMs: Int64,
        cooldownMs: Int64 = defaultCooldownMs
    ) -> Int {
        if lastSentEpochMs == 0 { return 0 }
        let remaining = lastSentEpochMs + cooldownMs - nowEpochMs
        if remaining <= 0 { return 0 }
        return Int((remaining + 999) / 1000)
    }
}
