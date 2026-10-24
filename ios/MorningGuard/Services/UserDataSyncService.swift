import Foundation
import MorningGuardCore
import Supabase

@MainActor
final class UserDataSyncService {
    private let auth: SupabaseAuthService
    private let settingsStore: SettingsStore
    private let highlightStore: BibleHighlightStore
    private let noteEncryption: NoteEncryptionService

    init(
        auth: SupabaseAuthService,
        settingsStore: SettingsStore,
        highlightStore: BibleHighlightStore,
        noteEncryption: NoteEncryptionService
    ) {
        self.auth = auth
        self.settingsStore = settingsStore
        self.highlightStore = highlightStore
        self.noteEncryption = noteEncryption
    }

    func syncAllIfSignedIn() async {
        guard settingsStore.userSettings.dataStorageMode == .cloudSync else { return }
        guard SupabaseConfig.isConfigured, auth.isSignedIn else { return }
        guard let client = makeClient() else { return }
        try? noteEncryption.ensureDekReady()
        highlightStore.migrateLegacyPlaintextNotes()
        await syncNoteCrypto(client: client)
        await syncHighlights(client: client)
    }

    private func makeClient() -> SupabaseClient? {
        guard SupabaseConfig.isConfigured, let url = URL(string: SupabaseConfig.url) else { return nil }
        return SupabaseClient(supabaseURL: url, supabaseKey: SupabaseConfig.anonKey)
    }

    private func syncNoteCrypto(client: SupabaseClient) async {
        do {
            let userId = try await auth.currentUserId()
            let rows: [UserNoteCryptoRow] = try await client
                .from("user_note_crypto")
                .select()
                .eq("user_id", value: userId)
                .execute()
                .value
            if let remote = rows.first {
                let state = NoteCryptoState(
                    enabled: true,
                    passwordWrappedDekBase64: remote.passwordWrappedDek,
                    passwordWrapSaltBase64: remote.saltBase64,
                    passwordWrapIvBase64: remote.wrapIvBase64
                )
                try noteEncryption.applyCloudWrappedDek(state)
                _ = noteEncryption.tryUnlockFromKeychain()
            }
            if let export = noteEncryption.cloudExportState(),
               let wrapped = export.passwordWrappedDekBase64,
               let salt = export.passwordWrapSaltBase64,
               let iv = export.passwordWrapIvBase64 {
                let row = UserNoteCryptoRow(
                    userId: userId,
                    passwordWrappedDek: wrapped,
                    saltBase64: salt,
                    wrapIvBase64: iv,
                    kdfIterations: NoteCryptoConstants.pbkdf2Iterations
                )
                try await client.from("user_note_crypto").upsert(row, onConflict: "user_id").execute()
            }
        } catch {
            // Non-fatal; sync retries on next sign-in or save.
        }
    }

    private func syncHighlights(client: SupabaseClient) async {
        do {
            let userId = try await auth.currentUserId()
            let bound = settingsStore.userSettings.syncBoundUserId
            if let bound, bound != userId {
                highlightStore.clearAll()
                settingsStore.clearMorningHistory()
            }
            settingsStore.setBoundUserId(userId)

            let remoteRows: [BibleHighlightRow] = try await client
                .from("bible_highlights")
                .select()
                .execute()
                .value

            let remoteNoteByServerId = Dictionary(
                uniqueKeysWithValues: remoteRows.compactMap { row -> (Int64, String)? in
                    guard let id = row.id else { return nil }
                    return (id, row.note ?? "")
                }
            )

            if !noteEncryption.requiresCloudRecovery() {
                let domain = remoteRows.compactMap { $0.toDomain() }
                highlightStore.reconcileWithRemote(domain)
            }

            if noteEncryption.requiresCloudRecovery() { return }
            guard SyncOwnership.shouldUploadLocalHighlights(boundUserId: bound, currentUserId: userId) else {
                return
            }

            let local = highlightStore.highlights
            guard !local.isEmpty else { return }

            let pushRows = buildHighlightPushRows(
                local: local,
                userId: userId,
                remoteNoteByServerId: remoteNoteByServerId
            )
            guard !pushRows.isEmpty else { return }
            try await client.from("bible_highlights").upsert(pushRows, onConflict: "id").execute()

            let refreshed: [BibleHighlightRow] = try await client
                .from("bible_highlights")
                .select()
                .execute()
                .value
            if !noteEncryption.requiresCloudRecovery() {
                highlightStore.reconcileWithRemote(refreshed.compactMap { $0.toDomain() })
            }
        } catch {
            // logged on next attempt
        }
    }

    private func buildHighlightPushRows(
        local: [BibleHighlight],
        userId: String,
        remoteNoteByServerId: [Int64: String]
    ) -> [BibleHighlightRow] {
        local.compactMap { highlight in
            let remoteNote = highlight.remoteId > 0 ? remoteNoteByServerId[highlight.remoteId] : nil
            let plainNote = highlightStore.displayNote(for: highlight)
            let decision = HighlightSyncNotePolicy.resolve(
                localPlainNote: plainNote,
                remoteNote: remoteNote,
                dekUnlocked: noteEncryption.isUnlocked
            )
            if case .omitNote = decision { return nil }
            let wireNote: String?
            do {
                wireNote = try HighlightSyncNotePolicy.toWireValue(decision: decision) { plain in
                    try noteEncryption.encryptForStorage(plain)
                }
            } catch {
                return nil
            }
            let noteForRow: String
            if let wireNote {
                noteForRow = wireNote
            } else if let remoteNote, NoteContentCipher.isCiphertext(remoteNote) {
                noteForRow = remoteNote
            } else {
                noteForRow = ""
            }
            return BibleHighlightRow(
                id: highlight.remoteId > 0 ? highlight.remoteId : nil,
                userId: userId,
                clientId: highlight.id > 0 ? highlight.id : nil,
                translationId: highlight.translationId,
                book: highlight.book,
                chapter: highlight.chapter,
                label: highlight.label,
                excerpt: highlight.excerpt,
                verse: highlight.verse,
                verseEnd: highlight.verseEnd,
                verseNumbers: highlight.verseNumbers,
                colorKey: highlight.colorKey.isEmpty ? BibleHighlightColors.defaultKey : highlight.colorKey,
                note: noteForRow,
                selectionStart: highlight.selectionStart,
                selectionEnd: highlight.selectionEnd,
                createdAt: isoString(epochMs: highlight.createdAtEpochMs)
            )
        }
    }

    private func isoString(epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000)
        return ISO8601DateFormatter().string(from: date)
    }
}

private struct UserNoteCryptoRow: Codable {
    var userId: String
    var passwordWrappedDek: String
    var saltBase64: String
    var wrapIvBase64: String
    var kdfIterations: Int

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case passwordWrappedDek = "password_wrapped_dek"
        case saltBase64 = "salt_base64"
        case wrapIvBase64 = "wrap_iv_base64"
        case kdfIterations = "kdf_iterations"
    }
}

private struct BibleHighlightRow: Codable {
    var id: Int64?
    var userId: String
    var clientId: Int64?
    var translationId: String
    var book: String
    var chapter: Int
    var label: String
    var excerpt: String
    var verse: Int?
    var verseEnd: Int?
    var verseNumbers: String?
    var colorKey: String?
    var note: String?
    var selectionStart: Int?
    var selectionEnd: Int?
    var createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case clientId = "client_id"
        case translationId = "translation_id"
        case book, chapter, label, excerpt, verse
        case verseEnd = "verse_end"
        case verseNumbers = "verse_numbers"
        case colorKey = "color_key"
        case note
        case selectionStart = "selection_start"
        case selectionEnd = "selection_end"
        case createdAt = "created_at"
    }

    func toDomain() -> BibleHighlight? {
        guard let serverId = id else { return nil }
        let createdMs = createdAt.flatMap { ISO8601DateFormatter().date(from: $0)?.timeIntervalSince1970 }
            .map { Int64($0 * 1000) } ?? Int64(Date().timeIntervalSince1970 * 1000)
        return BibleHighlight(
            id: clientId ?? 0,
            remoteId: serverId,
            translationId: translationId,
            book: book,
            chapter: chapter,
            label: label,
            excerpt: excerpt,
            verse: verse,
            verseEnd: verseEnd,
            verseNumbers: verseNumbers,
            colorKey: colorKey ?? BibleHighlightColors.defaultKey,
            note: note ?? "",
            selectionStart: selectionStart,
            selectionEnd: selectionEnd,
            createdAtEpochMs: createdMs
        )
    }
}
