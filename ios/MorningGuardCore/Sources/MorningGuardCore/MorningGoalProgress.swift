import Foundation

public struct MorningGoalProgress: Codable, Sendable, Equatable {
    public var dateKey: String
    public var config: MorningGoalConfig
    public var startedAtEpochMs: Int64?
    public var completedAtEpochMs: Int64?
    public var stepsAtStart: Int?
    /// Steps counted toward today's goal while the goal screen is open.
    public var stepsAccumulated: Int
    /// Seconds with the goal screen in the foreground (Bible chapter and timed focus).
    public var onScreenSeconds: Int

    public var isComplete: Bool {
        completedAtEpochMs != nil
    }

    public init(
        dateKey: String,
        config: MorningGoalConfig,
        startedAtEpochMs: Int64? = nil,
        completedAtEpochMs: Int64? = nil,
        stepsAtStart: Int? = nil,
        stepsAccumulated: Int = 0,
        onScreenSeconds: Int = 0
    ) {
        self.dateKey = dateKey
        self.config = config
        self.startedAtEpochMs = startedAtEpochMs
        self.completedAtEpochMs = completedAtEpochMs
        self.stepsAtStart = stepsAtStart
        self.stepsAccumulated = stepsAccumulated
        self.onScreenSeconds = onScreenSeconds
    }
}
