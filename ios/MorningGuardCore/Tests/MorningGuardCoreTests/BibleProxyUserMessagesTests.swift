import XCTest
@testable import MorningGuardCore

final class BibleProxyUserMessagesTests: XCTestCase {
    func test429IncludesRetryGuidance() {
        let message = BibleProxyUserMessages.friendlyMessage(httpStatus: 429, serverError: "Rate limit exceeded")
        XCTAssertTrue(message.localizedCaseInsensitiveContains("minute"))
    }

    func test504MentionsOpenFallback() {
        let message = BibleProxyUserMessages.friendlyMessage(httpStatus: 504, serverError: nil)
        XCTAssertTrue(message.localizedCaseInsensitiveContains("WEB"))
        XCTAssertTrue(message.localizedCaseInsensitiveContains("KJV"))
    }

    func test403LicensedDoesNotMentionWorker() {
        let message = BibleProxyUserMessages.friendlyMessage(httpStatus: 403, serverError: "Licensed content blocked")
        XCTAssertTrue(message.localizedCaseInsensitiveContains("WEB"))
        XCTAssertFalse(message.localizedCaseInsensitiveContains("worker"))
    }
}
