import Foundation
import MorningGuardCore
import Observation

struct GoalProgressStore: Codable, Equatable, Sendable {
    var progress: MorningGoalProgress?
    var emergencyBypassUntilEpochMs: Int64?
    var emergencyPassphraseHash: String?
    var failedAttempts: Int
    var lockedUntilEpochMs: Int64?

    init(
        progress: MorningGoalProgress? = nil,
        emergencyBypassUntilEpochMs: Int64? = nil,
        emergencyPassphraseHash: String? = nil,
        failedAttempts: Int = 0,
        lockedUntilEpochMs: Int64? = nil
    ) {
        self.progress = progress
        self.emergencyBypassUntilEpochMs = emergencyBypassUntilEpochMs
        self.emergencyPassphraseHash = emergencyPassphraseHash
        self.failedAttempts = failedAttempts
        self.lockedUntilEpochMs = lockedUntilEpochMs
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
        mirrorToAppGroup()
    }

    func updateUserSettings(_ transform: (UserSettings) -> UserSettings) {
        userSettings = transform(userSettings)
    }

    func updateGoalProgress(_ transform: (GoalProgressStore) -> GoalProgressStore) {
        goalProgressStore = transform(goalProgressStore)
    }

    func setEmergencyPassphraseHash(_ hash: String?) {
        updateGoalProgress { store in
            var copy = store
            copy.emergencyPassphraseHash = hash
            copy.failedAttempts = 0
            copy.lockedUntilEpochMs = nil
            return copy
        }
        if let hash {
            sharedDefaults?.set(hash, forKey: AppGroupKeys.emergencyPassphraseHash)
        } else {
            sharedDefaults?.removeObject(forKey: AppGroupKeys.emergencyPassphraseHash)
        }
    }

    func syncEmergencyHashFromKeychain(_ hash: String?) {
        if goalProgressStore.emergencyPassphraseHash != hash {
            updateGoalProgress { $0.withEmergencyHash(hash) }
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
        if let hash = goalProgressStore.emergencyPassphraseHash {
            sharedDefaults?.set(hash, forKey: AppGroupKeys.emergencyPassphraseHash)
        }
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

private extension GoalProgressStore {
    func withEmergencyHash(_ hash: String?) -> GoalProgressStore {
        var copy = self
        copy.emergencyPassphraseHash = hash
        return copy
    }
}
