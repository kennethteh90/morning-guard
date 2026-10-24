import XCTest
@testable import MorningGuardCore

final class RecoveryPasswordRulesTests: XCTestCase {
    func testRejectsShortPassword() {
        XCTAssertEqual(RecoveryPasswordRules.validate(password: "abc1"), "Use at least 8 characters.")
    }

    func testRejectsMissingLetterOrDigit() {
        XCTAssertEqual(RecoveryPasswordRules.validate(password: "12345678"), "Include at least one letter and one number.")
        XCTAssertEqual(RecoveryPasswordRules.validate(password: "abcdefgh"), "Include at least one letter and one number.")
    }

    func testAcceptsValidPassword() {
        XCTAssertNil(RecoveryPasswordRules.validate(password: "secure12"))
    }
}
