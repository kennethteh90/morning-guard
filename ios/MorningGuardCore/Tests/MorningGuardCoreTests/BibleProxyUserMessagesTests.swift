import XCTest
@testable import MorningGuardCore

final class BibleProxyUserMessagesTests: XCTestCase {
    func test429IncludesRetryGuidance() {
        let message = BibleProxyUserMessages.friendlyMessage(httpStatus: 429, serverError: "Rate limit exceeded")
        XCTAssertTrue(message.localizedCaseInsensitiveContains("minute"))
    }
}
