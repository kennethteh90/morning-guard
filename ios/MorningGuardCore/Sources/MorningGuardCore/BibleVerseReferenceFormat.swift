import Foundation

public enum BibleVerseReferenceFormat {
    public static func compact(sortedVerses: [Int]) -> String {
        if sortedVerses.isEmpty { return "" }
        if sortedVerses.count == 1 { return String(sortedVerses[0]) }

        var ranges: [(Int, Int)] = []
        var rangeStart = sortedVerses[0]
        var rangeEnd = rangeStart

        for i in 1..<sortedVerses.count {
            let v = sortedVerses[i]
            if v == rangeEnd + 1 {
                rangeEnd = v
            } else {
                ranges.append((rangeStart, rangeEnd))
                rangeStart = v
                rangeEnd = v
            }
        }
        ranges.append((rangeStart, rangeEnd))

        return ranges.map { start, end in
            start == end ? String(start) : "\(start)-\(end)"
        }.joined(separator: ",")
    }

    public static func chapterLabel(
        book: String,
        chapter: Int,
        verses: [Int],
        translationLabel: String? = nil
    ) -> String {
        let sorted = verses.sorted()
        let base: String
        if sorted.isEmpty {
            base = "\(book) \(chapter)"
        } else {
            base = "\(book) \(chapter):\(compact(sortedVerses: sorted))"
        }
        let version = translationLabel?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return version.isEmpty ? base : "\(base) (\(version))"
    }
}
