import Foundation

public struct BibleVerseLine: Equatable, Sendable {
    public var number: Int
    public var text: String

    public init(number: Int, text: String) {
        self.number = number
        self.text = text
    }
}

public enum BibleVerseCopyFormat {
    public static func format(
        book: String,
        chapter: Int,
        translationId: String,
        verses: [BibleVerseLine]
    ) -> String {
        let sorted = verses.sorted { $0.number < $1.number }
        if sorted.isEmpty { return "" }
        let reference = BibleVerseReferenceFormat.chapterLabel(
            book: book,
            chapter: chapter,
            verses: sorted.map(\.number),
            translationLabel: BibleTranslations.chipLabel(id: translationId)
        )
        return "\(formatBody(verses: sorted))\n\n\(reference)"
    }

    public static func referenceLine(highlight: BibleHighlight) -> String {
        let verses = BibleHighlightVerses.numbers(highlight: highlight).sorted()
        return BibleVerseReferenceFormat.chapterLabel(
            book: highlight.book,
            chapter: highlight.chapter,
            verses: Array(verses),
            translationLabel: BibleTranslations.chipLabel(id: highlight.translationId)
        )
    }

    public static func formatBody(verses: [BibleVerseLine]) -> String {
        let sorted = verses.sorted { $0.number < $1.number }
        if sorted.isEmpty { return "" }
        if sorted.count == 1 { return sorted[0].text.trimmingCharacters(in: .whitespacesAndNewlines) }
        return sorted.map { line in
            let text = line.text.trimmingCharacters(in: .whitespacesAndNewlines)
            let punctuated = endsSentence(text) ? text : "\(text)."
            return "\(line.number) \(punctuated)"
        }.joined(separator: " ")
    }

    public static func shareText(highlight: BibleHighlight) -> String {
        let body = highlight.excerpt.trimmingCharacters(in: .whitespacesAndNewlines)
        let reference = referenceLine(highlight: highlight)
        if body.isEmpty { return reference }
        return "\(body)\n\n\(reference)"
    }

    public static func bodyFromHighlight(highlight: BibleHighlight) -> String {
        let excerpt = highlight.excerpt.trimmingCharacters(in: .whitespacesAndNewlines)
        return excerpt.isEmpty ? highlight.label : excerpt
    }

    private static func endsSentence(_ text: String) -> Bool {
        guard let last = text.trimmingCharacters(in: .whitespacesAndNewlines).last else { return false }
        return ".!?;:".contains(last)
    }
}
