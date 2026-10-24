import XCTest
@testable import MorningGuardCore

final class NoteCryptoInteropVectorsTests: XCTestCase {
    func testPasswordWrapUnwrapsToSharedDek() throws {
        let wrap = NotePasswordWrap(
            wrappedDekBase64: NoteCryptoInteropVectors.passwordWrappedDekBase64,
            saltBase64: NoteCryptoInteropVectors.passwordWrapSaltBase64,
            ivBase64: NoteCryptoInteropVectors.passwordWrapIvBase64
        )
        let dek = try NoteCryptoPasswordWrap.unwrapDek(
            wrap: wrap,
            password: NoteCryptoInteropVectors.recoveryPassword
        )
        XCTAssertEqual([UInt8](dek), NoteCryptoInteropVectors.dekBytes)
    }

    func testNoteCiphertextDecryptsWithSharedDek() throws {
        let dek = Data(NoteCryptoInteropVectors.dekBytes)
        let plain = try NoteContentCipher.decrypt(stored: NoteCryptoInteropVectors.noteCiphertext, dekBytes: dek)
        XCTAssertEqual(plain, NoteCryptoInteropVectors.notePlaintext)
    }
}
