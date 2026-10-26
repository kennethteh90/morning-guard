import Foundation
import MorningGuardCore
import Observation

#if canImport(FamilyControls)
import FamilyControls
import ManagedSettings
#endif

#if canImport(ManagedSettings)
extension ManagedSettingsStore.Name {
    static let morningGuard = Self(rawValue: "morningGuard")
}
#endif

@MainActor
@Observable
final class ShieldController {
    private let settingsStore: SettingsStore
    private let gateService: GateService
    private let sharedDefaults: UserDefaults?

    #if canImport(FamilyControls)
    @ObservationIgnored private let authorizationCenter = AuthorizationCenter.shared
    @ObservationIgnored private let managedStore = ManagedSettingsStore(named: .morningGuard)
    var selection = FamilyActivitySelection()
    private(set) var authorizationStatus: AuthorizationStatus = .notDetermined
    #else
    private(set) var authorizationStatus: ShieldAuthorizationStatus = .unavailable
    var selectionTokenCount: Int = 0
    #endif

    private(set) var shieldsApplied: Bool = false

    init(settingsStore: SettingsStore, gateService: GateService) {
        self.settingsStore = settingsStore
        self.gateService = gateService
        sharedDefaults = UserDefaults(suiteName: AppGroupKeys.suiteName)
        #if canImport(FamilyControls)
        authorizationStatus = authorizationCenter.authorizationStatus
        loadSelectionFromAppGroup()
        settingsStore.mirrorFamilyControlsAuthorized(isAuthorized)
        #endif
    }

    var isAuthorized: Bool {
        #if canImport(FamilyControls)
        authorizationStatus == .approved
        #else
        false
        #endif
    }

    var hasSelection: Bool {
        #if canImport(FamilyControls)
        !selection.applicationTokens.isEmpty
            || !selection.categoryTokens.isEmpty
            || !selection.webDomainTokens.isEmpty
        #else
        selectionTokenCount > 0
        #endif
    }

    var shieldedAppCount: Int {
        #if canImport(FamilyControls)
        selection.applicationTokens.count
        #else
        selectionTokenCount
        #endif
    }

    func refreshAuthorizationStatus() {
        #if canImport(FamilyControls)
        authorizationStatus = authorizationCenter.authorizationStatus
        #endif
    }

    func requestAuthorization() async throws {
        #if canImport(FamilyControls)
        try await authorizationCenter.requestAuthorization(for: .individual)
        authorizationStatus = authorizationCenter.authorizationStatus
        #endif
    }

    func persistSelection() {
        #if canImport(FamilyControls)
        guard let data = try? PropertyListEncoder().encode(selection) else { return }
        sharedDefaults?.set(data, forKey: AppGroupKeys.shieldSelectionData)
        #endif
    }

    func loadSelectionFromAppGroup() {
        #if canImport(FamilyControls)
        guard let data = sharedDefaults?.data(forKey: AppGroupKeys.shieldSelectionData),
              let decoded = try? PropertyListDecoder().decode(FamilyActivitySelection.self, from: data) else {
            return
        }
        selection = decoded
        #endif
    }

    func refreshShields() {
        let shouldApply = ShieldSessionPolicy.shouldApplyShields(
            gateState: gateService.evaluation.state,
            isAuthorized: isAuthorized,
            hasSelection: hasSelection
        )
        #if canImport(FamilyControls)
        if shouldApply {
            applyShields()
        } else {
            clearShields()
        }
        #else
        shieldsApplied = false
        #endif
        settingsStore.mirrorShieldsActive(shouldApply)
        settingsStore.mirrorFamilyControlsAuthorized(isAuthorized)
    }

    func revokeShieldsImmediately() {
        #if canImport(FamilyControls)
        clearShields()
        #endif
        settingsStore.mirrorShieldsActive(false)
    }

    #if canImport(FamilyControls)
    private func applyShields() {
        managedStore.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        managedStore.shield.applicationCategories = selection.categoryTokens.isEmpty
            ? nil
            : ShieldSettings.ActivityCategoryPolicy.specific(selection.categoryTokens)
        managedStore.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
        shieldsApplied = true
    }

    private func clearShields() {
        managedStore.clearAllSettings()
        shieldsApplied = false
    }
    #endif
}

#if !canImport(FamilyControls)
enum ShieldAuthorizationStatus: Equatable {
    case unavailable
    case notDetermined
    case approved
    case denied
}
#endif
