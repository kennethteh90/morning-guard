import XCTest
@testable import MorningGuardCore

final class ShieldSessionPolicyTests: XCTestCase {
    func testApplyWhenLockedAuthorizedAndHasSelection() {
        XCTAssertTrue(
            ShieldSessionPolicy.shouldApplyShields(gateState: .locked, isAuthorized: true, hasSelection: true)
        )
    }

    func testNoApplyWhenUnlocked() {
        XCTAssertFalse(
            ShieldSessionPolicy.shouldApplyShields(gateState: .unlocked, isAuthorized: true, hasSelection: true)
        )
    }

    func testNoApplyWhenInactive() {
        XCTAssertFalse(
            ShieldSessionPolicy.shouldApplyShields(gateState: .inactive, isAuthorized: true, hasSelection: true)
        )
    }

    func testNoApplyWhenNotAuthorized() {
        XCTAssertFalse(
            ShieldSessionPolicy.shouldApplyShields(gateState: .locked, isAuthorized: false, hasSelection: true)
        )
    }

    func testNoApplyWithoutSelection() {
        XCTAssertFalse(
            ShieldSessionPolicy.shouldApplyShields(gateState: .locked, isAuthorized: true, hasSelection: false)
        )
    }

    func testClearWhenNotLocked() {
        XCTAssertTrue(ShieldSessionPolicy.shouldClearShields(gateState: .unlocked))
        XCTAssertTrue(ShieldSessionPolicy.shouldClearShields(gateState: .inactive))
        XCTAssertFalse(ShieldSessionPolicy.shouldClearShields(gateState: .locked))
    }

    func testPeaceSessionStaysLockedAfterIntervalWouldEnd() {
        XCTAssertFalse(ShieldSessionPolicy.shouldClearShields(gateState: .locked))
        XCTAssertTrue(
            ShieldSessionPolicy.shouldApplyShields(gateState: .locked, isAuthorized: true, hasSelection: true)
        )
    }

    func testReapplyOnlyWhenLockedWithSelection() {
        XCTAssertTrue(ShieldSessionPolicy.shouldReapplyAfterEvent(gateState: .locked, hasSelection: true))
        XCTAssertFalse(ShieldSessionPolicy.shouldReapplyAfterEvent(gateState: .unlocked, hasSelection: true))
        XCTAssertFalse(ShieldSessionPolicy.shouldReapplyAfterEvent(gateState: .locked, hasSelection: false))
    }
}
