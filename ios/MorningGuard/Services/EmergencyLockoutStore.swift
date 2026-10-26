import Foundation
import Security

struct EmergencyLockoutState: Codable, Equatable, Sendable {
    var failedAttempts: Int
    var lockedUntilEpochMs: Int64?

    static let empty = EmergencyLockoutState(failedAttempts: 0, lockedUntilEpochMs: nil)
}

enum EmergencyLockoutStoreError: Error {
    case keychainFailure(OSStatus)
}

/// Passphrase lockout counters — Keychain only (not included in iCloud backup JSON).
final class EmergencyLockoutStore: Sendable {
    private let service = "com.morningguard.emergency"
    private let account = "lockout"

    func load() -> EmergencyLockoutState {
        guard let data = readKeychainData(),
              let state = try? JSONDecoder().decode(EmergencyLockoutState.self, from: data) else {
            return .empty
        }
        return state
    }

    func save(_ state: EmergencyLockoutState) throws {
        let data = try JSONEncoder().encode(state)
        try writeKeychainData(data)
    }

    func clear() throws {
        try deleteKeychainItem()
    }

    private func readKeychainData() -> Data? {
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
        return data
    }

    private func writeKeychainData(_ data: Data) throws {
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
            guard update == errSecSuccess else { throw EmergencyLockoutStoreError.keychainFailure(update) }
        } else {
            var addQuery = query
            addQuery[kSecValueData as String] = data
            addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let add = SecItemAdd(addQuery as CFDictionary, nil)
            guard add == errSecSuccess else { throw EmergencyLockoutStoreError.keychainFailure(add) }
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
            throw EmergencyLockoutStoreError.keychainFailure(status)
        }
    }
}
