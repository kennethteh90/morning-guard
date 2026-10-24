import CryptoKit
import Foundation

public struct NotePasswordWrap: Equatable, Sendable {
    public var wrappedDekBase64: String
    public var saltBase64: String
    public var ivBase64: String

    public init(wrappedDekBase64: String, saltBase64: String, ivBase64: String) {
        self.wrappedDekBase64 = wrappedDekBase64
        self.saltBase64 = saltBase64
        self.ivBase64 = ivBase64
    }
}

public enum NoteCryptoPasswordWrap {
    public static func wrapDek(dekBytes: Data, password: String) throws -> NotePasswordWrap {
        var salt = Data(count: 16)
        var iv = Data(count: 12)
        _ = salt.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        _ = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }
        let key = SymmetricKey(data: PBKDF2.deriveKey(password: Data(password.utf8), salt: salt))
        let sealed = try AES.GCM.seal(dekBytes, using: key, nonce: try AES.GCM.Nonce(data: iv))
        return NotePasswordWrap(
            wrappedDekBase64: (sealed.ciphertext + sealed.tag).base64EncodedString(),
            saltBase64: salt.base64EncodedString(),
            ivBase64: iv.base64EncodedString()
        )
    }

    public static func wrapDek(
        dekBytes: Data,
        password: String,
        salt: Data,
        iv: Data
    ) throws -> NotePasswordWrap {
        let key = SymmetricKey(data: PBKDF2.deriveKey(password: Data(password.utf8), salt: salt))
        let sealed = try AES.GCM.seal(dekBytes, using: key, nonce: try AES.GCM.Nonce(data: iv))
        return NotePasswordWrap(
            wrappedDekBase64: (sealed.ciphertext + sealed.tag).base64EncodedString(),
            saltBase64: salt.base64EncodedString(),
            ivBase64: iv.base64EncodedString()
        )
    }

    public static func unwrapDek(wrap: NotePasswordWrap, password: String) throws -> Data {
        let salt = Data(base64Encoded: wrap.saltBase64) ?? Data()
        let iv = Data(base64Encoded: wrap.ivBase64) ?? Data()
        let ciphertext = Data(base64Encoded: wrap.wrappedDekBase64) ?? Data()
        let key = SymmetricKey(data: PBKDF2.deriveKey(password: Data(password.utf8), salt: salt))
        let tag = ciphertext.suffix(16)
        let body = ciphertext.dropLast(16)
        let box = try AES.GCM.SealedBox(nonce: try AES.GCM.Nonce(data: iv), ciphertext: body, tag: tag)
        return try AES.GCM.open(box, using: key)
    }
}
