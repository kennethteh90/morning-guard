import CryptoKit
import Foundation
import MorningGuardCore
import Security

struct NoteCryptoState: Codable, Equatable {
    var enabled: Bool
    var passwordWrappedDekBase64: String?
    var passwordWrapSaltBase64: String?
    var passwordWrapIvBase64: String?
    var keystoreWrappedDekBase64: String?
    var keystoreWrapIvBase64: String?
    var pendingCloudPasswordWrappedDekBase64: String?
    var pendingCloudPasswordWrapSaltBase64: String?
    var pendingCloudPasswordWrapIvBase64: String?
    var cloudRecoveryRequired: Bool

    init(
        enabled: Bool = false,
        passwordWrappedDekBase64: String? = nil,
        passwordWrapSaltBase64: String? = nil,
        passwordWrapIvBase64: String? = nil,
        keystoreWrappedDekBase64: String? = nil,
        keystoreWrapIvBase64: String? = nil,
        pendingCloudPasswordWrappedDekBase64: String? = nil,
        pendingCloudPasswordWrapSaltBase64: String? = nil,
        pendingCloudPasswordWrapIvBase64: String? = nil,
        cloudRecoveryRequired: Bool = false
    ) {
        self.enabled = enabled
        self.passwordWrappedDekBase64 = passwordWrappedDekBase64
        self.passwordWrapSaltBase64 = passwordWrapSaltBase64
        self.passwordWrapIvBase64 = passwordWrapIvBase64
        self.keystoreWrappedDekBase64 = keystoreWrappedDekBase64
        self.keystoreWrapIvBase64 = keystoreWrapIvBase64
        self.pendingCloudPasswordWrappedDekBase64 = pendingCloudPasswordWrappedDekBase64
        self.pendingCloudPasswordWrapSaltBase64 = pendingCloudPasswordWrapSaltBase64
        self.pendingCloudPasswordWrapIvBase64 = pendingCloudPasswordWrapIvBase64
        self.cloudRecoveryRequired = cloudRecoveryRequired
    }

    func hasRecoveryPassword() -> Bool {
        guard let wrapped = passwordWrappedDekBase64 else { return false }
        return !wrapped.isEmpty
    }

    func hasPendingCloudRecovery() -> Bool {
        cloudRecoveryRequired && pendingCloudPasswordWrappedDekBase64 != nil
    }
}

@MainActor
@Observable
final class NoteEncryptionService {
    static let cipherPrefix = NoteContentCipher.cipherPrefix
    static let encryptedPlaceholder = NoteContentCipher.encryptedPlaceholder

    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private static let stateKey = "noteCryptoState"
    private static let keychainLocalWrapKey = "com.morningguard.app.note-local-wrap"

    private var dek: SymmetricKey?

    var cryptoState: NoteCryptoState

    var isUnlocked: Bool { dek != nil }

    func requiresCloudRecovery() -> Bool {
        cryptoState.hasPendingCloudRecovery()
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        cryptoState = Self.loadState(from: defaults)
        _ = tryUnlockFromKeychain()
        if dek == nil {
            try? ensureDekReady()
        }
    }

    func ensureDekReady() throws {
        if dek != nil { return }
        if cryptoState.enabled, tryUnlockFromKeychain() { return }
        let dekBytes = randomBytes(count: 32)
        let keystoreWrap = wrapDekWithKeychain(dekBytes: dekBytes)
        cryptoState.enabled = true
        cryptoState.keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64
        cryptoState.keystoreWrapIvBase64 = keystoreWrap.ivBase64
        dek = SymmetricKey(data: dekBytes)
        persistState()
    }

    func setRecoveryPassword(_ password: String) throws {
        if let error = RecoveryPasswordRules.validate(password: password) {
            throw NSError(domain: "NotesCrypto", code: 1, userInfo: [NSLocalizedDescriptionKey: error])
        }
        try ensureDekReady()
        guard let dekBytes = dekData() else { throw NSError(domain: "NotesCrypto", code: 3) }
        let passwordWrap = try NoteCryptoPasswordWrap.wrapDek(dekBytes: dekBytes, password: password)
        cryptoState.passwordWrappedDekBase64 = passwordWrap.wrappedDekBase64
        cryptoState.passwordWrapSaltBase64 = passwordWrap.saltBase64
        cryptoState.passwordWrapIvBase64 = passwordWrap.ivBase64
        persistState()
    }

    func setupWithPassword(_ password: String) throws {
        try setRecoveryPassword(password)
    }

    func unlockWithPassword(_ password: String) throws {
        let pending = cryptoState.hasPendingCloudRecovery()
        let wrapped = pending
            ? cryptoState.pendingCloudPasswordWrappedDekBase64
            : cryptoState.passwordWrappedDekBase64
        let saltB64 = pending
            ? cryptoState.pendingCloudPasswordWrapSaltBase64
            : cryptoState.passwordWrapSaltBase64
        let ivB64 = pending
            ? cryptoState.pendingCloudPasswordWrapIvBase64
            : cryptoState.passwordWrapIvBase64

        guard let wrapped, let saltB64, let ivB64 else {
            throw NSError(domain: "NotesCrypto", code: 2, userInfo: [
                NSLocalizedDescriptionKey: "Notes encryption is not set up on this device.",
            ])
        }
        let wrap = NotePasswordWrap(wrappedDekBase64: wrapped, saltBase64: saltB64, ivBase64: ivB64)
        let dekBytes = try NoteCryptoPasswordWrap.unwrapDek(wrap: wrap, password: password)
        let keystoreWrap = wrapDekWithKeychain(dekBytes: dekBytes)
        cryptoState.enabled = true
        cryptoState.passwordWrappedDekBase64 = wrapped
        cryptoState.passwordWrapSaltBase64 = saltB64
        cryptoState.passwordWrapIvBase64 = ivB64
        cryptoState.keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64
        cryptoState.keystoreWrapIvBase64 = keystoreWrap.ivBase64
        cryptoState.pendingCloudPasswordWrappedDekBase64 = nil
        cryptoState.pendingCloudPasswordWrapSaltBase64 = nil
        cryptoState.pendingCloudPasswordWrapIvBase64 = nil
        cryptoState.cloudRecoveryRequired = false
        dek = SymmetricKey(data: dekBytes)
        persistState()
    }

    func changePassword(oldPassword: String, newPassword: String) throws {
        if let error = RecoveryPasswordRules.validate(password: newPassword) {
            throw NSError(domain: "NotesCrypto", code: 1, userInfo: [NSLocalizedDescriptionKey: error])
        }
        try unlockWithPassword(oldPassword)
        guard let dekBytes = dekData() else { throw NSError(domain: "NotesCrypto", code: 3) }
        let passwordWrap = try NoteCryptoPasswordWrap.wrapDek(dekBytes: dekBytes, password: newPassword)
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
        let local = cryptoState
        let localHasPassword = local.hasRecoveryPassword()
        let localAutoDeviceKey = local.enabled && !localHasPassword

        if localAutoDeviceKey {
            cryptoState.pendingCloudPasswordWrappedDekBase64 = state.passwordWrappedDekBase64
            cryptoState.pendingCloudPasswordWrapSaltBase64 = state.passwordWrapSaltBase64
            cryptoState.pendingCloudPasswordWrapIvBase64 = state.passwordWrapIvBase64
            cryptoState.cloudRecoveryRequired = true
            persistState()
            return
        }

        if dek != nil, let dekBytes = dekData() {
            let keystoreWrap = wrapDekWithKeychain(dekBytes: dekBytes)
            cryptoState = state
            cryptoState.keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64
            cryptoState.keystoreWrapIvBase64 = keystoreWrap.ivBase64
            cryptoState.pendingCloudPasswordWrappedDekBase64 = nil
            cryptoState.pendingCloudPasswordWrapSaltBase64 = nil
            cryptoState.pendingCloudPasswordWrapIvBase64 = nil
            cryptoState.cloudRecoveryRequired = false
        } else {
            cryptoState = state
            cryptoState.pendingCloudPasswordWrappedDekBase64 = nil
            cryptoState.pendingCloudPasswordWrapSaltBase64 = nil
            cryptoState.pendingCloudPasswordWrapIvBase64 = nil
            cryptoState.cloudRecoveryRequired = false
        }
        persistState()
    }

    func abandonPendingCloudRecovery() throws {
        try ensureDekReady()
        cryptoState.pendingCloudPasswordWrappedDekBase64 = nil
        cryptoState.pendingCloudPasswordWrapSaltBase64 = nil
        cryptoState.pendingCloudPasswordWrapIvBase64 = nil
        cryptoState.cloudRecoveryRequired = false
        persistState()
    }

    func cloudExportState() -> NoteCryptoState? {
        guard cryptoState.enabled, cryptoState.hasRecoveryPassword() else { return nil }
        if cryptoState.cloudRecoveryRequired { return nil }
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

    func encryptForStorage(_ plain: String) throws -> String {
        try ensureDekReady()
        guard let key = dek else {
            throw NSError(domain: "NotesCrypto", code: 4, userInfo: [
                NSLocalizedDescriptionKey: "Notes encryption key is locked.",
            ])
        }
        return try NoteContentCipher.encrypt(plain: plain, dek: key)
    }

    func decryptFromStorage(_ stored: String) -> String {
        if stored.isEmpty { return "" }
        guard NoteContentCipher.isCiphertext(stored) else { return stored }
        guard let key = dek else { return Self.encryptedPlaceholder }
        return (try? NoteContentCipher.decrypt(stored: stored, dek: key)) ?? Self.encryptedPlaceholder
    }

    func reencryptPlaintextValue(_ stored: String) throws -> String {
        if stored.isEmpty || NoteContentCipher.isCiphertext(stored) { return stored }
        try ensureDekReady()
        return try encryptForStorage(stored)
    }

    private struct KeystoreWrap {
        var wrappedDekBase64: String
        var ivBase64: String
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
