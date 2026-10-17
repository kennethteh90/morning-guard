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

    func testSharedStateRequiresActiveFlagAndSelection() {
        XCTAssertTrue(
            ShieldSessionPolicy.shouldApplyShieldsFromSharedState(shieldsActive: true, hasSelection: true)
        )
        XCTAssertFalse(
            ShieldSessionPolicy.shouldApplyShieldsFromSharedState(shieldsActive: false, hasSelection: true)
        )
    }

    func testIntervalEndAlwaysClears() {
        XCTAssertTrue(ShieldSessionPolicy.shouldClearShieldsOnIntervalEnd(shieldsActive: true))
        XCTAssertTrue(ShieldSessionPolicy.shouldClearShieldsOnIntervalEnd(shieldsActive: false))
    }
}
