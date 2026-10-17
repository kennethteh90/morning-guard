import Foundation

public struct UserSettings: Codable, Sendable, Equatable {
    public var morningWindow: MorningWindow
    public var alarm: AlarmSettings
    public var bypassPackageNames: Set<String>
    public var morningGoal: MorningGoalConfig
    public var digestOnlyDuringMorningGate: Bool
    public var onboardingCompleted: Bool
    /// Last Supabase user id that owned local sync data on this device.
    public var syncBoundUserId: String?

    public init(
        morningWindow: MorningWindow = MorningWindow(),
        alarm: AlarmSettings = AlarmSettings(),
        bypassPackageNames: Set<String> = DefaultBypassPackages.packageNames,
        morningGoal: MorningGoalConfig = MorningGoalConfig(type: .bibleChapter),
        digestOnlyDuringMorningGate: Bool = true,
        onboardingCompleted: Bool = false,
        syncBoundUserId: String? = nil
    ) {
        self.morningWindow = morningWindow
        self.alarm = alarm
        self.bypassPackageNames = bypassPackageNames
        self.morningGoal = morningGoal
        self.digestOnlyDuringMorningGate = digestOnlyDuringMorningGate
        self.onboardingCompleted = onboardingCompleted
        self.syncBoundUserId = syncBoundUserId
    }
}
