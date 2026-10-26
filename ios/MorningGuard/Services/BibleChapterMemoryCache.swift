import Foundation
import MorningGuardCore

final class BibleChapterMemoryCache: @unchecked Sendable {
    static let shared = BibleChapterMemoryCache()

    private let lock = NSLock()
    private var entries: [String: BibleChapterPassage] = [:]
    private let maxEntries = 16

    private init() {}

    func cacheId(translation: String, book: String, chapter: Int) -> String {
        "\(BibleTranslations.normalizeId(translation))|\(book.trimmingCharacters(in: .whitespacesAndNewlines))|\(chapter)"
    }

    func get(translation: String, book: String, chapter: Int) -> BibleChapterPassage? {
        let id = cacheId(translation: translation, book: book, chapter: chapter)
        lock.lock()
        defer { lock.unlock() }
        return entries[id]
    }

    func put(_ passage: BibleChapterPassage, translation: String, book: String, chapter: Int) {
        let id = cacheId(translation: translation, book: book, chapter: chapter)
        lock.lock()
        entries[id] = passage
        if entries.count > maxEntries {
            let dropKey = entries.keys.first
            if let dropKey { entries.removeValue(forKey: dropKey) }
        }
        lock.unlock()
    }

    func contains(translation: String, book: String, chapter: Int) -> Bool {
        get(translation: translation, book: book, chapter: chapter) != nil
    }
}
