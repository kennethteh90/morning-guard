import CryptoKit
import Foundation

enum LocalBackupCipher {
    private static let saltBytes = 16
    private static let ivBytes = 12
    private static let tagBytes = 16
    private static let iterations = 120_000

    static func encrypt(plaintext: Data, password: String) throws -> EncryptedLocalBackupFile {
        var salt = Data(count: saltBytes)
        var iv = Data(count: ivBytes)
        _ = salt.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, saltBytes, $0.baseAddress!) }
        _ = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, ivBytes, $0.baseAddress!) }

        let keyData = PBKDF2.deriveKey(password: Data(password.utf8), salt: salt, iterations: iterations)
        let key = SymmetricKey(data: keyData)
        let nonce = try AES.GCM.Nonce(data: iv)
        let sealed = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        let ciphertext = sealed.ciphertext + sealed.tag

        return EncryptedLocalBackupFile(
            saltBase64: salt.base64EncodedString(),
            ivBase64: iv.base64EncodedString(),
            ciphertextBase64: ciphertext.base64EncodedString()
        )
    }

    static func decrypt(file: EncryptedLocalBackupFile, password: String) throws -> Data {
        let salt = Data(base64Encoded: file.saltBase64) ?? Data()
        let iv = Data(base64Encoded: file.ivBase64) ?? Data()
        let ciphertext = Data(base64Encoded: file.ciphertextBase64) ?? Data()
        guard ciphertext.count > tagBytes else { throw LocalBackupError.invalidPassword }

        let keyData = PBKDF2.deriveKey(password: Data(password.utf8), salt: salt, iterations: iterations)
        let key = SymmetricKey(data: keyData)
        let nonce = try AES.GCM.Nonce(data: iv)
        let tag = ciphertext.suffix(tagBytes)
        let body = ciphertext.dropLast(tagBytes)
        let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: body, tag: tag)
        do {
            return try AES.GCM.open(box, using: key)
        } catch {
            throw LocalBackupError.invalidPassword
        }
    }
}

enum LocalBackupError: LocalizedError {
    case encryptionFailed
    case invalidPassword
    case unsupportedSchema
    case emptyFile

    var errorDescription: String? {
        switch self {
        case .encryptionFailed: "Backup encryption failed."
        case .invalidPassword: "Wrong backup password or corrupted backup file."
        case .unsupportedSchema: "This backup format is no longer supported. Create a new backup with the current app."
        case .emptyFile: "Backup file is empty."
        }
    }
}
