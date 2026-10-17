import CommonCrypto
import Foundation
import MorningGuardCore
import Security

enum PassphraseStoreError: LocalizedError {
    case invalidLength
    case notConfigured
    case invalidPassphrase
    case locked
    case keychainFailure(OSStatus)

    var errorDescription: String? {
        switch self {
        case .invalidLength:
            return "Passphrase must be at least 8 characters."
        case .notConfigured:
            return "Emergency passphrase is not configured."
        case .invalidPassphrase:
            return "Invalid passphrase."
        case .locked:
            return "Too many failed attempts. Try again in 15 minutes."
        case .keychainFailure(let status):
            return "Keychain error (\(status))."
        }
    }
}

/// PBKDF2-SHA256 storage compatible with Android `PassphraseHasher` (`pbkdf2-sha256$iterations$salt$hash`).
final class PassphraseStore: Sendable {
    private let service = "com.morningguard.emergency"
    private let account = "passphrase"

    func storedHash() -> String? {
        readKeychainString()
    }

    func setPassphrase(_ passphrase: String, policy: EmergencyBypassPolicy) throws {
        guard policy.validateLength(passphrase) else {
            throw PassphraseStoreError.invalidLength
        }
        let stored = Self.hash(passphrase: passphrase)
        try writeKeychainString(stored)
    }

    func verify(_ passphrase: String, stored: String) -> Bool {
        Self.verify(passphrase: passphrase, stored: stored)
    }

    func clear() throws {
        try deleteKeychainItem()
    }

    static func hash(passphrase: String) -> String {
        let salt = randomSaltHex(byteCount: 16)
        let derived = deriveKey(passphrase: passphrase, saltHex: salt, iterations: iterations)
        return "\(storagePrefix)$\(iterations)$\(salt)$\(derived)"
    }

    static func verify(passphrase: String, stored: String) -> Bool {
        guard isModernFormat(stored) else { return false }
        let parts = stored.split(separator: "$", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 4,
              let iterations = Int(parts[1]) else { return false }
        let salt = parts[2]
        let expected = parts[3]
        let actual = deriveKey(passphrase: passphrase, saltHex: salt, iterations: iterations)
        return constantTimeEquals(actual, expected)
    }

    static func isModernFormat(_ stored: String?) -> Bool {
        guard let stored else { return false }
        return stored.hasPrefix("\(storagePrefix)$")
    }

    private static let storagePrefix = "pbkdf2-sha256"
    private static let iterations = 120_000
    private static let keyLengthBits = 256

    private static func deriveKey(passphrase: String, saltHex: String, iterations: Int) -> String {
        let salt = hexToBytes(saltHex)
        let derived = pbkdf2SHA256(password: Data(passphrase.utf8), salt: salt, iterations: iterations)
        return derived.map { String(format: "%02x", $0) }.joined()
    }

    private static func pbkdf2SHA256(password: Data, salt: Data, iterations: Int) -> Data {
        var derived = [UInt8](repeating: 0, count: keyLengthBits / 8)
        let status = password.withUnsafeBytes { passwordBytes in
            salt.withUnsafeBytes { saltBytes in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    passwordBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                    password.count,
                    saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    UInt32(iterations),
                    &derived,
                    derived.count
                )
            }
        }
        guard status == kCCSuccess else { return Data() }
        return Data(derived)
    }

    private static func randomSaltHex(byteCount: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        _ = SecRandomCopyBytes(kSecRandomDefault, byteCount, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    private static func hexToBytes(_ hex: String) -> Data {
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2, limitedBy: hex.endIndex) ?? hex.endIndex
            let chunk = hex[index..<next]
            if let byte = UInt8(chunk, radix: 16) {
                data.append(byte)
            }
            index = next
        }
        return data
    }

    private static func constantTimeEquals(_ a: String, _ b: String) -> Bool {
        if a.count != b.count { return false }
        var result = 0
        for (left, right) in zip(a.utf8, b.utf8) {
            result |= Int(left) ^ Int(right)
        }
        return result == 0
    }

    private func readKeychainString() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func writeKeychainString(_ value: String) throws {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        if status == errSecSuccess {
            let update = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
            guard update == errSecSuccess else { throw PassphraseStoreError.keychainFailure(update) }
        } else {
            var addQuery = query
            addQuery[kSecValueData as String] = data
            addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let add = SecItemAdd(addQuery as CFDictionary, nil)
            guard add == errSecSuccess else { throw PassphraseStoreError.keychainFailure(add) }
        }
    }

    private func deleteKeychainItem() throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw PassphraseStoreError.keychainFailure(status)
        }
    }
}
