import Foundation

public enum GoalType: String, Codable, Sendable, Equatable {
    case bibleChapter = "BIBLE_CHAPTER"
    case timedFocus = "TIMED_FOCUS"
    case stepCount = "STEP_COUNT"
}
