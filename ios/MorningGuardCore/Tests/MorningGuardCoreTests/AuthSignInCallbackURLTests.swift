import Foundation
import XCTest
@testable import MorningGuardCore

final class AuthSignInCallbackURLTests: XCTestCase {
    func testAcceptsAuthHostWithCode() {
        XCTAssertTrue(AuthSignInCallbackURL.isSignInCallback(URL(string: "morningguard://auth?code=abc")!))
    }

    func testRejectsAuthEvilHost() {
        XCTAssertFalse(AuthSignInCallbackURL.isSignInCallback(URL(string: "morningguard://auth.evil?code=abc")!))
    }

    func testRejectsAuthenticationHost() {
        XCTAssertFalse(AuthSignInCallbackURL.isSignInCallback(URL(string: "morningguard://authentication?code=abc")!))
    }

    func testRejectsBareAuthWithoutCode() {
        XCTAssertFalse(AuthSignInCallbackURL.isSignInCallback(URL(string: "morningguard://auth")!))
    }

    func testRejectsFragmentAccessToken() {
        XCTAssertFalse(AuthSignInCallbackURL.isSignInCallback(URL(string: "morningguard://auth#access_token=secret")!))
    }

    func testRejectsQueryAccessTokenOnly() {
        XCTAssertFalse(AuthSignInCallbackURL.isSignInCallback(URL(string: "morningguard://auth?access_token=secret")!))
    }

    func testRejectsHttpsGoalScheme() {
        XCTAssertFalse(AuthSignInCallbackURL.isSignInCallback(URL(string: "https://morning-guard.codingagents.cc/?code=abc")!))
    }
}
