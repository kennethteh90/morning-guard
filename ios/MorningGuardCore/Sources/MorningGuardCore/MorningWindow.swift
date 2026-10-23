import Foundation

public struct MorningWindow: Codable, Sendable, Equatable {
    public var startHour: Int
    public var startMinute: Int
    public var endHour: Int
    public var endMinute: Int

    public init(
        startHour: Int = 1,
        startMinute: Int = 0,
        endHour: Int = 10,
        endMinute: Int = 0
    ) {
        self.startHour = startHour
        self.startMinute = startMinute
        self.endHour = endHour
        self.endMinute = endMinute
    }
}
