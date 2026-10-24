import Foundation
import MorningGuardCore
import Observation

// Highlight notes are stored encrypted; sync uses plaintext only in memory via displayNote().

@MainActor
@Observable
final class BibleHighlightStore {
    private let defaults: UserDefaults
    private let noteEncryption: NoteEncryptionService
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private static let storageKey = "bibleHighlights"
    private static let lastColorKey = "lastHighlightColorKey"

    var highlights: [BibleHighlight] = []
    var lastHighlightColorKey: String

    init(defaults: UserDefaults = .standard, noteEncryption: NoteEncryptionService) {
        self.defaults = defaults
        self.noteEncryption = noteEncryption
        lastHighlightColorKey = defaults.string(forKey: Self.lastColorKey) ?? BibleHighlightColors.defaultKey
        highlights = Self.load(from: defaults, noteEncryption: noteEncryption)
    }

    func highlightsForChapter(translationId: String, book: String, chapter: Int) -> [BibleHighlight] {
        highlights.filter {
            $0.translationId.caseInsensitiveCompare(translationId) == .orderedSame
                && $0.book.caseInsensitiveCompare(book) == .orderedSame
                && $0.chapter == chapter
        }
    }

    func saveHighlight(_ highlight: BibleHighlight, onNoteSaved: (() -> Void)? = nil) {
        var copy = highlight
        let hadNote = !copy.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        copy.note = (try? noteEncryption.encryptForStorage(copy.note)) ?? copy.note
        if copy.id == 0 {
            copy.id = (highlights.map(\.id).max() ?? 0) + 1
        }
        highlights.removeAll { $0.id == copy.id }
        highlights.append(copy)
        persist()
        if hadNote { onNoteSaved?() }
    }

    func migrateLegacyPlaintextNotes() {
        var changed = false
        highlights = highlights.map { highlight in
            guard NoteContentCipher.isLegacyPlaintext(highlight.note) else { return highlight }
            var copy = highlight
            copy.note = (try? noteEncryption.reencryptPlaintextValue(highlight.note)) ?? highlight.note
            changed = true
            return copy
        }
        if changed { persist() }
    }

    func reencryptAllStoredNotes() {
        var changed = false
        highlights = highlights.map { highlight in
            let plain = noteEncryption.decryptFromStorage(highlight.note)
            guard !plain.isEmpty else { return highlight }
            var copy = highlight
            copy.note = (try? noteEncryption.encryptForStorage(plain)) ?? highlight.note
            changed = true
            return copy
        }
        if changed { persist() }
    }

    func reconcileWithRemote(_ remoteHighlights: [BibleHighlight]) {
        let remoteIds = Set(remoteHighlights.map(\.remoteId).filter { $0 > 0 })
        highlights.removeAll { local in
            local.remoteId > 0 && !remoteIds.contains(local.remoteId)
        }
        for remote in remoteHighlights where remote.remoteId > 0 {
            mergeOneRemote(remote)
        }
        persist()
    }

    func clearAll() {
        highlights = []
        persist()
    }

    private func mergeOneRemote(_ remote: BibleHighlight) {
        let noteStored: String
        if NoteContentCipher.isLegacyPlaintext(remote.note) {
            noteStored = (try? noteEncryption.encryptForStorage(remote.note)) ?? remote.note
        } else {
            noteStored = remote.note
        }
        let remoteNormalized = BibleHighlight(
            id: remote.id,
            remoteId: remote.remoteId,
            translationId: remote.translationId,
            book: remote.book,
            chapter: remote.chapter,
            label: remote.label,
            excerpt: remote.excerpt,
            verse: remote.verse,
            verseEnd: remote.verseEnd,
            verseNumbers: remote.verseNumbers,
            colorKey: remote.colorKey,
            note: noteStored,
            selectionStart: remote.selectionStart,
            selectionEnd: remote.selectionEnd,
            createdAtEpochMs: remote.createdAtEpochMs
        )
        if let index = highlights.firstIndex(where: { $0.remoteId == remoteNormalized.remoteId }) {
            highlights[index] = remoteNormalized
            return
        }
        if remoteNormalized.id > 0, let index = highlights.firstIndex(where: { $0.id == remoteNormalized.id }) {
            highlights[index] = remoteNormalized
            return
        }
        highlights.append(remoteNormalized)
    }

    func replaceAll(_ newHighlights: [BibleHighlight]) {
        highlights = newHighlights
        persist()
    }

    func displayNote(for highlight: BibleHighlight) -> String {
        noteEncryption.decryptFromStorage(highlight.note)
    }

    func deleteHighlight(id: Int64) {
        highlights.removeAll { $0.id == id }
        persist()
    }

    @discardableResult
    func deleteHighlightReturningRemoved(id: Int64) -> BibleHighlight? {
        guard let index = highlights.firstIndex(where: { $0.id == id }) else { return nil }
        let removed = highlights[index]
        highlights.remove(at: index)
        persist()
        return removed
    }

    func restoreHighlight(_ highlight: BibleHighlight) {
        highlights.removeAll { $0.id == highlight.id }
        highlights.append(highlight)
        highlights.sort { $0.createdAtEpochMs > $1.createdAtEpochMs }
        persist()
    }

    func setLastHighlightColorKey(_ key: String) {
        let normalized = BibleHighlightColors.keys.contains(key) ? key : BibleHighlightColors.defaultKey
        lastHighlightColorKey = normalized
        defaults.set(normalized, forKey: Self.lastColorKey)
    }

    private func persist() {
        guard let data = try? encoder.encode(highlights) else { return }
        defaults.set(data, forKey: Self.storageKey)
    }

    private static func load(from defaults: UserDefaults, noteEncryption: NoteEncryptionService) -> [BibleHighlight] {
        guard let data = defaults.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([BibleHighlight].self, from: data) else {
            return []
        }
        return decoded.sorted { $0.createdAtEpochMs > $1.createdAtEpochMs }
    }
}
