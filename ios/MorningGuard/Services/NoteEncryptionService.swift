import CryptoKit
import Foundation
import Security

struct NoteCryptoState: Codable, Equatable {
    var enabled: Bool
    var passwordWrappedDekBase64: String?
    var passwordWrapSaltBase64: String?
    var passwordWrapIvBase64: String?
    var keystoreWrappedDekBase64: String?
    var keystoreWrapIvBase64: String?

    init(
        enabled: Bool = false,
        passwordWrappedDekBase64: String? = nil,
        passwordWrapSaltBase64: String? = nil,
        passwordWrapIvBase64: String? = nil,
        keystoreWrappedDekBase64: String? = nil,
        keystoreWrapIvBase64: String? = nil
    ) {
        self.enabled = enabled
        self.passwordWrappedDekBase64 = passwordWrappedDekBase64
        self.passwordWrapSaltBase64 = passwordWrapSaltBase64
        self.passwordWrapIvBase64 = passwordWrapIvBase64
        self.keystoreWrappedDekBase64 = keystoreWrappedDekBase64
        self.keystoreWrapIvBase64 = keystoreWrapIvBase64
    }
}

@MainActor
@Observable
final class NoteEncryptionService {
    static let cipherPrefix = "mgenc1:"
    static let encryptedPlaceholder = "🔒 Encrypted note — unlock in Settings"

    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private static let stateKey = "noteCryptoState"
    private static let keychainLocalWrapKey = "com.morningguard.app.note-local-wrap"

    private var dek: SymmetricKey?

    var cryptoState: NoteCryptoState

    var isUnlocked: Bool { dek != nil }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        cryptoState = Self.loadState(from: defaults)
        _ = tryUnlockFromKeychain()
    }

    func setupWithPassword(_ password: String) throws {
        guard password.count >= 8 else {
            throw NSError(domain: "NotesCrypto", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Password must be at least 8 characters.",
            ])
        }
        let dekBytes = randomBytes(count: 32)
        let passwordWrap = wrapDekWithPassword(dekBytes: dekBytes, password: password)
        let keystoreWrap = wrapDekWithKeychain(dekBytes: dekBytes)
        cryptoState = NoteCryptoState(
            enabled: true,
            passwordWrappedDekBase64: passwordWrap.wrappedDekBase64,
            passwordWrapSaltBase64: passwordWrap.saltBase64,
            passwordWrapIvBase64: passwordWrap.ivBase64,
            keystoreWrappedDekBase64: keystoreWrap.wrappedDekBase64,
            keystoreWrapIvBase64: keystoreWrap.ivBase64
        )
        dek = SymmetricKey(data: dekBytes)
        persistState()
    }

    func unlockWithPassword(_ password: String) throws {
        guard let wrapped = cryptoState.passwordWrappedDekBase64,
              let saltB64 = cryptoState.passwordWrapSaltBase64,
              let ivB64 = cryptoState.passwordWrapIvBase64 else {
            throw NSError(domain: "NotesCrypto", code: 2, userInfo: [
                NSLocalizedDescriptionKey: "Notes encryption is not set up on this device.",
            ])
        }
        let dekBytes = try unwrapDekWithPassword(
            wrap: PasswordWrap(wrappedDekBase64: wrapped, saltBase64: saltB64, ivBase64: ivB64),
            password: password
        )
        let keystoreWrap = wrapDekWithKeychain(dekBytes: dekBytes)
        cryptoState.enabled = true
        cryptoState.keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64
        cryptoState.keystoreWrapIvBase64 = keystoreWrap.ivBase64
        dek = SymmetricKey(data: dekBytes)
        persistState()
    }

    func changePassword(oldPassword: String, newPassword: String) throws {
        guard newPassword.count >= 8 else {
            throw NSError(domain: "NotesCrypto", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "New password must be at least 8 characters.",
            ])
        }
        try unlockWithPassword(oldPassword)
        guard let dekBytes = dekData() else { throw NSError(domain: "NotesCrypto", code: 3) }
        let passwordWrap = wrapDekWithPassword(dekBytes: dekBytes, password: newPassword)
        cryptoState.passwordWrappedDekBase64 = passwordWrap.wrappedDekBase64
        cryptoState.passwordWrapSaltBase64 = passwordWrap.saltBase64
        cryptoState.passwordWrapIvBase64 = passwordWrap.ivBase64
        persistState()
    }

    @discardableResult
    func tryUnlockFromKeychain() -> Bool {
        guard cryptoState.enabled,
              let wrappedB64 = cryptoState.keystoreWrappedDekBase64,
              let ivB64 = cryptoState.keystoreWrapIvBase64 else { return false }
        do {
            let dekBytes = try unwrapDekWithKeychain(wrap: KeystoreWrap(wrappedDekBase64: wrappedB64, ivBase64: ivB64))
            dek = SymmetricKey(data: dekBytes)
            return true
        } catch {
            return false
        }
    }

    func applyCloudWrappedDek(_ state: NoteCryptoState) throws {
        guard state.enabled, state.passwordWrappedDekBase64 != nil else { return }
        if dek != nil, let dekBytes = dekData() {
            let keystoreWrap = wrapDekWithKeychain(dekBytes: dekBytes)
            cryptoState = state
            cryptoState.keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64
            cryptoState.keystoreWrapIvBase64 = keystoreWrap.ivBase64
        } else {
            cryptoState = state
        }
        persistState()
    }

    func cloudExportState() -> NoteCryptoState? {
        guard cryptoState.enabled, cryptoState.passwordWrappedDekBase64 != nil else { return nil }
        var exported = cryptoState
        exported.keystoreWrappedDekBase64 = nil
        exported.keystoreWrapIvBase64 = nil
        return exported
    }

    func lock() {
        dek = nil
    }

    func resetStoredCrypto() {
        dek = nil
        cryptoState = NoteCryptoState()
        persistState()
    }

    func encryptForStorage(_ plain: String) -> String {
        let trimmed = plain.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        guard let key = dek else { return trimmed }
        do {
            var iv = Data(count: 12)
            _ = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }
            let nonce = try AES.GCM.Nonce(data: iv)
            let sealed = try AES.GCM.seal(Data(trimmed.utf8), using: key, nonce: nonce)
            let payload = iv + sealed.ciphertext + sealed.tag
            return Self.cipherPrefix + payload.base64EncodedString()
        } catch {
            return trimmed
        }
    }

    func decryptFromStorage(_ stored: String) -> String {
        if stored.isEmpty { return "" }
        guard stored.hasPrefix(Self.cipherPrefix) else { return stored }
        guard let key = dek else { return Self.encryptedPlaceholder }
        let b64 = String(stored.dropFirst(Self.cipherPrefix.count))
        guard let payload = Data(base64Encoded: b64), payload.count > 28 else { return Self.encryptedPlaceholder }
        do {
            let iv = payload.prefix(12)
            let tag = payload.suffix(16)
            let body = payload.dropFirst(12).dropLast(16)
            let box = try AES.GCM.SealedBox(nonce: try AES.GCM.Nonce(data: iv), ciphertext: body, tag: tag)
            let data = try AES.GCM.open(box, using: key)
            return String(data: data, encoding: .utf8) ?? Self.encryptedPlaceholder
        } catch {
            return Self.encryptedPlaceholder
        }
    }

    private struct PasswordWrap {
        var wrappedDekBase64: String
        var saltBase64: String
        var ivBase64: String
    }

    private struct KeystoreWrap {
        var wrappedDekBase64: String
        var ivBase64: String
    }

    private func wrapDekWithPassword(dekBytes: Data, password: String) -> PasswordWrap {
        var salt = Data(count: 16)
        var iv = Data(count: 12)
        _ = salt.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        _ = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }
        let key = SymmetricKey(data: PBKDF2.deriveKey(password: Data(password.utf8), salt: salt))
        let sealed = try! AES.GCM.seal(dekBytes, using: key, nonce: try! AES.GCM.Nonce(data: iv))
        return PasswordWrap(
            wrappedDekBase64: (sealed.ciphertext + sealed.tag).base64EncodedString(),
            saltBase64: salt.base64EncodedString(),
            ivBase64: iv.base64EncodedString()
        )
    }

    private func unwrapDekWithPassword(wrap: PasswordWrap, password: String) throws -> Data {
        let salt = Data(base64Encoded: wrap.saltBase64) ?? Data()
        let iv = Data(base64Encoded: wrap.ivBase64) ?? Data()
        let ciphertext = Data(base64Encoded: wrap.wrappedDekBase64) ?? Data()
        let key = SymmetricKey(data: PBKDF2.deriveKey(password: Data(password.utf8), salt: salt))
        let tag = ciphertext.suffix(16)
        let body = ciphertext.dropLast(16)
        let box = try AES.GCM.SealedBox(nonce: try AES.GCM.Nonce(data: iv), ciphertext: body, tag: tag)
        return try AES.GCM.open(box, using: key)
    }

    private func wrapDekWithKeychain(dekBytes: Data) -> KeystoreWrap {
        let localKey = loadOrCreateLocalWrapKey()
        var iv = Data(count: 12)
        _ = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }
        let sealed = try! AES.GCM.seal(dekBytes, using: localKey, nonce: try! AES.GCM.Nonce(data: iv))
        return KeystoreWrap(
            wrappedDekBase64: (sealed.ciphertext + sealed.tag).base64EncodedString(),
            ivBase64: iv.base64EncodedString()
        )
    }

    private func unwrapDekWithKeychain(wrap: KeystoreWrap) throws -> Data {
        let localKey = loadOrCreateLocalWrapKey()
        let iv = Data(base64Encoded: wrap.ivBase64) ?? Data()
        let ciphertext = Data(base64Encoded: wrap.wrappedDekBase64) ?? Data()
        let tag = ciphertext.suffix(16)
        let body = ciphertext.dropLast(16)
        let box = try AES.GCM.SealedBox(nonce: try AES.GCM.Nonce(data: iv), ciphertext: body, tag: tag)
        return try AES.GCM.open(box, using: localKey)
    }

    private func loadOrCreateLocalWrapKey() -> SymmetricKey {
        if let data = KeychainHelper.load(account: Self.keychainLocalWrapKey), data.count == 32 {
            return SymmetricKey(data: data)
        }
        let bytes = randomBytes(count: 32)
        KeychainHelper.save(account: Self.keychainLocalWrapKey, data: bytes)
        return SymmetricKey(data: bytes)
    }

    private func dekData() -> Data? {
        guard let dek else { return nil }
        return dek.withUnsafeBytes { Data($0) }
    }

    private func randomBytes(count: Int) -> Data {
        var data = Data(count: count)
        _ = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) }
        return data
    }

    private func persistState() {
        guard let encoded = try? encoder.encode(cryptoState) else { return }
        defaults.set(encoded, forKey: Self.stateKey)
    }

    private static func loadState(from defaults: UserDefaults) -> NoteCryptoState {
        guard let data = defaults.data(forKey: stateKey),
              let decoded = try? JSONDecoder().decode(NoteCryptoState.self, from: data) else {
            return NoteCryptoState()
        }
        return decoded
    }
}

private enum KeychainHelper {
    static func save(account: String, data: Data) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }

    static func load(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { return nil }
        return item as? Data
    }
}
