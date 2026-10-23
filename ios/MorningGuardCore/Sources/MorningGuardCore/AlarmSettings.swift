import Foundation

public struct AlarmSettings: Codable, Sendable, Equatable {
    public var enabled: Bool
    public var hour: Int
    public var minute: Int

    public init(enabled: Bool = false, hour: Int = 6, minute: Int = 30) {
        self.enabled = enabled
        self.hour = hour
        self.minute = minute
    }
}
