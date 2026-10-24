import MorningGuardCore
import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    static var privacyPolicyURL: URL? {
        let raw = (Bundle.main.object(forInfoDictionaryKey: "PRIVACY_POLICY_URL") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return URL(string: raw ?? "https://morning-guard.codingagents.cc/privacy/")
    }

    @Environment(AppState.self) private var appState
    @State private var windowStart = Date()
    @State private var windowEnd = Date()
    @State private var alarmEnabled = false
    @State private var alarmTime = Date()
    @State private var newPassphrase = ""
    @State private var passphraseMessage: String?
    @State private var notesPassword = ""
    @State private var notesConfirmPassword = ""
    @State private var notesNewPassword = ""
    @State private var notesConfirmNewPassword = ""
    @State private var notesMessage: String?
    @State private var magicLinkEmail = ""
    @State private var accountMessage: String?
    @State private var backupMessage: String?
    @State private var backupPasswordMode: LocalBackupPasswordSheet.Mode?
    @State private var showRestoreConfirm = false
    @State private var showImportPicker = false
    @State private var exportDocument: BackupFileDocument?
    @State private var showExporter = false

    var body: some View {
        Form {
            Section("Morning window") {
                DatePicker("Starts", selection: $windowStart, displayedComponents: .hourAndMinute)
                    .onChange(of: windowStart) { _, _ in persistWindow() }
                DatePicker("Ends", selection: $windowEnd, displayedComponents: .hourAndMinute)
                    .onChange(of: windowEnd) { _, _ in persistWindow() }
            }

            Section("Morning alarm") {
                Toggle("Enabled", isOn: $alarmEnabled)
                    .onChange(of: alarmEnabled) { _, _ in persistAlarm() }
                DatePicker("Time", selection: $alarmTime, displayedComponents: .hourAndMinute)
                    .onChange(of: alarmTime) { _, _ in persistAlarm() }
            }

            Section("Goal") {
                Picker("Type", selection: goalTypeBinding) {
                    Text("Bible chapter").tag(GoalType.bibleChapter)
                    Text("Timed focus").tag(GoalType.timedFocus)
                    Text("Steps").tag(GoalType.stepCount)
                }
                if appState.settingsStore.userSettings.morningGoal.type == .bibleChapter {
                    Text("Reading: \(appState.settingsStore.userSettings.morningGoal.bibleBook) \(appState.settingsStore.userSettings.morningGoal.bibleChapter)")
                }
            }

            RecoveryPasswordBannerView()
            notesEncryptionSection
            backupSection
            accountSection

            Section("Emergency passphrase") {
                SecureField("New passphrase (8+ characters)", text: $newPassphrase)
                Button("Save passphrase") { savePassphrase() }
                if let passphraseMessage {
                    Text(passphraseMessage)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Privacy") {
                if let url = SettingsView.privacyPolicyURL {
                    Link("Privacy policy", destination: url)
                }
            }

            Section("On iPhone") {
                Text("Peace break re-enables app shields instead of holding notifications. Use Focus in Settings for notification quiet.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Shields") {
                Text("To fully remove shields, revoke Screen Time access for Morning Guard in Settings → Screen Time, or clear your app selection during onboarding.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if appState.gateService.evaluation.state != .locked {
                    Button("Clear shields now") {
                        appState.shieldController.revokeShieldsImmediately()
                    }
                }
            }

            if MGDebugFlags.showDebugPanels {
                Section {
                    Text("Quick presets for gate, peace break, and shield testing. Debug builds only.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    ForEach(DebugWindowPreset.allCases) { preset in
                        Button(preset.label) {
                            appState.applyDebugWindowPreset(preset)
                            loadFromStore()
                        }
                    }
                    Button("Reset today's goal") {
                        appState.debugResetTodayGoal()
                    }
                    Button("Reset onboarding") {
                        appState.debugResetOnboarding()
                    }
                } header: {
                    Text("Debug")
                }
            }
        }
        .navigationTitle("Settings")
        .onAppear { loadFromStore() }
        .sheet(item: $backupPasswordMode) { mode in
            LocalBackupPasswordSheet(mode: mode) { password in
                switch mode {
                case .export:
                    exportBackup(password: password)
                case .importFile:
                    pendingImportPassword = password
                    showImportPicker = true
                }
            }
        }
        .fileImporter(
            isPresented: $showImportPicker,
            allowedContentTypes: [UTType(filenameExtension: "mgbackup") ?? .data],
            allowsMultipleSelection: false
        ) { result in
            importBackup(result: result)
        }
        .fileExporter(
            isPresented: $showExporter,
            document: exportDocument,
            contentType: UTType(filenameExtension: "mgbackup") ?? .data,
            defaultFilename: "morning-guard-backup"
        ) { _ in
            exportDocument = nil
        }
        .confirmationDialog(
            "Restore from backup?",
            isPresented: $showRestoreConfirm,
            titleVisibility: .visible
        ) {
            Button("Restore", role: .destructive) {
                backupPasswordMode = .importFile
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Restore replaces settings, streaks, highlights, and goal progress on this device. This cannot be undone.")
        }
    }

    @State private var pendingImportPassword: String?

    @ViewBuilder
    private var notesEncryptionSection: some View {
        Section {
            Text("Reflection notes are always encrypted on this device (Keychain). Set a recovery password to open them after reinstall or on a new phone.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(
                appState.noteEncryption.cryptoState.hasRecoveryPassword()
                    ? "Recovery password: set"
                    : "Recovery password: not set — notes cannot be recovered on a new device"
            )
            .font(.subheadline)
            .foregroundStyle(
                appState.noteEncryption.cryptoState.hasRecoveryPassword() ? MGTheme.accent : .orange
            )
            if !appState.noteEncryption.cryptoState.hasRecoveryPassword() {
                Text("Use at least 8 characters with a letter and a number. Save it in a password manager.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                SecureField("Recovery password", text: $notesPassword)
                SecureField("Confirm recovery password", text: $notesConfirmPassword)
                Button("Set recovery password") { setupNotesEncryption() }
                    .disabled(notesPassword.count < 8 || notesConfirmPassword.count < 8)
            } else if !appState.noteEncryption.isUnlocked {
                SecureField("Recovery password", text: $notesPassword)
                Button("Unlock notes") { unlockNotesEncryption() }
                    .disabled(notesPassword.count < 8)
            } else {
                Text("Notes unlocked on this device.")
                    .foregroundStyle(MGTheme.accent)
                SecureField("Current recovery password", text: $notesPassword)
                SecureField("New recovery password", text: $notesNewPassword)
                SecureField("Confirm new recovery password", text: $notesConfirmNewPassword)
                Button("Change recovery password") { changeNotesPassword() }
                    .disabled(
                        notesPassword.count < 8
                            || notesNewPassword.count < 8
                            || notesConfirmNewPassword.count < 8
                    )
            }
            if let notesMessage {
                Text(notesMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Highlight notes")
        }
    }

    @ViewBuilder
    private var backupSection: some View {
        Section {
            Text("Encrypted backup includes highlights, streaks, and settings on this device (not a cloud export).")
                .font(.caption)
                .foregroundStyle(.secondary)
            Button("Export encrypted backup") {
                backupPasswordMode = .export
            }
            Button("Restore from backup") {
                showRestoreConfirm = true
            }
            if let backupMessage {
                Text(backupMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Local backup")
        }
    }

    @ViewBuilder
    private var accountSection: some View {
        Section {
            switch appState.supabaseAuth.state {
            case .unconfigured:
                Text("Cloud sync is not configured in this build.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if let hint = SupabaseConfig.missingPieceMessage {
                    Text(hint).font(.caption2).foregroundStyle(.secondary)
                }
            case .signedIn(let user):
                Text("Signed in as \(user.email ?? "your account"). Streaks and highlights sync while cloud sync is enabled.")
                    .font(.caption)
                Button("Sign out", role: .destructive) {
                    Task {
                        await appState.signOut()
                        accountMessage = "Signed out on this device."
                    }
                }
            case .signedOut, .error:
                TextField("Email for magic link", text: $magicLinkEmail)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                Button("Send sign-in link") {
                    Task {
                        do {
                            try await appState.sendMagicLink(email: magicLinkEmail)
                            accountMessage = "Check your email and open the link on this iPhone."
                        } catch {
                            accountMessage = error.localizedDescription
                        }
                    }
                }
            }
            if let accountMessage {
                Text(accountMessage).font(.caption).foregroundStyle(.secondary)
            }
        } header: {
            Text("Account")
        }
    }

    private func setupNotesEncryption() {
        if let validation = RecoveryPasswordRules.validate(password: notesPassword) {
            notesMessage = validation
            return
        }
        if notesPassword != notesConfirmPassword {
            notesMessage = "Passwords do not match."
            return
        }
        do {
            try appState.noteEncryption.setupWithPassword(notesPassword)
            appState.bibleHighlightStore.reencryptAllStoredNotes()
            notesPassword = ""
            notesConfirmPassword = ""
            notesMessage = "Recovery password saved. Store it in a password manager."
            Task { await appState.userDataSync.syncAllIfSignedIn() }
        } catch {
            notesMessage = error.localizedDescription
        }
    }

    private func unlockNotesEncryption() {
        do {
            try appState.noteEncryption.unlockWithPassword(notesPassword)
            notesPassword = ""
            notesMessage = "Notes unlocked."
        } catch {
            notesMessage = error.localizedDescription
        }
    }

    private func changeNotesPassword() {
        if let validation = RecoveryPasswordRules.validate(password: notesNewPassword) {
            notesMessage = validation
            return
        }
        if notesNewPassword != notesConfirmNewPassword {
            notesMessage = "Passwords do not match."
            return
        }
        do {
            try appState.noteEncryption.changePassword(oldPassword: notesPassword, newPassword: notesNewPassword)
            notesPassword = ""
            notesNewPassword = ""
            notesConfirmNewPassword = ""
            notesMessage = "Notes password updated."
            Task { await appState.userDataSync.syncAllIfSignedIn() }
        } catch {
            notesMessage = error.localizedDescription
        }
    }

    private func exportBackup(password: String) {
        do {
            let data = try appState.localBackupRepository.exportEncrypted(password: password)
            exportDocument = BackupFileDocument(data: data)
            showExporter = true
            backupMessage = "Choose where to save the backup file."
        } catch {
            backupMessage = error.localizedDescription
        }
    }

    private func importBackup(result: Result<[URL], Error>) {
        guard let password = pendingImportPassword else { return }
        pendingImportPassword = nil
        switch result {
        case .failure:
            backupMessage = "Could not open backup file."
        case .success(let urls):
            guard let url = urls.first else {
                backupMessage = "Could not open backup file."
                return
            }
            do {
                let accessed = url.startAccessingSecurityScopedResource()
                defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                let data = try Data(contentsOf: url)
                try appState.localBackupRepository.importEncrypted(password: password, data: data)
                appState.noteEncryption.lock()
                loadFromStore()
                if appState.noteEncryption.cryptoState.enabled {
                    backupMessage = "Backup restored. Unlock highlight notes in Settings with your notes password."
                } else {
                    backupMessage = "Backup restored."
                }
                Task { await appState.alarmScheduler.apply(alarm: appState.settingsStore.userSettings.alarm) }
            } catch {
                backupMessage = error.localizedDescription
            }
        }
    }

    private var goalTypeBinding: Binding<GoalType> {
        Binding(
            get: { appState.settingsStore.userSettings.morningGoal.type },
            set: { newType in
                appState.settingsStore.updateUserSettings { settings in
                    var copy = settings
                    copy.morningGoal = MorningGoalConfig(type: newType)
                    return copy
                }
            }
        )
    }

    private func loadFromStore() {
        let settings = appState.settingsStore.userSettings
        windowStart = date(hour: settings.morningWindow.startHour, minute: settings.morningWindow.startMinute)
        windowEnd = date(hour: settings.morningWindow.endHour, minute: settings.morningWindow.endMinute)
        alarmEnabled = settings.alarm.enabled
        alarmTime = date(hour: settings.alarm.hour, minute: settings.alarm.minute)
    }

    private func persistWindow() {
        let start = Calendar.current.dateComponents([.hour, .minute], from: windowStart)
        let end = Calendar.current.dateComponents([.hour, .minute], from: windowEnd)
        let window = MorningWindow(
            startHour: start.hour ?? 1,
            startMinute: start.minute ?? 0,
            endHour: end.hour ?? 10,
            endMinute: end.minute ?? 0
        )
        appState.updateMorningWindow(window)
    }

    private func persistAlarm() {
        let components = Calendar.current.dateComponents([.hour, .minute], from: alarmTime)
        let alarm = AlarmSettings(
            enabled: alarmEnabled,
            hour: components.hour ?? 6,
            minute: components.minute ?? 30
        )
        appState.updateAlarm(alarm)
    }

    private func savePassphrase() {
        do {
            try appState.setEmergencyPassphrase(newPassphrase)
            newPassphrase = ""
            passphraseMessage = "Emergency passphrase saved."
        } catch {
            passphraseMessage = error.localizedDescription
        }
    }

    private func date(hour: Int, minute: Int) -> Date {
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour = hour
        components.minute = minute
        return Calendar.current.date(from: components) ?? Date()
    }
}
