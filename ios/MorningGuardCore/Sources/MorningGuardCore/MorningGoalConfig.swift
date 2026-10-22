import Foundation

public struct MorningGoalConfig: Codable, Sendable, Equatable {
    public var type: GoalType
    public var bibleTranslation: String
    public var bibleBook: String
    public var bibleChapter: Int
    public var focusMinutes: Int
    public var stepTarget: Int

    public init(
        type: GoalType,
        bibleTranslation: String = "web",
        bibleBook: String = "John",
        bibleChapter: Int = 1,
        focusMinutes: Int = 5,
        stepTarget: Int = 500
    ) {
        self.type = type
        self.bibleTranslation = bibleTranslation
        self.bibleBook = bibleBook
        self.bibleChapter = bibleChapter
        self.focusMinutes = focusMinutes
        self.stepTarget = stepTarget
    }
}
