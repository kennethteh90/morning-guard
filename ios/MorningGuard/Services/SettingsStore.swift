import Foundation
import MorningGuardCore
import Observation

struct GoalProgressStore: Codable, Equatable, Sendable {
    var progress: MorningGoalProgress?
    var emergencyBypassUntilEpochMs: Int64?
    var peaceSessionUntilEpochMs: Int64?
    var morningHistory: [MorningDayRecord]
    var goalTimeSecondsByDate: [String: Int]
    var bibleReadingSecondsByDate: [String: Int]
    var goalStepsByDate: [String: Int]

    init(
        progress: MorningGoalProgress? = nil,
        emergencyBypassUntilEpochMs: Int64? = nil,
        peaceSessionUntilEpochMs: Int64? = nil,
        morningHistory: [MorningDayRecord] = [],
        goalTimeSecondsByDate: [String: Int] = [:],
        bibleReadingSecondsByDate: [String: Int] = [:],
        goalStepsByDate: [String: Int] = [:]
    ) {
        self.progress = progress
        self.emergencyBypassUntilEpochMs = emergencyBypassUntilEpochMs
        self.peaceSessionUntilEpochMs = peaceSessionUntilEpochMs
        self.morningHistory = morningHistory
        self.goalTimeSecondsByDate = goalTimeSecondsByDate
        self.bibleReadingSecondsByDate = bibleReadingSecondsByDate
        self.goalStepsByDate = goalStepsByDate
    }

    enum CodingKeys: String, CodingKey {
        case progress, emergencyBypassUntilEpochMs, peaceSessionUntilEpochMs
        case emergencyPassphraseHash
        case failedAttempts, lockedUntilEpochMs
        case morningHistory, goalTimeSecondsByDate, bibleReadingSecondsByDate, goalStepsByDate
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        progress = try container.decodeIfPresent(MorningGoalProgress.self, forKey: .progress)
        emergencyBypassUntilEpochMs = try container.decodeIfPresent(Int64.self, forKey: .emergencyBypassUntilEpochMs)
        peaceSessionUntilEpochMs = try container.decodeIfPresent(Int64.self, forKey: .peaceSessionUntilEpochMs)
        _ = try container.decodeIfPresent(String.self, forKey: .emergencyPassphraseHash)
        _ = try container.decodeIfPresent(Int.self, forKey: .failedAttempts)
        _ = try container.decodeIfPresent(Int64.self, forKey: .lockedUntilEpochMs)
        morningHistory = try container.decodeIfPresent([MorningDayRecord].self, forKey: .morningHistory) ?? []
        goalTimeSecondsByDate = try container.decodeIfPresent([String: Int].self, forKey: .goalTimeSecondsByDate) ?? [:]
        bibleReadingSecondsByDate = try container.decodeIfPresent([String: Int].self, forKey: .bibleReadingSecondsByDate) ?? [:]
        goalStepsByDate = try container.decodeIfPresent([String: Int].self, forKey: .goalStepsByDate) ?? [:]
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(progress, forKey: .progress)
        try container.encodeIfPresent(emergencyBypassUntilEpochMs, forKey: .emergencyBypassUntilEpochMs)
        try container.encodeIfPresent(peaceSessionUntilEpochMs, forKey: .peaceSessionUntilEpochMs)
        try container.encode(morningHistory, forKey: .morningHistory)
        try container.encode(goalTimeSecondsByDate, forKey: .goalTimeSecondsByDate)
        try container.encode(bibleReadingSecondsByDate, forKey: .bibleReadingSecondsByDate)
        try container.encode(goalStepsByDate, forKey: .goalStepsByDate)
    }
}

@MainActor
@Observable
final class SettingsStore {
    private let defaults: UserDefaults
    private let sharedDefaults: UserDefaults?
    private let lockoutStore = EmergencyLockoutStore()
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private enum LocalKey {
        static let userSettings = "userSettings"
        static let goalProgress = "goalProgress"
    }

    var userSettings: UserSettings {
        didSet { persistUserSettings() }
    }

    var goalProgressStore: GoalProgressStore {
        didSet { persistGoalProgress() }
    }

    init(appGroupIdentifier: String = AppGroupKeys.suiteName) {
        defaults = .standard
        sharedDefaults = UserDefaults(suiteName: appGroupIdentifier)
        userSettings = Self.loadUserSettings(from: defaults, shared: sharedDefaults)
        Self.migrateLegacyLockoutFromGoalProgressJSON(
            lockoutStore: lockoutStore,
            defaults: defaults,
            shared: sharedDefaults
        )
        goalProgressStore = Self.loadGoalProgress(from: defaults, shared: sharedDefaults)
        clearLegacyPassphraseHashFromSharedStorage()
        mirrorToAppGroup()
    }

    func emergencyLockoutState() -> EmergencyLockoutState {
        lockoutStore.load()
    }

    func setEmergencyLockoutState(_ state: EmergencyLockoutState) {
        try? lockoutStore.save(state)
    }

    func updateUserSettings(_ transform: (UserSettings) -> UserSettings) {
        userSettings = transform(userSettings)
    }

    func updateGoalProgress(_ transform: (GoalProgressStore) -> GoalProgressStore) {
        goalProgressStore = transform(goalProgressStore)
    }

    func setBoundUserId(_ userId: String) {
        updateUserSettings { settings in
            var copy = settings
            copy.syncBoundUserId = userId
            return copy
        }
    }

    func clearMorningHistory() {
        updateGoalProgress { store in
            var copy = store
            copy.morningHistory = []
            copy.goalTimeSecondsByDate = [:]
            copy.bibleReadingSecondsByDate = [:]
            copy.goalStepsByDate = [:]
            return copy
        }
    }

    func mergeMorningHistoryFromRemote(_ remoteRecords: [MorningDayRecord]) {
        guard !remoteRecords.isEmpty else { return }
        updateGoalProgress { store in
            var history = store.morningHistory
            var times = store.goalTimeSecondsByDate
            var steps = store.goalStepsByDate
            for record in remoteRecords {
                history = MorningHistoryMerge.mergeMorningRecord(
                    history: history,
                    dateKey: record.dateKey,
                    outcome: record.outcome,
                    recordedAtEpochMs: record.recordedAtEpochMs,
                    timeOnGoalSeconds: record.timeOnGoalSeconds,
                    stepsOnGoal: record.stepsOnGoal
                )
                times = MorningHistoryMerge.mergeGoalTime(
                    map: times,
                    dateKey: record.dateKey,
                    seconds: record.timeOnGoalSeconds
                )
                steps = MorningHistoryMerge.mergeGoalSteps(
                    map: steps,
                    dateKey: record.dateKey,
                    steps: record.stepsOnGoal
                )
            }
            var copy = store
            copy.morningHistory = history
            copy.goalTimeSecondsByDate = times
            copy.goalStepsByDate = steps
            return copy
        }
    }

    func clearSensitiveGoalData() {
        try? lockoutStore.clear()
        updateGoalProgress { _ in GoalProgressStore() }
    }

    func clearSyncBoundUserId() {
        updateUserSettings { settings in
            var copy = settings
            copy.syncBoundUserId = nil
            return copy
        }
    }

    func markRecoveryPasswordPromptShownAfterSignIn() {
        updateUserSettings { $0.withRecoveryPromptShown() }
    }

    func recordRecoveryPasswordPromptDismissed() {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        updateUserSettings { settings in
            var copy = settings
            copy.recoveryPasswordPromptShownAfterSignIn = true
            copy.recoveryPasswordLaterCount = settings.recoveryPasswordLaterCount + 1
            copy.recoveryPasswordPromptLastDismissedEpochMs = nowMs
            return copy
        }
    }

    func incrementNoteSaveCountForRecoveryPrompt() {
        updateUserSettings { settings in
            var copy = settings
            copy.noteSaveCountForRecoveryPrompt = settings.noteSaveCountForRecoveryPrompt + 1
            return copy
        }
    }

    func clearLegacyPassphraseHashFromSharedStorage() {
        sharedDefaults?.removeObject(forKey: AppGroupKeys.emergencyPassphraseHash)
        if var data = defaults.data(forKey: LocalKey.goalProgress),
           var json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            json.removeValue(forKey: "emergencyPassphraseHash")
            if let rewritten = try? JSONSerialization.data(withJSONObject: json) {
                defaults.set(rewritten, forKey: LocalKey.goalProgress)
            }
        }
    }

    private func persistUserSettings() {
        guard let data = try? encoder.encode(userSettings) else { return }
        defaults.set(data, forKey: LocalKey.userSettings)
        sharedDefaults?.set(data, forKey: AppGroupKeys.userSettingsJSON)
        mirrorMorningWindow()
    }

    private func persistGoalProgress() {
        guard let data = try? encoder.encode(goalProgressStore) else { return }
        defaults.set(data, forKey: LocalKey.goalProgress)
        sharedDefaults?.set(data, forKey: AppGroupKeys.goalProgressJSON)
    }

    func mirrorGateEvaluation(_ evaluation: GateEvaluation) {
        guard let data = try? encoder.encode(evaluation) else { return }
        sharedDefaults?.set(data, forKey: AppGroupKeys.gateEvaluationJSON)
    }

    func mirrorShieldsActive(_ active: Bool) {
        sharedDefaults?.set(active, forKey: AppGroupKeys.shieldsActive)
    }

    func mirrorFamilyControlsAuthorized(_ authorized: Bool) {
        sharedDefaults?.set(authorized, forKey: AppGroupKeys.familyControlsAuthorized)
    }

    private func mirrorMorningWindow() {
        guard let data = try? encoder.encode(userSettings.morningWindow) else { return }
        sharedDefaults?.set(data, forKey: AppGroupKeys.morningWindowJSON)
    }

    private func mirrorToAppGroup() {
        persistUserSettings()
        persistGoalProgress()
    }

    private static func loadUserSettings(from defaults: UserDefaults, shared: UserDefaults?) -> UserSettings {
        if let data = defaults.data(forKey: LocalKey.userSettings),
           let decoded = try? JSONDecoder().decode(UserSettings.self, from: data) {
            return decoded
        }
        if let data = shared?.data(forKey: AppGroupKeys.userSettingsJSON),
           let decoded = try? JSONDecoder().decode(UserSettings.self, from: data) {
            return decoded
        }
        return UserSettings()
    }

    private static func migrateLegacyLockoutFromGoalProgressJSON(
        lockoutStore: EmergencyLockoutStore,
        defaults: UserDefaults,
        shared: UserDefaults?
    ) {
        guard lockoutStore.load() == .empty else { return }
        migrateLegacyLockout(lockoutStore: lockoutStore, in: defaults, key: LocalKey.goalProgress)
        if let shared {
            migrateLegacyLockout(lockoutStore: lockoutStore, in: shared, key: AppGroupKeys.goalProgressJSON)
        }
    }

    private static func migrateLegacyLockout(
        lockoutStore: EmergencyLockoutStore,
        in storage: UserDefaults,
        key: String
    ) {
        guard let data = storage.data(forKey: key),
              var json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        let attempts = json["failedAttempts"] as? Int ?? 0
        let locked: Int64? = {
            if let value = json["lockedUntilEpochMs"] as? Int64 { return value }
            if let value = json["lockedUntilEpochMs"] as? Int { return Int64(value) }
            if let value = json["lockedUntilEpochMs"] as? Double { return Int64(value) }
            return nil
        }()
        guard attempts > 0 || locked != nil else { return }
        try? lockoutStore.save(
            EmergencyLockoutState(failedAttempts: attempts, lockedUntilEpochMs: locked)
        )
        json.removeValue(forKey: "failedAttempts")
        json.removeValue(forKey: "lockedUntilEpochMs")
        if let rewritten = try? JSONSerialization.data(withJSONObject: json) {
            storage.set(rewritten, forKey: key)
        }
    }

    private static func loadGoalProgress(from defaults: UserDefaults, shared: UserDefaults?) -> GoalProgressStore {
        if let data = defaults.data(forKey: LocalKey.goalProgress),
           let decoded = try? JSONDecoder().decode(GoalProgressStore.self, from: data) {
            return decoded
        }
        if let data = shared?.data(forKey: AppGroupKeys.goalProgressJSON),
           let decoded = try? JSONDecoder().decode(GoalProgressStore.self, from: data) {
            return decoded
        }
        return GoalProgressStore()
    }
}

private extension UserSettings {
    func withRecoveryPromptShown() -> UserSettings {
        var copy = self
        copy.recoveryPasswordPromptShownAfterSignIn = true
        return copy
    }
}
