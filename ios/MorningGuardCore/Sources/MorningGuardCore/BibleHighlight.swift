import Foundation

public struct BibleHighlight: Codable, Equatable, Identifiable, Sendable {
    public var id: Int64
    public var translationId: String
    public var book: String
    public var chapter: Int
    public var label: String
    public var excerpt: String
    public var verse: Int?
    public var verseEnd: Int?
    public var verseNumbers: String?
    public var colorKey: String
    public var note: String
    public var createdAtEpochMs: Int64

    public init(
        id: Int64 = 0,
        translationId: String,
        book: String,
        chapter: Int,
        label: String,
        excerpt: String,
        verse: Int? = nil,
        verseEnd: Int? = nil,
        verseNumbers: String? = nil,
        colorKey: String = BibleHighlightColors.defaultKey,
        note: String = "",
        createdAtEpochMs: Int64
    ) {
        self.id = id
        self.translationId = translationId
        self.book = book
        self.chapter = chapter
        self.label = label
        self.excerpt = excerpt
        self.verse = verse
        self.verseEnd = verseEnd
        self.verseNumbers = verseNumbers
        self.colorKey = colorKey
        self.note = note
        self.createdAtEpochMs = createdAtEpochMs
    }
}

public enum BibleHighlightColors {
    public static let defaultKey = "yellow"
    public static let keys = ["yellow", "green", "blue", "pink", "purple"]
}

public enum BibleHighlightVerses {
    public static func numbers(highlight: BibleHighlight) -> Set<Int> {
        if let csv = highlight.verseNumbers?.trimmingCharacters(in: .whitespacesAndNewlines), !csv.isEmpty {
            return Set(csv.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) })
        }
        if let start = highlight.verse { return [start] }
        return []
    }
}
