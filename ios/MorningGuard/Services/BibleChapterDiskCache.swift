import Foundation
import MorningGuardCore

struct BibleChapterDiskCache: Sendable {
    private struct CacheEntry: Codable {
        var cachedAtEpochMs: Int64
        var reference: String
        var blocks: [BlockDto]
    }

    private struct BlockDto: Codable {
        var type: String
        var text: String
    }

    private let directory: URL

    init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        directory = base.appendingPathComponent("bible-chapters", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    func get(translation: String, book: String, chapter: Int) -> BibleChapterPassage? {
        let url = fileURL(translation: translation, book: book, chapter: chapter)
        guard let data = try? Data(contentsOf: url),
              let entry = try? JSONDecoder().decode(CacheEntry.self, from: data) else {
            return nil
        }
        let age = Date().timeIntervalSince1970 - Double(entry.cachedAtEpochMs) / 1000
        if age > BibleChapterCachePolicy.ttl(forTranslationId: translation) {
            try? FileManager.default.removeItem(at: url)
            return nil
        }
        return mapEntry(entry)
    }

    func put(_ passage: BibleChapterPassage, translation: String, book: String, chapter: Int) {
        let blocks = passage.blocks.compactMap { block -> BlockDto? in
            switch block {
            case .sectionHeading(let text):
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return nil }
                return BlockDto(type: "heading", text: trimmed)
            case .paragraph(let text):
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return nil }
                return BlockDto(type: "paragraph", text: trimmed)
            }
        }
        let entry = CacheEntry(
            cachedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            reference: passage.reference,
            blocks: blocks
        )
        guard let data = try? JSONEncoder().encode(entry) else { return }
        let url = fileURL(translation: translation, book: book, chapter: chapter)
        try? data.write(to: url, options: .atomic)
    }

    func containsFresh(translation: String, book: String, chapter: Int) -> Bool {
        get(translation: translation, book: book, chapter: chapter) != nil
    }

    private func fileURL(translation: String, book: String, chapter: Int) -> URL {
        let id = BibleChapterMemoryCache.shared.cacheId(translation: translation, book: book, chapter: chapter)
        let safe = id.replacingOccurrences(of: #"[^a-zA-Z0-9._-]"#, with: "_", options: .regularExpression)
        return directory.appendingPathComponent("\(safe).json")
    }

    private func mapEntry(_ entry: CacheEntry) -> BibleChapterPassage {
        let blocks: [BiblePassageBlock] = entry.blocks.compactMap { dto in
            let text = dto.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else { return nil }
            switch dto.type.lowercased() {
            case "heading": return .sectionHeading(text)
            case "paragraph": return .paragraph(text)
            default: return nil
            }
        }
        return BibleChapterPassage(reference: entry.reference, blocks: blocks)
    }
}
