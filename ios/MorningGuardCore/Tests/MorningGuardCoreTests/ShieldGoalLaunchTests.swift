import Foundation
import XCTest
@testable import MorningGuardCore

final class ShieldGoalLaunchTests: XCTestCase {
    func testIsGoalDeepLink() {
        XCTAssertTrue(ShieldGoalLaunch.isGoalDeepLink(URL(string: "morningguard://goal")!))
        XCTAssertTrue(ShieldGoalLaunch.isGoalDeepLink(URL(string: "morningguard:///goal")!))
        XCTAssertFalse(ShieldGoalLaunch.isGoalDeepLink(URL(string: "morningguard://auth")!))
        XCTAssertFalse(ShieldGoalLaunch.isGoalDeepLink(URL(string: "https://example.com/goal")!))
    }

    func testDeepLinkFromNotificationUserInfo() {
        let info: [AnyHashable: Any] = [
            ShieldGoalLaunch.notificationUserInfoDeepLinkKey: ShieldGoalLaunch.deepLinkURLString,
        ]
        XCTAssertEqual(
            ShieldGoalLaunch.deepLinkFromNotificationUserInfo(info)?.absoluteString,
            "morningguard://goal"
        )
        XCTAssertNil(ShieldGoalLaunch.deepLinkFromNotificationUserInfo([:]))
    }
}
