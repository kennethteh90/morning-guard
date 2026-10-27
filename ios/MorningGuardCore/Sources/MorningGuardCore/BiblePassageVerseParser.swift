import Foundation

public enum BiblePassageVerseParser {
    public enum Segment: Equatable, Sendable {
        case heading(String)
        case verse(number: Int, text: String)
    }

    private static let verseMarker = try! NSRegularExpression(pattern: #"(\[\d+\]|\d+\.)"#)

    public static func segments(blocks: [BiblePassageBlock]) -> [Segment] {
        var out: [Segment] = []
        var pendingVerses: [Segment] = []

        func flushVerses() {
            guard !pendingVerses.isEmpty else { return }
            pendingVerses.sort { lhs, rhs in
                guard case .verse(let ln, _) = lhs, case .verse(let rn, _) = rhs else { return false }
                return ln < rn
            }
            out.append(contentsOf: pendingVerses)
            pendingVerses.removeAll(keepingCapacity: true)
        }

        var lastVerseInSection = 0
        for block in blocks {
            switch block {
            case .sectionHeading(let text):
                flushVerses()
                lastVerseInSection = 0
                out.append(.heading(text.trimmingCharacters(in: .whitespacesAndNewlines)))
            case .paragraph(let text):
                let parsed = versesInParagraph(text, lastVerseInSection: lastVerseInSection)
                for verse in parsed {
                    mergeVerse(into: &pendingVerses, verse: verse)
                }
                if let maxVerse = pendingVerses.compactMap({ segment -> Int? in
                    if case .verse(let number, _) = segment { return number }
                    return nil
                }).max() {
                    lastVerseInSection = maxVerse
                }
            }
        }
        flushVerses()
        return out
    }

    private static func mergeVerse(into pending: inout [Segment], verse: Segment) {
        guard case .verse(let number, let text) = verse else { return }
        if let index = pending.firstIndex(where: { segment in
            if case .verse(let existingNumber, _) = segment { return existingNumber == number }
            return false
        }) {
            guard case .verse(_, let existingText) = pending[index] else { return }
            let combined = "\(existingText)\n\n\(text)".trimmingCharacters(in: .whitespacesAndNewlines)
            pending[index] = .verse(number: number, text: combined)
        } else {
            pending.append(verse)
        }
    }

    private static func versesInParagraph(_ paragraph: String, lastVerseInSection: Int) -> [Segment] {
        let trimmed = paragraph.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return [] }

        let ns = trimmed as NSString
        let fullRange = NSRange(location: 0, length: ns.length)
        let allMatches = verseMarker.matches(in: trimmed, range: fullRange)
        if allMatches.isEmpty {
            return continuationOrFirstVerse(text: trimmed, lastVerseInSection: lastVerseInSection)
        }

        var matches: [NSTextCheckingResult] = []
        var lastVerse = lastVerseInSection
        for match in allMatches {
            let marker = ns.substring(with: match.range)
            let number = verseNumberFromMarker(marker)
            guard isIncreasingVerseBoundary(number: number, lastVerse: lastVerse) else { continue }
            matches.append(match)
            lastVerse = number
        }

        if matches.isEmpty {
            if let orphaned = orphanedExplicitVerse(text: trimmed, lastVerseInSection: lastVerseInSection) {
                return orphaned
            }
            return continuationOrFirstVerse(
                text: stripLeadingVerseMarkers(trimmed),
                lastVerseInSection: lastVerseInSection
            )
        }

        var verses: [Segment] = []
        for i in matches.indices {
            let start = matches[i].range.location
            let end = i + 1 < matches.count ? matches[i + 1].range.location : ns.length
            let chunk = ns.substring(with: NSRange(location: start, length: end - start))
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let marker = ns.substring(with: matches[i].range)
            let number = verseNumberFromMarker(marker)
            var body = chunk.hasPrefix(marker)
                ? String(chunk.dropFirst(marker.count)).trimmingCharacters(in: .whitespacesAndNewlines)
                : chunk
            body = stripEmbeddedPoeticLineNumbers(verseNumber: number, text: body)
            if number > 0, !body.isEmpty {
                verses.append(.verse(number: number, text: body))
            }
        }
        return verses
    }

    private static func orphanedExplicitVerse(text: String, lastVerseInSection: Int) -> [Segment]? {
        guard lastVerseInSection > 0 else { return nil }
        guard let number = explicitLeadingVerseNumber(text) else { return nil }
        guard number < lastVerseInSection else { return nil }
        let body = stripLeadingVerseMarkers(text)
        if body.isEmpty { return nil }
        return [.verse(number: number, text: body)]
    }

    private static func explicitLeadingVerseNumber(_ text: String) -> Int? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let ns = trimmed as NSString
        let range = NSRange(location: 0, length: ns.length)
        guard let match = verseMarker.firstMatch(in: trimmed, range: range),
              match.range.location == 0 else { return nil }
        let marker = ns.substring(with: match.range)
        let number = verseNumberFromMarker(marker)
        return number > 0 ? number : nil
    }

    private static func continuationOrFirstVerse(text: String, lastVerseInSection: Int) -> [Segment] {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return [] }
        if lastVerseInSection > 0 {
            return [.verse(number: lastVerseInSection, text: trimmed)]
        }
        return [.verse(number: 1, text: trimmed)]
    }

    private static func stripLeadingVerseMarkers(_ text: String) -> String {
        var remainder = text.trimmingCharacters(in: .whitespacesAndNewlines)
        while !remainder.isEmpty {
            let ns = remainder as NSString
            let range = NSRange(location: 0, length: ns.length)
            guard let match = verseMarker.firstMatch(in: remainder, range: range),
                  match.range.location == 0 else { break }
            remainder = ns.substring(from: match.range.upperBound).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return remainder.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Poetic / quoted lines inside a verse often repeat `1.` or `[1]`; only split on increasing verse numbers.
    private static func isIncreasingVerseBoundary(number: Int, lastVerse: Int) -> Bool {
        guard number > 0 else { return false }
        if lastVerse == 0 { return true }
        return number > lastVerse
    }

    private static func stripEmbeddedPoeticLineNumbers(verseNumber: Int, text: String) -> String {
        guard verseNumber > 1 else { return text }
        return text
            .replacingOccurrences(of: #"(\r?\n)1\.(\s)"#, with: "$1$2", options: .regularExpression)
            .replacingOccurrences(of: #"(\r?\n)\[1\](\s)"#, with: "$1$2", options: .regularExpression)
    }

    private static func verseNumberFromMarker(_ marker: String) -> Int {
        if let m = marker.range(of: #"\[(\d+)\]"#, options: .regularExpression) {
            let digits = marker[m].filter(\.isNumber)
            return Int(digits) ?? 0
        }
        if let m = marker.range(of: #"(\d+)\."#, options: .regularExpression) {
            let digits = marker[m].filter(\.isNumber)
            return Int(digits) ?? 0
        }
        return 0
    }
}
