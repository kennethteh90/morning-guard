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
    public var recoveryPasswordPromptShownAfterSignIn: Bool
    public var recoveryPasswordLaterCount: Int
    public var recoveryPasswordPromptLastDismissedEpochMs: Int64
    public var noteSaveCountForRecoveryPrompt: Int

    public init(
        morningWindow: MorningWindow = MorningWindow(),
        alarm: AlarmSettings = AlarmSettings(),
        bypassPackageNames: Set<String> = DefaultBypassPackages.packageNames,
        morningGoal: MorningGoalConfig = MorningGoalConfig(type: .bibleChapter),
        digestOnlyDuringMorningGate: Bool = true,
        onboardingCompleted: Bool = false,
        dataStorageMode: DataStorageMode = .localOnly,
        syncBoundUserId: String? = nil,
        recoveryPasswordPromptShownAfterSignIn: Bool = false,
        recoveryPasswordLaterCount: Int = 0,
        recoveryPasswordPromptLastDismissedEpochMs: Int64 = 0,
        noteSaveCountForRecoveryPrompt: Int = 0
    ) {
        self.morningWindow = morningWindow
        self.alarm = alarm
        self.bypassPackageNames = bypassPackageNames
        self.morningGoal = morningGoal
        self.digestOnlyDuringMorningGate = digestOnlyDuringMorningGate
        self.onboardingCompleted = onboardingCompleted
        self.dataStorageMode = dataStorageMode
        self.syncBoundUserId = syncBoundUserId
        self.recoveryPasswordPromptShownAfterSignIn = recoveryPasswordPromptShownAfterSignIn
        self.recoveryPasswordLaterCount = recoveryPasswordLaterCount
        self.recoveryPasswordPromptLastDismissedEpochMs = recoveryPasswordPromptLastDismissedEpochMs
        self.noteSaveCountForRecoveryPrompt = noteSaveCountForRecoveryPrompt
    }
}

extension UserSettings: Codable {
    private enum CodingKeys: String, CodingKey {
        case morningWindow, alarm, bypassPackageNames, morningGoal
        case digestOnlyDuringMorningGate, onboardingCompleted, dataStorageMode, syncBoundUserId
        case recoveryPasswordPromptShownAfterSignIn, recoveryPasswordLaterCount
        case recoveryPasswordPromptLastDismissedEpochMs, noteSaveCountForRecoveryPrompt
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
        recoveryPasswordPromptShownAfterSignIn = try container.decodeIfPresent(
            Bool.self, forKey: .recoveryPasswordPromptShownAfterSignIn
        ) ?? false
        recoveryPasswordLaterCount = try container.decodeIfPresent(Int.self, forKey: .recoveryPasswordLaterCount) ?? 0
        recoveryPasswordPromptLastDismissedEpochMs = try container.decodeIfPresent(
            Int64.self, forKey: .recoveryPasswordPromptLastDismissedEpochMs
        ) ?? 0
        noteSaveCountForRecoveryPrompt = try container.decodeIfPresent(
            Int.self, forKey: .noteSaveCountForRecoveryPrompt
        ) ?? 0
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
        try container.encode(recoveryPasswordPromptShownAfterSignIn, forKey: .recoveryPasswordPromptShownAfterSignIn)
        try container.encode(recoveryPasswordLaterCount, forKey: .recoveryPasswordLaterCount)
        try container.encode(recoveryPasswordPromptLastDismissedEpochMs, forKey: .recoveryPasswordPromptLastDismissedEpochMs)
        try container.encode(noteSaveCountForRecoveryPrompt, forKey: .noteSaveCountForRecoveryPrompt)
    }
}
