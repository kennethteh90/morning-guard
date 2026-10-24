import XCTest
@testable import MorningGuardCore

final class MagicLinkCooldownTests: XCTestCase {
    func testNoPriorSendAllowsImmediateResend() {
        XCTAssertEqual(MagicLinkCooldown.secondsUntilResend(lastSentEpochMs: 0, nowEpochMs: 1_000), 0)
    }

    func testFullCooldownWithinWindow() {
        let sent: Int64 = 1_000_000
        let now = sent + 10_000
        XCTAssertEqual(
            MagicLinkCooldown.secondsUntilResend(lastSentEpochMs: sent, nowEpochMs: now),
            50
        )
    }

    func testCooldownExpired() {
        let sent: Int64 = 1_000_000
        let now = sent + MagicLinkCooldown.defaultCooldownMs + 1
        XCTAssertEqual(
            MagicLinkCooldown.secondsUntilResend(lastSentEpochMs: sent, nowEpochMs: now),
            0
        )
    }
}
