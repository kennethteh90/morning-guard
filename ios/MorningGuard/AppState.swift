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
    let noteEncryption: NoteEncryptionService
    let bibleHighlightStore: BibleHighlightStore
    let localBackupRepository: LocalBackupRepository
    let healthKitStepReader: HealthKitStepReader
    let alarmScheduler: MorningAlarmScheduler
    let peaceSessionService: PeaceSessionService

    private let emergencyPolicy = EmergencyBypassPolicy()
    private var gateRefreshTask: Task<Void, Never>?

    var deepLinkGoalPending = false

    init() {
        let settings = SettingsStore()
        let gate = GateService(settingsStore: settings)
        self.settingsStore = settings
        self.gateService = gate
        self.shieldController = ShieldController(settingsStore: settings, gateService: gate)
        self.peaceSessionService = PeaceSessionService(settingsStore: settings, gateService: gate)
        self.deviceActivityScheduler = DeviceActivityScheduler()
        self.passphraseStore = PassphraseStore()
        self.bibleClient = BibleClient()
        let noteEncryption = NoteEncryptionService()
        self.noteEncryption = noteEncryption
        self.bibleHighlightStore = BibleHighlightStore(noteEncryption: noteEncryption)
        self.localBackupRepository = LocalBackupRepository(
            settingsStore: settings,
            highlightStore: self.bibleHighlightStore,
            noteEncryption: noteEncryption
        )
        self.healthKitStepReader = HealthKitStepReader()
        self.alarmScheduler = MorningAlarmScheduler()

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
        peaceSessionService.reconcileStoredPeaceSession()
        gateService.refresh()
        shieldController.refreshAuthorizationStatus()
        shieldController.refreshShields()
    }

    func startOrExtendPeaceSession() {
        peaceSessionService.startOrExtendOneHour()
        shieldController.refreshShields()
    }

    func endPeaceSession() {
        peaceSessionService.endPeaceSession()
        shieldController.refreshShields()
    }

    func applyDebugWindowPreset(_ preset: DebugWindowPreset) {
        guard MGDebugFlags.showDebugPanels else { return }
        updateMorningWindow(preset.window())
    }

    func debugResetTodayGoal() {
        guard MGDebugFlags.showDebugPanels else { return }
        settingsStore.updateGoalProgress { store in
            var copy = store
            copy.progress = nil
            copy.peaceSessionUntilEpochMs = nil
            return copy
        }
        refreshGateAndShields()
    }

    func debugResetOnboarding() {
        guard MGDebugFlags.showDebugPanels else { return }
        settingsStore.updateUserSettings { $0.withOnboardingCompleted(false) }
    }

    func debugActivateTestWindow() {
        applyDebugWindowPreset(.includingNow)
    }

    func debugForceCompleteGoal() {
        guard MGDebugFlags.showDebugPanels else { return }
        _ = gateService.startGoalIfNeeded()
        guard let progress = settingsStore.goalProgressStore.progress, !progress.isComplete else { return }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        settingsStore.updateGoalProgress { store in
            var copy = store
            guard var p = copy.progress else { return store }
            p.onScreenSeconds = max(p.onScreenSeconds, 60)
            if settingsStore.userSettings.morningGoal.type == .stepCount {
                p.stepsAccumulated = max(p.stepsAccumulated, settingsStore.userSettings.morningGoal.stepTarget)
            }
            copy.progress = p
            return copy
        }
        gateService.markComplete()
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

    func setBibleTranslation(_ translationId: String) {
        let normalized = BibleTranslations.normalizeId(translationId)
        settingsStore.updateUserSettings { settings in
            var copy = settings
            copy.morningGoal.bibleTranslation = normalized
            return copy
        }
    }

    func setBibleReading(book: String, chapter: Int) {
        let normalizedBook = BibleCanon.normalizeBook(bookName: book)
        let clampedChapter = max(1, min(chapter, BibleCanon.chapterCount(bookName: normalizedBook)))
        settingsStore.updateUserSettings { settings in
            var copy = settings
            copy.morningGoal.bibleBook = normalizedBook
            copy.morningGoal.bibleChapter = clampedChapter
            return copy
        }
    }

    func setBibleBook(_ book: String) {
        let chapter = settingsStore.userSettings.morningGoal.bibleChapter
        setBibleReading(book: book, chapter: chapter)
    }

    func stepBibleChapter(delta: Int) {
        let goal = settingsStore.userSettings.morningGoal
        let next: (String, Int)
        if delta > 0 {
            next = BibleCanon.advance(bookName: goal.bibleBook, chapter: goal.bibleChapter)
        } else {
            next = BibleCanon.previous(bookName: goal.bibleBook, chapter: goal.bibleChapter)
        }
        setBibleReading(book: next.0, chapter: next.1)
    }

    func updateAlarm(_ alarm: AlarmSettings) {
        settingsStore.updateUserSettings { $0.withAlarm(alarm) }
        Task { await alarmScheduler.apply(alarm: alarm) }
    }

    func setEmergencyPassphrase(_ passphrase: String) throws {
        try passphraseStore.setPassphrase(passphrase, policy: emergencyPolicy)
    }

    func attemptEmergencyBypass(passphrase: String) throws -> Int64 {
        let store = settingsStore.goalProgressStore
        if emergencyPolicy.isLocked(lockedUntilEpochMs: store.lockedUntilEpochMs) {
            throw PassphraseStoreError.locked
        }
        guard let stored = passphraseStore.storedHash() else {
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
        let dateKey = MorningGateEvaluator.dateKey(for: Date(), calendar: Calendar.current)
        let trackGoalTime = settingsStore.userSettings.morningGoal.type != .stepCount
        settingsStore.recordMorningOutcome(
            dateKey: dateKey,
            outcome: .emergencyBypass,
            trackGoalTime: trackGoalTime
        )
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
