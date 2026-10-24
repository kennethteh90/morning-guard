import Foundation
import MorningGuardCore

/// Client-side cooldown so we do not hammer Supabase Auth (especially on the default SMTP tier).
enum MagicLinkRateLimitStore {
    static let defaultCooldownMs: Int64 = MagicLinkCooldown.defaultCooldownMs

    private static let defaults = UserDefaults.standard
    private static let prefix = "magic_link_rate_limit.last_sent."

    static func secondsUntilResend(email: String, cooldownMs: Int64 = defaultCooldownMs) -> Int {
        let key = prefix + email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let lastAt = defaults.object(forKey: key) as? Int64 ?? 0
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return MagicLinkCooldown.secondsUntilResend(
            lastSentEpochMs: lastAt,
            nowEpochMs: now,
            cooldownMs: cooldownMs
        )
    }

    static func canSend(email: String, cooldownMs: Int64 = defaultCooldownMs) -> Bool {
        secondsUntilResend(email: email, cooldownMs: cooldownMs) == 0
    }

    static func recordSent(email: String) {
        let key = prefix + email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        defaults.set(now, forKey: key)
    }
}

struct MagicLinkRateLimitedError: LocalizedError {
    let retryAfterSeconds: Int

    var errorDescription: String? {
        "Please wait \(retryAfterSeconds) seconds before requesting another link."
    }
}
