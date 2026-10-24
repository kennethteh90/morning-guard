import XCTest
@testable import MorningGuardCore

final class NoteCryptoInteropVectorsTests: XCTestCase {
    func testPasswordWrapUnwrapsToSharedDek() throws {
        let dek = Data(NoteCryptoInteropVectors.dekBytes)
        let salt = Data((0..<16).map { UInt8($0) })
        let iv = Data((0..<12).map { UInt8($0) })
        let wrap = try NoteCryptoPasswordWrap.wrapDek(
            dekBytes: dek,
            password: NoteCryptoInteropVectors.recoveryPassword,
            salt: salt,
            iv: iv
        )
        XCTAssertEqual(wrap.saltBase64, NoteCryptoInteropVectors.passwordWrapSaltBase64)
        XCTAssertEqual(wrap.ivBase64, NoteCryptoInteropVectors.passwordWrapIvBase64)
        XCTAssertEqual(wrap.wrappedDekBase64, NoteCryptoInteropVectors.passwordWrappedDekBase64)
        let unwrapped = try NoteCryptoPasswordWrap.unwrapDek(
            wrap: wrap,
            password: NoteCryptoInteropVectors.recoveryPassword
        )
        XCTAssertEqual([UInt8](unwrapped), NoteCryptoInteropVectors.dekBytes)
    }

    func testNoteCiphertextDecryptsWithSharedDek() throws {
        let dek = Data(NoteCryptoInteropVectors.dekBytes)
        let plain = try NoteContentCipher.decrypt(stored: NoteCryptoInteropVectors.noteCiphertext, dekBytes: dek)
        XCTAssertEqual(plain, NoteCryptoInteropVectors.notePlaintext)
    }
}
