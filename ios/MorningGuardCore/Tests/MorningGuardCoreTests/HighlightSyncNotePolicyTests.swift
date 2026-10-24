import XCTest
@testable import MorningGuardCore

final class HighlightSyncNotePolicyTests: XCTestCase {
    func testLockedDekPreservesRemoteCiphertext() {
        let remote = NoteCryptoInteropVectors.noteCiphertext
        let decision = HighlightSyncNotePolicy.resolve(
            localPlainNote: "local note",
            remoteNote: remote,
            dekUnlocked: false
        )
        XCTAssertEqual(decision, .preserveRemote(remoteNote: remote))
    }

    func testLockedDekOmitsPushWhenNoRemoteCiphertext() {
        let decision = HighlightSyncNotePolicy.resolve(
            localPlainNote: "local note",
            remoteNote: "",
            dekUnlocked: false
        )
        XCTAssertEqual(decision, .omitNote)
    }

    func testUnlockedDekEncryptsLocal() {
        let decision = HighlightSyncNotePolicy.resolve(
            localPlainNote: "hello",
            remoteNote: nil,
            dekUnlocked: true
        )
        XCTAssertEqual(decision, .encryptLocal(plainNote: "hello"))
    }
}
