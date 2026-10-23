import Foundation

public struct EmergencyBypassPolicy: Sendable {
    public static let MAX_FAILED_ATTEMPTS = 5
    public static let LOCKOUT_DURATION_MS: Int64 = 15 * 60 * 1000

    private let minLength: Int
    private let bypassDurationMs: Int64
    public let maxFailedAttemptsLimit: Int
    public let lockoutDurationMsLimit: Int64
    private let now: () -> Date

    public init(
        minLength: Int = 8,
        bypassDurationMs: Int64 = 60 * 60 * 1000,
        maxFailedAttempts: Int = EmergencyBypassPolicy.MAX_FAILED_ATTEMPTS,
        lockoutDurationMs: Int64 = EmergencyBypassPolicy.LOCKOUT_DURATION_MS,
        now: @escaping () -> Date = Date.init
    ) {
        self.minLength = minLength
        self.bypassDurationMs = bypassDurationMs
        self.maxFailedAttemptsLimit = maxFailedAttempts
        self.lockoutDurationMsLimit = lockoutDurationMs
        self.now = now
    }

    public func validateLength(passphrase: String) -> Bool {
        passphrase.count >= minLength
    }

    public func bypassExpiresAt(nowMs: Int64? = nil) -> Int64 {
        let ms = nowMs ?? Self.epochMilliseconds(from: now())
        return ms + bypassDurationMs
    }

    public func isLocked(lockedUntilEpochMs: Int64?, nowMs: Int64? = nil) -> Bool {
        let ms = nowMs ?? Self.epochMilliseconds(from: now())
        guard let lockedUntilEpochMs else { return false }
        return ms < lockedUntilEpochMs
    }

    public func effectiveFailedAttempts(
        failedAttempts: Int,
        lockedUntilEpochMs: Int64?,
        nowMs: Int64? = nil
    ) -> Int {
        let ms = nowMs ?? Self.epochMilliseconds(from: now())
        if let lockedUntilEpochMs, ms >= lockedUntilEpochMs {
            return 0
        }
        return failedAttempts
    }

    public func recordFailure(
        failedAttempts: Int,
        lockedUntilEpochMs: Int64?,
        nowMs: Int64? = nil
    ) -> (failedAttempts: Int, lockedUntilEpochMs: Int64?) {
        let ms = nowMs ?? Self.epochMilliseconds(from: now())
        if isLocked(lockedUntilEpochMs: lockedUntilEpochMs, nowMs: ms) {
            return (failedAttempts, lockedUntilEpochMs)
        }
        let current = effectiveFailedAttempts(
            failedAttempts: failedAttempts,
            lockedUntilEpochMs: lockedUntilEpochMs,
            nowMs: ms
        )
        let nextAttempts = current + 1
        let newLockedUntil: Int64? = if nextAttempts >= maxFailedAttemptsLimit {
            ms + lockoutDurationMsLimit
        } else {
            nil
        }
        return (nextAttempts, newLockedUntil)
    }

    public func clearedLockoutAfterSuccess() -> (failedAttempts: Int, lockedUntilEpochMs: Int64?) {
        (0, nil)
    }

    public func constantTimeEquals(_ a: String, _ b: String) -> Bool {
        if a.count != b.count { return false }
        var result = 0
        for (left, right) in zip(a.utf8, b.utf8) {
            result |= Int(left) ^ Int(right)
        }
        return result == 0
    }

    private static func epochMilliseconds(from date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 * 1000)
    }
}
