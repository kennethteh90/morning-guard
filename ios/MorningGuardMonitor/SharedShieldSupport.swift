import Foundation
import FamilyControls
import ManagedSettings

enum MonitorAppGroupKeys {
    static let suiteName = "group.com.morningguard.shared"
    static let shieldSelectionData = "shieldSelectionData"
    static let shieldsActive = "shieldsActive"
}

struct SharedShieldState {
    let shieldsActive: Bool
    let hasSelection: Bool
    let selection: FamilyActivitySelection?
}

enum SharedShieldStateReader {
    static func load() -> SharedShieldState {
        let defaults = UserDefaults(suiteName: MonitorAppGroupKeys.suiteName)
        let shieldsActive = defaults?.bool(forKey: MonitorAppGroupKeys.shieldsActive) ?? false
        guard let data = defaults?.data(forKey: MonitorAppGroupKeys.shieldSelectionData),
              let selection = try? PropertyListDecoder().decode(FamilyActivitySelection.self, from: data)
        else {
            return SharedShieldState(shieldsActive: shieldsActive, hasSelection: false, selection: nil)
        }
        let hasSelection = !selection.applicationTokens.isEmpty
            || !selection.categoryTokens.isEmpty
            || !selection.webDomainTokens.isEmpty
        return SharedShieldState(shieldsActive: shieldsActive, hasSelection: hasSelection, selection: selection)
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
