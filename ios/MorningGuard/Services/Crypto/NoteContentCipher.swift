import CryptoKit
import Foundation

enum NoteContentCipher {
    static let cipherPrefix = "mgenc1:"
    static let encryptedPlaceholder = "🔒 Encrypted note — unlock in Settings"

    static func isCiphertext(_ stored: String) -> Bool {
        stored.hasPrefix(cipherPrefix)
    }

    static func isLegacyPlaintext(_ stored: String) -> Bool {
        !stored.isEmpty && !isCiphertext(stored)
    }

    static func encrypt(plain: String, dek: SymmetricKey) throws -> String {
        let trimmed = plain.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        var iv = Data(count: 12)
        _ = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }
        let nonce = try AES.GCM.Nonce(data: iv)
        let sealed = try AES.GCM.seal(Data(trimmed.utf8), using: dek, nonce: nonce)
        let payload = iv + sealed.ciphertext + sealed.tag
        return cipherPrefix + payload.base64EncodedString()
    }

    static func decrypt(stored: String, dek: SymmetricKey) throws -> String {
        if stored.isEmpty { return "" }
        guard isCiphertext(stored) else { return stored }
        let b64 = String(stored.dropFirst(cipherPrefix.count))
        guard let payload = Data(base64Encoded: b64), payload.count > 28 else { return encryptedPlaceholder }
        let iv = payload.prefix(12)
        let tag = payload.suffix(16)
        let body = payload.dropFirst(12).dropLast(16)
        let box = try AES.GCM.SealedBox(nonce: try AES.GCM.Nonce(data: iv), ciphertext: body, tag: tag)
        let data = try AES.GCM.open(box, using: dek)
        return String(data: data, encoding: .utf8) ?? encryptedPlaceholder
    }
}
