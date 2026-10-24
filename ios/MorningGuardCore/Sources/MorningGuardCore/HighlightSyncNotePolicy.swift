import Foundation

public enum HighlightSyncNotePolicy {
    public enum NotePushValue: Equatable {
        case encryptLocal(plainNote: String)
        case preserveRemote(remoteNote: String)
        case omitNote
    }

    public static func resolve(
        localPlainNote: String,
        remoteNote: String?,
        dekUnlocked: Bool
    ) -> NotePushValue {
        let trimmedLocal = localPlainNote.trimmingCharacters(in: .whitespacesAndNewlines)
        let remote = remoteNote?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        if !dekUnlocked {
            if NoteContentCipher.isCiphertext(remote) {
                return .preserveRemote(remoteNote: remote)
            }
            if !trimmedLocal.isEmpty {
                return .omitNote
            }
            return .omitNote
        }

        if trimmedLocal.isEmpty {
            return .encryptLocal(plainNote: "")
        }
        return .encryptLocal(plainNote: trimmedLocal)
    }

    public static func toWireValue(
        decision: NotePushValue,
        encrypt: (String) throws -> String
    ) rethrows -> String? {
        switch decision {
        case .encryptLocal(let plain):
            return try encrypt(plain)
        case .preserveRemote(let remote):
            return remote
        case .omitNote:
            return nil
        }
    }
}
