import Foundation
import MorningGuardCore
import Observation

@MainActor
@Observable
final class RecoveryPasswordCoordinator {
    private let settingsStore: SettingsStore
    private let auth: SupabaseAuthService
    private let noteEncryption: NoteEncryptionService
    private let highlightStore: BibleHighlightStore
    private let syncService: UserDataSyncService

    var sheetRequest = false
    var periodicRequest = false
    var statusMessage: String?
    var errorMessage: String?

    init(
        settingsStore: SettingsStore,
        auth: SupabaseAuthService,
        noteEncryption: NoteEncryptionService,
        highlightStore: BibleHighlightStore,
        syncService: UserDataSyncService
    ) {
        self.settingsStore = settingsStore
        self.auth = auth
        self.noteEncryption = noteEncryption
        self.highlightStore = highlightStore
        self.syncService = syncService
    }

    var showBlockingCloudRecovery: Bool {
        auth.isSignedIn
            && settingsStore.userSettings.dataStorageMode == .cloudSync
            && noteEncryption.requiresCloudRecovery()
    }

    var showDedicatedSheet: Bool {
        let settings = settingsStore.userSettings
        let signedIn = auth.isSignedIn
        let hasRecovery = noteEncryption.cryptoState.hasRecoveryPassword()
        let cloudRecovery = noteEncryption.requiresCloudRecovery()
        let dedicated = sheetRequest || periodicRequest
            || RecoveryPasswordPromptPolicy.shouldShowDedicatedSheet(
                settings: settings,
                hasRecoveryPassword: hasRecovery,
                signedIn: signedIn,
                cloudRecoveryRequired: cloudRecovery
            )
            || RecoveryPasswordPromptPolicy.shouldShowPeriodicSheet(
                settings: settings,
                hasRecoveryPassword: hasRecovery,
                signedIn: signedIn
            )
        return dedicated && !cloudRecovery
    }

    var showBanner: Bool {
        RecoveryPasswordPromptPolicy.shouldShowBanner(
            settings: settingsStore.userSettings,
            hasRecoveryPassword: noteEncryption.cryptoState.hasRecoveryPassword(),
            signedIn: auth.isSignedIn
        )
    }

    func onSignedIn() {
        try? noteEncryption.ensureDekReady()
        sheetRequest = true
    }

    func onNoteSaved() {
        settingsStore.incrementNoteSaveCountForRecoveryPrompt()
        if RecoveryPasswordPromptPolicy.shouldShowPeriodicSheet(
            settings: settingsStore.userSettings,
            hasRecoveryPassword: noteEncryption.cryptoState.hasRecoveryPassword(),
            signedIn: auth.isSignedIn
        ) {
            periodicRequest = true
        }
    }

    func dismissLater() {
        settingsStore.recordRecoveryPasswordPromptDismissed()
        sheetRequest = false
        periodicRequest = false
    }

    func markPromptShown() {
        settingsStore.markRecoveryPasswordPromptShownAfterSignIn()
    }

    func setRecoveryPassword(password: String, confirmation: String) {
        if let validation = RecoveryPasswordRules.validate(password: password) {
            errorMessage = validation
            return
        }
        if password != confirmation {
            errorMessage = "Passwords do not match."
            return
        }
        do {
            try noteEncryption.setRecoveryPassword(password)
            highlightStore.reencryptAllStoredNotes()
            Task { await syncService.syncAllIfSignedIn() }
            sheetRequest = false
            periodicRequest = false
            statusMessage = "Recovery password saved. Store it in a password manager."
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func unlockCloudRecovery(password: String) {
        do {
            try noteEncryption.unlockWithPassword(password)
            highlightStore.reencryptAllStoredNotes()
            Task { await syncService.syncAllIfSignedIn() }
            statusMessage = "Notes unlocked on this device."
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func startFreshWithoutCloudNotes() {
        do {
            try noteEncryption.abandonPendingCloudRecovery()
            Task { await syncService.syncAllIfSignedIn() }
            statusMessage =
                "This device will use its own encryption. Older cloud notes stay locked unless you recover the password later."
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func clearMessages() {
        statusMessage = nil
        errorMessage = nil
    }
}
