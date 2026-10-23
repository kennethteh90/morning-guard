import Foundation
import MorningGuardCore

@MainActor
final class LocalBackupRepository {
    private let settingsStore: SettingsStore
    private let highlightStore: BibleHighlightStore
    private let noteEncryption: NoteEncryptionService
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(
        settingsStore: SettingsStore,
        highlightStore: BibleHighlightStore,
        noteEncryption: NoteEncryptionService
    ) {
        self.settingsStore = settingsStore
        self.highlightStore = highlightStore
        self.noteEncryption = noteEncryption
    }

    func buildPayload() -> MorningGuardBackupPayload {
        let crypto = noteEncryption.cloudExportState()
        return MorningGuardBackupPayload(
            exportedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            settings: settingsStore.userSettings.withOnboardingCompleted(true),
            goalProgress: settingsStore.goalProgressStore,
            noteCrypto: crypto,
            highlights: highlightStore.highlights.map { $0.toBackupRow() }
        )
    }

    func exportEncrypted(password: String) throws -> Data {
        guard password.count >= 8 else {
            throw NSError(domain: "LocalBackup", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Backup password must be at least 8 characters.",
            ])
        }
        let plaintext = try encoder.encode(buildPayload())
        let encrypted = try LocalBackupCipher.encrypt(plaintext: plaintext, password: password)
        return try encoder.encode(encrypted)
    }

    func importEncrypted(password: String, data: Data) throws {
        guard !data.isEmpty else { throw LocalBackupError.emptyFile }
        let envelope = try decoder.decode(EncryptedLocalBackupFile.self, from: data)
        if envelope.schemaVersion > localBackupSchemaVersion {
            throw NSError(domain: "LocalBackup", code: 2, userInfo: [
                NSLocalizedDescriptionKey:
                    "Backup was made with a newer app version. Update Morning Guard and try again.",
            ])
        }
        let plaintext = try LocalBackupCipher.decrypt(file: envelope, password: password)
        let payload = try decoder.decode(MorningGuardBackupPayload.self, from: plaintext)
        if payload.schemaVersion < 2 {
            throw LocalBackupError.unsupportedSchema
        }
        try applyPayload(payload)
    }

    private func applyPayload(_ payload: MorningGuardBackupPayload) throws {
        var restoredSettings = payload.settings
        restoredSettings.dataStorageMode = .localOnly
        restoredSettings.onboardingCompleted = true
        restoredSettings.syncBoundUserId = nil
        settingsStore.userSettings = restoredSettings
        settingsStore.goalProgressStore = payload.goalProgress

        noteEncryption.lock()
        if let crypto = payload.noteCrypto, crypto.enabled {
            try noteEncryption.applyCloudWrappedDek(crypto.portableForBackup())
        } else {
            noteEncryption.resetStoredCrypto()
        }

        highlightStore.replaceAll(payload.highlights.enumerated().map { index, row in
            BibleHighlight.fromBackupRow(row, id: Int64(index + 1))
        })
    }
}

private extension UserSettings {
    func withOnboardingCompleted(_ completed: Bool) -> UserSettings {
        var copy = self
        copy.onboardingCompleted = completed
        return copy
    }
}

private extension NoteCryptoState {
    func portableForBackup() -> NoteCryptoState {
        var copy = self
        copy.keystoreWrappedDekBase64 = nil
        copy.keystoreWrapIvBase64 = nil
        return copy
    }
}
