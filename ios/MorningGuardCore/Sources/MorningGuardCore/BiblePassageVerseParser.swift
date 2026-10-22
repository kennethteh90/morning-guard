import Foundation

public enum BiblePassageVerseParser {
    public enum Segment: Equatable, Sendable {
        case heading(String)
        case verse(number: Int, text: String)
    }

    private static let verseMarker = try! NSRegularExpression(pattern: #"(\[\d+\]|\d+\.)"#)

    public static func segments(blocks: [BiblePassageBlock]) -> [Segment] {
        var out: [Segment] = []
        for block in blocks {
            switch block {
            case .sectionHeading(let text):
                out.append(.heading(text.trimmingCharacters(in: .whitespacesAndNewlines)))
            case .paragraph(let text):
                out.append(contentsOf: versesInParagraph(text))
            }
        }
        return out
    }

    private static func versesInParagraph(_ paragraph: String) -> [Segment] {
        let trimmed = paragraph.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return [] }

        let ns = trimmed as NSString
        let fullRange = NSRange(location: 0, length: ns.length)
        let matches = verseMarker.matches(in: trimmed, range: fullRange)
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
            let body = chunk.hasPrefix(marker)
                ? String(chunk.dropFirst(marker.count)).trimmingCharacters(in: .whitespacesAndNewlines)
                : chunk
            if number > 0, !body.isEmpty {
                verses.append(.verse(number: number, text: body))
            }
        }
        return verses
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
