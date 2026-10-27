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

        for block in blocks {
            switch block {
            case .sectionHeading(let text):
                flushVerses()
                out.append(.heading(text.trimmingCharacters(in: .whitespacesAndNewlines)))
            case .paragraph(let text):
                pendingVerses.append(contentsOf: versesInParagraph(text))
            }
        }
        flushVerses()
        return out
    }

    private static func versesInParagraph(_ paragraph: String) -> [Segment] {
        let trimmed = paragraph.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return [] }

        let ns = trimmed as NSString
        let fullRange = NSRange(location: 0, length: ns.length)
        let allMatches = verseMarker.matches(in: trimmed, range: fullRange)
        if allMatches.isEmpty {
            return [.verse(number: 1, text: trimmed)]
        }

        var matches: [NSTextCheckingResult] = []
        var lastVerse = 0
        for match in allMatches {
            let marker = ns.substring(with: match.range)
            let number = verseNumberFromMarker(marker)
            guard isIncreasingVerseBoundary(number: number, lastVerse: lastVerse) else { continue }
            matches.append(match)
            lastVerse = number
        }

        if matches.isEmpty {
            return [.verse(number: 1, text: trimmed)]
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
