import Foundation
import MorningGuardCore

let localBackupSchemaVersion = 2

struct MorningGuardBackupPayload: Codable {
    var schemaVersion: Int
    var exportedAtEpochMs: Int64
    var settings: UserSettings
    var goalProgress: GoalProgressStore
    var noteCrypto: NoteCryptoState?
    var highlights: [BibleHighlightBackupRow]

    init(
        exportedAtEpochMs: Int64,
        settings: UserSettings,
        goalProgress: GoalProgressStore,
        noteCrypto: NoteCryptoState?,
        highlights: [BibleHighlightBackupRow]
    ) {
        schemaVersion = localBackupSchemaVersion
        self.exportedAtEpochMs = exportedAtEpochMs
        self.settings = settings
        self.goalProgress = goalProgress
        self.noteCrypto = noteCrypto
        self.highlights = highlights
    }
}

struct BibleHighlightBackupRow: Codable, Equatable {
    var translationId: String
    var book: String
    var chapter: Int
    var label: String
    var excerpt: String
    var verse: Int?
    var verseEnd: Int?
    var verseNumbers: String?
    var colorKey: String
    var note: String
    var selectionStart: Int?
    var selectionEnd: Int?
    var createdAtEpochMs: Int64
}

struct EncryptedLocalBackupFile: Codable {
    var schemaVersion: Int
    var saltBase64: String
    var ivBase64: String
    var ciphertextBase64: String

    init(schemaVersion: Int = localBackupSchemaVersion, saltBase64: String, ivBase64: String, ciphertextBase64: String) {
        self.schemaVersion = schemaVersion
        self.saltBase64 = saltBase64
        self.ivBase64 = ivBase64
        self.ciphertextBase64 = ciphertextBase64
    }
}

extension BibleHighlight {
    func toBackupRow() -> BibleHighlightBackupRow {
        BibleHighlightBackupRow(
            translationId: translationId,
            book: book,
            chapter: chapter,
            label: label,
            excerpt: excerpt,
            verse: verse,
            verseEnd: verseEnd,
            verseNumbers: verseNumbers,
            colorKey: colorKey,
            note: note,
            createdAtEpochMs: createdAtEpochMs
        )
    }

    static func fromBackupRow(_ row: BibleHighlightBackupRow, id: Int64) -> BibleHighlight {
        BibleHighlight(
            id: id,
            translationId: row.translationId,
            book: row.book,
            chapter: row.chapter,
            label: row.label,
            excerpt: row.excerpt,
            verse: row.verse,
            verseEnd: row.verseEnd,
            verseNumbers: row.verseNumbers,
            colorKey: row.colorKey,
            note: row.note,
            createdAtEpochMs: row.createdAtEpochMs
        )
    }
}
