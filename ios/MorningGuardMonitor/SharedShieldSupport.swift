import Foundation
import FamilyControls
import ManagedSettings
import MorningGuardCore

enum MonitorAppGroupKeys {
    static let suiteName = "group.com.morningguard.shared"
    static let shieldSelectionData = "shieldSelectionData"
    static let shieldsActive = "shieldsActive"
    static let userSettingsJSON = "userSettingsJSON"
    static let goalProgressJSON = "goalProgressJSON"
}

struct SharedShieldState {
    let gateState: GateState
    let hasSelection: Bool
    let selection: FamilyActivitySelection?
}

enum SharedShieldStateReader {
    static func load() -> SharedShieldState {
        let defaults = UserDefaults(suiteName: MonitorAppGroupKeys.suiteName)
        let gateState = evaluateGateState(defaults: defaults)
        guard let data = defaults?.data(forKey: MonitorAppGroupKeys.shieldSelectionData),
              let selection = try? PropertyListDecoder().decode(FamilyActivitySelection.self, from: data)
        else {
            return SharedShieldState(gateState: gateState, hasSelection: false, selection: nil)
        }
        let hasSelection = !selection.applicationTokens.isEmpty
            || !selection.categoryTokens.isEmpty
            || !selection.webDomainTokens.isEmpty
        return SharedShieldState(gateState: gateState, hasSelection: hasSelection, selection: selection)
    }

    private static func evaluateGateState(defaults: UserDefaults?) -> GateState {
        let evaluator = MorningGateEvaluator()
        let settings: UserSettings
        if let data = defaults?.data(forKey: MonitorAppGroupKeys.userSettingsJSON),
           let decoded = try? JSONDecoder().decode(UserSettings.self, from: data) {
            settings = decoded
        } else {
            settings = UserSettings()
        }
        let progressStore: GoalProgressSnapshot
        if let data = defaults?.data(forKey: MonitorAppGroupKeys.goalProgressJSON),
           let decoded = try? JSONDecoder().decode(GoalProgressSnapshot.self, from: data) {
            progressStore = decoded
        } else {
            progressStore = GoalProgressSnapshot()
        }
        return evaluator.evaluate(
            window: settings.morningWindow,
            progress: progressStore.progress,
            emergencyBypassActiveUntilEpochMs: progressStore.emergencyBypassUntilEpochMs,
            peaceSessionUntilEpochMs: progressStore.peaceSessionUntilEpochMs
        ).state
    }
}

/// Minimal goal progress fields mirrored for the Device Activity extension (no passphrase hash).
struct GoalProgressSnapshot: Codable {
    var progress: MorningGoalProgress?
    var emergencyBypassUntilEpochMs: Int64?
    var peaceSessionUntilEpochMs: Int64?

    init(
        progress: MorningGoalProgress? = nil,
        emergencyBypassUntilEpochMs: Int64? = nil,
        peaceSessionUntilEpochMs: Int64? = nil
    ) {
        self.progress = progress
        self.emergencyBypassUntilEpochMs = emergencyBypassUntilEpochMs
        self.peaceSessionUntilEpochMs = peaceSessionUntilEpochMs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        progress = try container.decodeIfPresent(MorningGoalProgress.self, forKey: .progress)
        emergencyBypassUntilEpochMs = try container.decodeIfPresent(Int64.self, forKey: .emergencyBypassUntilEpochMs)
        peaceSessionUntilEpochMs = try container.decodeIfPresent(Int64.self, forKey: .peaceSessionUntilEpochMs)
    }

    private enum CodingKeys: String, CodingKey {
        case progress, emergencyBypassUntilEpochMs, peaceSessionUntilEpochMs
    }
}

struct ShieldApplier {
    private let store = ManagedSettingsStore(named: ManagedSettingsStore.Name(rawValue: "morningGuard"))

    func apply(selection: FamilyActivitySelection) {
        store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.applicationCategories = selection.categoryTokens.isEmpty
            ? nil
            : ShieldSettings.ActivityCategoryPolicy.specific(selection.categoryTokens)
        store.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
    }

    func clear() {
        store.clearAllSettings()
    }
}
