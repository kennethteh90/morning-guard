import Foundation
import MorningGuardCore
import Observation

struct GoalProgressStore: Codable, Equatable, Sendable {
    var progress: MorningGoalProgress?
    var emergencyBypassUntilEpochMs: Int64?
    var peaceSessionUntilEpochMs: Int64?
    var failedAttempts: Int
    var lockedUntilEpochMs: Int64?
    var morningHistory: [MorningDayRecord]
    var goalTimeSecondsByDate: [String: Int]
    var goalStepsByDate: [String: Int]

    init(
        progress: MorningGoalProgress? = nil,
        emergencyBypassUntilEpochMs: Int64? = nil,
        peaceSessionUntilEpochMs: Int64? = nil,
        failedAttempts: Int = 0,
        lockedUntilEpochMs: Int64? = nil,
        morningHistory: [MorningDayRecord] = [],
        goalTimeSecondsByDate: [String: Int] = [:],
        goalStepsByDate: [String: Int] = [:]
    ) {
        self.progress = progress
        self.emergencyBypassUntilEpochMs = emergencyBypassUntilEpochMs
        self.peaceSessionUntilEpochMs = peaceSessionUntilEpochMs
        self.failedAttempts = failedAttempts
        self.lockedUntilEpochMs = lockedUntilEpochMs
        self.morningHistory = morningHistory
        self.goalTimeSecondsByDate = goalTimeSecondsByDate
        self.goalStepsByDate = goalStepsByDate
    }

    enum CodingKeys: String, CodingKey {
        case progress, emergencyBypassUntilEpochMs, peaceSessionUntilEpochMs
        case emergencyPassphraseHash
        case failedAttempts, lockedUntilEpochMs
        case morningHistory, goalTimeSecondsByDate, goalStepsByDate
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        progress = try container.decodeIfPresent(MorningGoalProgress.self, forKey: .progress)
        emergencyBypassUntilEpochMs = try container.decodeIfPresent(Int64.self, forKey: .emergencyBypassUntilEpochMs)
        peaceSessionUntilEpochMs = try container.decodeIfPresent(Int64.self, forKey: .peaceSessionUntilEpochMs)
        _ = try container.decodeIfPresent(String.self, forKey: .emergencyPassphraseHash)
        failedAttempts = try container.decodeIfPresent(Int.self, forKey: .failedAttempts) ?? 0
        lockedUntilEpochMs = try container.decodeIfPresent(Int64.self, forKey: .lockedUntilEpochMs)
        morningHistory = try container.decodeIfPresent([MorningDayRecord].self, forKey: .morningHistory) ?? []
        goalTimeSecondsByDate = try container.decodeIfPresent([String: Int].self, forKey: .goalTimeSecondsByDate) ?? [:]
        goalStepsByDate = try container.decodeIfPresent([String: Int].self, forKey: .goalStepsByDate) ?? [:]
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(progress, forKey: .progress)
        try container.encodeIfPresent(emergencyBypassUntilEpochMs, forKey: .emergencyBypassUntilEpochMs)
        try container.encodeIfPresent(peaceSessionUntilEpochMs, forKey: .peaceSessionUntilEpochMs)
        try container.encode(failedAttempts, forKey: .failedAttempts)
        try container.encodeIfPresent(lockedUntilEpochMs, forKey: .lockedUntilEpochMs)
        try container.encode(morningHistory, forKey: .morningHistory)
        try container.encode(goalTimeSecondsByDate, forKey: .goalTimeSecondsByDate)
        try container.encode(goalStepsByDate, forKey: .goalStepsByDate)
    }
}

@MainActor
@Observable
final class SettingsStore {
    private let defaults: UserDefaults
    private let sharedDefaults: UserDefaults?
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
        goalProgressStore = Self.loadGoalProgress(from: defaults, shared: sharedDefaults)
        clearLegacyPassphraseHashFromSharedStorage()
        mirrorToAppGroup()
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
