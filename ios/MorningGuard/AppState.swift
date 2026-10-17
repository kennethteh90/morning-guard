import Foundation
import MorningGuardCore
import Observation

@MainActor
@Observable
final class AppState {
    let settingsStore: SettingsStore
    let gateService: GateService
    let shieldController: ShieldController
    let deviceActivityScheduler: DeviceActivityScheduler
    let passphraseStore: PassphraseStore
    let bibleClient: BibleClient
    let healthKitStepReader: HealthKitStepReader
    let alarmScheduler: MorningAlarmScheduler

    private let emergencyPolicy = EmergencyBypassPolicy()
    private var gateRefreshTask: Task<Void, Never>?

    var deepLinkGoalPending = false

    init() {
        let settings = SettingsStore()
        let gate = GateService(settingsStore: settings)
        self.settingsStore = settings
        self.gateService = gate
        self.shieldController = ShieldController(settingsStore: settings, gateService: gate)
        self.deviceActivityScheduler = DeviceActivityScheduler()
        self.passphraseStore = PassphraseStore()
        self.bibleClient = BibleClient()
        self.healthKitStepReader = HealthKitStepReader()
        self.alarmScheduler = MorningAlarmScheduler()

        settings.syncEmergencyHashFromKeychain(passphraseStore.storedHash())
        gate.refresh()
        shieldController.refreshShields()
        deviceActivityScheduler.reschedule(for: settings.userSettings.morningWindow)
        Task { await alarmScheduler.apply(alarm: settings.userSettings.alarm) }
    }

    func onAppear() {
        refreshGateAndShields()
    }

    func onSceneActive() {
        refreshGateAndShields()
    }

    func refreshGateAndShields() {
        gateService.refresh()
        shieldController.refreshAuthorizationStatus()
        shieldController.refreshShields()
    }

    func completeOnboarding() {
        settingsStore.updateUserSettings { $0.withOnboardingCompleted(true) }
        deviceActivityScheduler.reschedule(for: settingsStore.userSettings.morningWindow)
        shieldController.persistSelection()
        refreshGateAndShields()
        Task { await alarmScheduler.apply(alarm: settingsStore.userSettings.alarm) }
    }

    func updateMorningWindow(_ window: MorningWindow) {
        settingsStore.updateUserSettings { $0.withMorningWindow(window) }
        deviceActivityScheduler.reschedule(for: window)
        refreshGateAndShields()
    }

    func updateAlarm(_ alarm: AlarmSettings) {
        settingsStore.updateUserSettings { $0.withAlarm(alarm) }
        Task { await alarmScheduler.apply(alarm: alarm) }
    }

    func setEmergencyPassphrase(_ passphrase: String) throws {
        try passphraseStore.setPassphrase(passphrase, policy: emergencyPolicy)
        let hash = passphraseStore.storedHash()
        settingsStore.setEmergencyPassphraseHash(hash)
    }

    func attemptEmergencyBypass(passphrase: String) throws -> Int64 {
        let store = settingsStore.goalProgressStore
        if emergencyPolicy.isLocked(lockedUntilEpochMs: store.lockedUntilEpochMs) {
            throw PassphraseStoreError.locked
        }
        guard let stored = passphraseStore.storedHash() ?? store.emergencyPassphraseHash else {
            throw PassphraseStoreError.notConfigured
        }
        guard PassphraseStore.isModernFormat(stored) else {
            throw PassphraseStoreError.notConfigured
        }
        guard passphraseStore.verify(passphrase, stored: stored) else {
            let (attempts, lockedUntil) = emergencyPolicy.recordFailure(
                failedAttempts: store.failedAttempts,
                lockedUntilEpochMs: store.lockedUntilEpochMs
            )
            settingsStore.updateGoalProgress {
                var copy = $0
                copy.failedAttempts = attempts
                copy.lockedUntilEpochMs = lockedUntil
                return copy
            }
            throw PassphraseStoreError.invalidPassphrase
        }
        let expires = emergencyPolicy.bypassExpiresAt()
        let cleared = emergencyPolicy.clearedLockoutAfterSuccess()
        settingsStore.updateGoalProgress {
            var copy = $0
            copy.emergencyBypassUntilEpochMs = expires
            copy.failedAttempts = cleared.failedAttempts
            copy.lockedUntilEpochMs = cleared.lockedUntilEpochMs
            return copy
        }
        gateService.activateEmergencyBypass(expiresAtMs: expires)
        shieldController.refreshShields()
        return expires
    }

    func handleDeepLink(_ url: URL) {
        guard url.scheme?.lowercased() == "morningguard" else { return }
        if url.host?.lowercased() == "goal" || url.path == "/goal" {
            deepLinkGoalPending = true
        }
    }

    func startPeriodicGateRefresh() {
        gateRefreshTask?.cancel()
        gateRefreshTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(30))
                await self?.refreshGateAndShields()
            }
        }
    }

    func stopPeriodicGateRefresh() {
        gateRefreshTask?.cancel()
        gateRefreshTask = nil
    }
}

private extension UserSettings {
    func withOnboardingCompleted(_ completed: Bool) -> UserSettings {
        var copy = self
        copy.onboardingCompleted = completed
        return copy
    }

    func withMorningWindow(_ window: MorningWindow) -> UserSettings {
        var copy = self
        copy.morningWindow = window
        return copy
    }

    func withAlarm(_ alarm: AlarmSettings) -> UserSettings {
        var copy = self
        copy.alarm = alarm
        return copy
    }
}
