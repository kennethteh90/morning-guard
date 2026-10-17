import XCTest
@testable import MorningGuardCore

final class EmergencyBypassPolicyTests: XCTestCase {
    func testFifthFailedAttemptSetsLockout() {
        let policy = EmergencyBypassPolicy(now: { Date(timeIntervalSince1970: 1) })
        var attempts = 0
        var lockedUntil: Int64?

        for _ in 0 ..< 4 {
            let result = policy.recordFailure(failedAttempts: attempts, lockedUntilEpochMs: lockedUntil, nowMs: 1_000)
            attempts = result.failedAttempts
            lockedUntil = result.lockedUntilEpochMs
            XCTAssertNil(lockedUntil)
        }

        let fifth = policy.recordFailure(failedAttempts: attempts, lockedUntilEpochMs: lockedUntil, nowMs: 1_000)
        XCTAssertEqual(fifth.failedAttempts, 5)
        XCTAssertEqual(fifth.lockedUntilEpochMs, 1_000 + EmergencyBypassPolicy.LOCKOUT_DURATION_MS)
        XCTAssertTrue(policy.isLocked(lockedUntilEpochMs: fifth.lockedUntilEpochMs, nowMs: 1_000))
    }

    func testLockRemainsActiveUntilDurationElapses() {
        let lockStart: Int64 = 10_000
        let lockedUntil = lockStart + EmergencyBypassPolicy.LOCKOUT_DURATION_MS
        let policy = EmergencyBypassPolicy(now: { Date(timeIntervalSince1970: Double(lockStart + 60_000) / 1000) })

        XCTAssertTrue(policy.isLocked(lockedUntilEpochMs: lockedUntil, nowMs: lockStart + 60_000))
        XCTAssertFalse(policy.isLocked(lockedUntilEpochMs: lockedUntil, nowMs: lockedUntil))
        XCTAssertFalse(policy.isLocked(lockedUntilEpochMs: lockedUntil, nowMs: lockedUntil + 1))
    }
}
