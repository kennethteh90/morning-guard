import Foundation

public struct BibleChapterPassage: Equatable, Sendable {
    public var reference: String
    public var blocks: [BiblePassageBlock]

    public init(reference: String, blocks: [BiblePassageBlock]) {
        self.reference = reference
        self.blocks = blocks
    }
}

public enum BiblePassageBlock: Equatable, Sendable {
    case sectionHeading(String)
    case paragraph(String)
}

public enum BiblePassageFormatter {
    private static let verseStart = try! NSRegularExpression(pattern: #"^(\[\d+\]|\d+\.)"#)

    public static func parseChapterBody(body: String, fallbackReference: String) -> BibleChapterPassage {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return BibleChapterPassage(reference: fallbackReference, blocks: [])
        }
        let lines = trimmed.components(separatedBy: .newlines)
        let firstLine = lines.first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let reference = firstLine.isEmpty ? fallbackReference : firstLine
        let contentLines: [String]
        if lines.count > 1, !isVerseLine(firstLine) {
            contentLines = Array(lines.dropFirst())
        } else {
            contentLines = lines
        }
        return BibleChapterPassage(reference: reference, blocks: parseContentLines(lines: contentLines))
    }

    public static func parseContentLines(lines: [String]) -> [BiblePassageBlock] {
        var blocks: [BiblePassageBlock] = []
        var paragraph = ""

        func flushParagraph() {
            let text = paragraph.trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty {
                blocks.append(.paragraph(text))
            }
            paragraph = ""
        }

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                flushParagraph()
                continue
            }
            if !paragraph.isEmpty { paragraph.append("\n") }
            paragraph.append(trimmed)
        }
        flushParagraph()
        return blocks
    }

    private static func isVerseLine(_ line: String) -> Bool {
        let range = NSRange(line.startIndex..<line.endIndex, in: line)
        return verseStart.firstMatch(in: line, range: range) != nil
    }
}
