import Foundation

public struct BibleHighlight: Codable, Equatable, Identifiable, Sendable {
    public var id: Int64
    public var remoteId: Int64
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
    public var selectionStart: Int?
    public var selectionEnd: Int?
    public var createdAtEpochMs: Int64

    public init(
        id: Int64 = 0,
        remoteId: Int64 = 0,
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
        selectionStart: Int? = nil,
        selectionEnd: Int? = nil,
        createdAtEpochMs: Int64
    ) {
        self.id = id
        self.remoteId = remoteId
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
        self.selectionStart = selectionStart
        self.selectionEnd = selectionEnd
        self.createdAtEpochMs = createdAtEpochMs
    }
}

extension BibleHighlight {
    private enum CodingKeys: String, CodingKey {
        case id, remoteId, translationId, book, chapter, label, excerpt
        case verse, verseEnd, verseNumbers, colorKey, note
        case selectionStart, selectionEnd, createdAtEpochMs
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int64.self, forKey: .id)
        remoteId = try container.decodeIfPresent(Int64.self, forKey: .remoteId) ?? 0
        translationId = try container.decode(String.self, forKey: .translationId)
        book = try container.decode(String.self, forKey: .book)
        chapter = try container.decode(Int.self, forKey: .chapter)
        label = try container.decode(String.self, forKey: .label)
        excerpt = try container.decode(String.self, forKey: .excerpt)
        verse = try container.decodeIfPresent(Int.self, forKey: .verse)
        verseEnd = try container.decodeIfPresent(Int.self, forKey: .verseEnd)
        verseNumbers = try container.decodeIfPresent(String.self, forKey: .verseNumbers)
        colorKey = try container.decodeIfPresent(String.self, forKey: .colorKey) ?? BibleHighlightColors.defaultKey
        note = try container.decodeIfPresent(String.self, forKey: .note) ?? ""
        selectionStart = try container.decodeIfPresent(Int.self, forKey: .selectionStart)
        selectionEnd = try container.decodeIfPresent(Int.self, forKey: .selectionEnd)
        createdAtEpochMs = try container.decode(Int64.self, forKey: .createdAtEpochMs)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        if remoteId > 0 { try container.encode(remoteId, forKey: .remoteId) }
        try container.encode(translationId, forKey: .translationId)
        try container.encode(book, forKey: .book)
        try container.encode(chapter, forKey: .chapter)
        try container.encode(label, forKey: .label)
        try container.encode(excerpt, forKey: .excerpt)
        try container.encodeIfPresent(verse, forKey: .verse)
        try container.encodeIfPresent(verseEnd, forKey: .verseEnd)
        try container.encodeIfPresent(verseNumbers, forKey: .verseNumbers)
        try container.encode(colorKey, forKey: .colorKey)
        try container.encode(note, forKey: .note)
        try container.encodeIfPresent(selectionStart, forKey: .selectionStart)
        try container.encodeIfPresent(selectionEnd, forKey: .selectionEnd)
        try container.encode(createdAtEpochMs, forKey: .createdAtEpochMs)
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
