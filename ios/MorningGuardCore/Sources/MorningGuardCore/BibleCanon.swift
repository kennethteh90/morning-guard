import Foundation

public struct BibleBook: Sendable, Equatable {
    public var name: String
    public var chapterCount: Int
    public init(name: String, chapterCount: Int) { self.name = name; self.chapterCount = chapterCount }
}

public enum BibleCanon {
    public static let books: [BibleBook] = [
        BibleBook(name: "Genesis", chapterCount: 50),
        BibleBook(name: "Exodus", chapterCount: 40),
        BibleBook(name: "Leviticus", chapterCount: 27),
        BibleBook(name: "Numbers", chapterCount: 36),
        BibleBook(name: "Deuteronomy", chapterCount: 34),
        BibleBook(name: "Joshua", chapterCount: 24),
        BibleBook(name: "Judges", chapterCount: 21),
        BibleBook(name: "Ruth", chapterCount: 4),
        BibleBook(name: "1 Samuel", chapterCount: 31),
        BibleBook(name: "2 Samuel", chapterCount: 24),
        BibleBook(name: "1 Kings", chapterCount: 22),
        BibleBook(name: "2 Kings", chapterCount: 25),
        BibleBook(name: "1 Chronicles", chapterCount: 29),
        BibleBook(name: "2 Chronicles", chapterCount: 36),
        BibleBook(name: "Ezra", chapterCount: 10),
        BibleBook(name: "Nehemiah", chapterCount: 13),
        BibleBook(name: "Esther", chapterCount: 10),
        BibleBook(name: "Job", chapterCount: 42),
        BibleBook(name: "Psalms", chapterCount: 150),
        BibleBook(name: "Proverbs", chapterCount: 31),
        BibleBook(name: "Ecclesiastes", chapterCount: 12),
        BibleBook(name: "Song of Solomon", chapterCount: 8),
        BibleBook(name: "Isaiah", chapterCount: 66),
        BibleBook(name: "Jeremiah", chapterCount: 52),
        BibleBook(name: "Lamentations", chapterCount: 5),
        BibleBook(name: "Ezekiel", chapterCount: 48),
        BibleBook(name: "Daniel", chapterCount: 12),
        BibleBook(name: "Hosea", chapterCount: 14),
        BibleBook(name: "Joel", chapterCount: 3),
        BibleBook(name: "Amos", chapterCount: 9),
        BibleBook(name: "Obadiah", chapterCount: 1),
        BibleBook(name: "Jonah", chapterCount: 4),
        BibleBook(name: "Micah", chapterCount: 7),
        BibleBook(name: "Nahum", chapterCount: 3),
        BibleBook(name: "Habakkuk", chapterCount: 3),
        BibleBook(name: "Zephaniah", chapterCount: 3),
        BibleBook(name: "Haggai", chapterCount: 2),
        BibleBook(name: "Zechariah", chapterCount: 14),
        BibleBook(name: "Malachi", chapterCount: 4),
        BibleBook(name: "Matthew", chapterCount: 28),
        BibleBook(name: "Mark", chapterCount: 16),
        BibleBook(name: "Luke", chapterCount: 24),
        BibleBook(name: "John", chapterCount: 21),
        BibleBook(name: "Acts", chapterCount: 28),
        BibleBook(name: "Romans", chapterCount: 16),
        BibleBook(name: "1 Corinthians", chapterCount: 16),
        BibleBook(name: "2 Corinthians", chapterCount: 13),
        BibleBook(name: "Galatians", chapterCount: 6),
        BibleBook(name: "Ephesians", chapterCount: 6),
        BibleBook(name: "Philippians", chapterCount: 4),
        BibleBook(name: "Colossians", chapterCount: 4),
        BibleBook(name: "1 Thessalonians", chapterCount: 5),
        BibleBook(name: "2 Thessalonians", chapterCount: 3),
        BibleBook(name: "1 Timothy", chapterCount: 6),
        BibleBook(name: "2 Timothy", chapterCount: 4),
        BibleBook(name: "Titus", chapterCount: 3),
        BibleBook(name: "Philemon", chapterCount: 1),
        BibleBook(name: "Hebrews", chapterCount: 13),
        BibleBook(name: "James", chapterCount: 5),
        BibleBook(name: "1 Peter", chapterCount: 5),
        BibleBook(name: "2 Peter", chapterCount: 3),
        BibleBook(name: "1 John", chapterCount: 5),
        BibleBook(name: "2 John", chapterCount: 1),
        BibleBook(name: "3 John", chapterCount: 1),
        BibleBook(name: "Jude", chapterCount: 1),
        BibleBook(name: "Revelation", chapterCount: 22),
    ]

    public static func bookIndex(bookName: String) -> Int {
        books.firstIndex { $0.name.caseInsensitiveCompare(bookName) == .orderedSame } ?? -1
    }

    public static func chapterCount(bookName: String) -> Int {
        let idx = bookIndex(bookName: bookName)
        guard idx >= 0 else { return 1 }
        return books[idx].chapterCount
    }

    public static func normalizeBook(bookName: String) -> String {
        let idx = bookIndex(bookName: bookName)
        return idx >= 0 ? books[idx].name : books[0].name
    }

    public static func advance(bookName: String, chapter: Int) -> (String, Int) {
        let index = bookIndex(bookName: bookName) >= 0 ? bookIndex(bookName: bookName) : bookIndex(bookName: "John")
        let book = books[index]
        if chapter < book.chapterCount { return (book.name, chapter + 1) }
        let nextIndex = index + 1
        if nextIndex < books.count { return (books[nextIndex].name, 1) }
        return (book.name, chapter)
    }

    public static func previous(bookName: String, chapter: Int) -> (String, Int) {
        let index = bookIndex(bookName: bookName) >= 0 ? bookIndex(bookName: bookName) : bookIndex(bookName: "John")
        if chapter > 1 { return (books[index].name, chapter - 1) }
        let prevIndex = index - 1
        if prevIndex >= 0 {
            let prev = books[prevIndex]
            return (prev.name, prev.chapterCount)
        }
        return (books[index].name, 1)
    }
}
