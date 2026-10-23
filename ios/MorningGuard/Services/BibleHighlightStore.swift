import Foundation
import MorningGuardCore
import Observation

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

    func saveHighlight(_ highlight: BibleHighlight) {
        var copy = highlight
        copy.note = noteEncryption.encryptForStorage(copy.note)
        if copy.id == 0 {
            copy.id = (highlights.map(\.id).max() ?? 0) + 1
        }
        highlights.removeAll { $0.id == copy.id }
        highlights.append(copy)
        persist()
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
