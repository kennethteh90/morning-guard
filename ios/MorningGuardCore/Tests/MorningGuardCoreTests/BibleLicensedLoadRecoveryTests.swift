import XCTest
@testable import MorningGuardCore

final class BibleLicensedLoadRecoveryTests: XCTestCase {
    func testOffersFallbackForLicensedTranslationOn401() {
        XCTAssertTrue(
            BibleLicensedLoadRecovery.shouldOfferOpenBibleFallback(
                translationId: "niv",
                httpStatus: 401,
                errorDescription: "any"
            )
        )
    }

    func testOffersFallbackForLicensedOn504Message() {
        XCTAssertTrue(
            BibleLicensedLoadRecovery.shouldOfferOpenBibleFallback(
                translationId: "nkjv",
                errorDescription: "The Bible service is temporarily unavailable (HTTP 504)."
            )
        )
    }

    func testNoFallbackForWebTranslation() {
        XCTAssertFalse(
            BibleLicensedLoadRecovery.shouldOfferOpenBibleFallback(
                translationId: "web",
                httpStatus: 504,
                errorDescription: "timeout"
            )
        )
    }

    func testOpenFallbackIdsIncludeWebAndKjv() {
        XCTAssertEqual(BibleLicensedLoadRecovery.openFallbackTranslationIds, ["web", "kjv"])
    }
}
