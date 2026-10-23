import Foundation

public struct UserSettings: Sendable, Equatable {
    public var morningWindow: MorningWindow
    public var alarm: AlarmSettings
    public var bypassPackageNames: Set<String>
    public var morningGoal: MorningGoalConfig
    public var digestOnlyDuringMorningGate: Bool
    public var onboardingCompleted: Bool
    public var dataStorageMode: DataStorageMode
    /// Last Supabase user id that owned local sync data on this device.
    public var syncBoundUserId: String?

    public init(
        morningWindow: MorningWindow = MorningWindow(),
        alarm: AlarmSettings = AlarmSettings(),
        bypassPackageNames: Set<String> = DefaultBypassPackages.packageNames,
        morningGoal: MorningGoalConfig = MorningGoalConfig(type: .bibleChapter),
        digestOnlyDuringMorningGate: Bool = true,
        onboardingCompleted: Bool = false,
        dataStorageMode: DataStorageMode = .localOnly,
        syncBoundUserId: String? = nil
    ) {
        self.morningWindow = morningWindow
        self.alarm = alarm
        self.bypassPackageNames = bypassPackageNames
        self.morningGoal = morningGoal
        self.digestOnlyDuringMorningGate = digestOnlyDuringMorningGate
        self.onboardingCompleted = onboardingCompleted
        self.dataStorageMode = dataStorageMode
        self.syncBoundUserId = syncBoundUserId
    }
}

extension UserSettings: Codable {
    private enum CodingKeys: String, CodingKey {
        case morningWindow, alarm, bypassPackageNames, morningGoal
        case digestOnlyDuringMorningGate, onboardingCompleted, dataStorageMode, syncBoundUserId
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        morningWindow = try container.decode(MorningWindow.self, forKey: .morningWindow)
        alarm = try container.decode(AlarmSettings.self, forKey: .alarm)
        bypassPackageNames = try container.decodeIfPresent(Set<String>.self, forKey: .bypassPackageNames)
            ?? DefaultBypassPackages.packageNames
        morningGoal = try container.decode(MorningGoalConfig.self, forKey: .morningGoal)
        digestOnlyDuringMorningGate = try container.decodeIfPresent(Bool.self, forKey: .digestOnlyDuringMorningGate) ?? true
        onboardingCompleted = try container.decodeIfPresent(Bool.self, forKey: .onboardingCompleted) ?? false
        dataStorageMode = try container.decodeIfPresent(DataStorageMode.self, forKey: .dataStorageMode) ?? .localOnly
        syncBoundUserId = try container.decodeIfPresent(String.self, forKey: .syncBoundUserId)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(morningWindow, forKey: .morningWindow)
        try container.encode(alarm, forKey: .alarm)
        try container.encode(bypassPackageNames, forKey: .bypassPackageNames)
        try container.encode(morningGoal, forKey: .morningGoal)
        try container.encode(digestOnlyDuringMorningGate, forKey: .digestOnlyDuringMorningGate)
        try container.encode(onboardingCompleted, forKey: .onboardingCompleted)
        try container.encode(dataStorageMode, forKey: .dataStorageMode)
        try container.encodeIfPresent(syncBoundUserId, forKey: .syncBoundUserId)
    }
}
