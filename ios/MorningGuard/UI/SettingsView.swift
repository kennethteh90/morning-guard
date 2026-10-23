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
    @State private var notesNewPassword = ""
    @State private var notesMessage: String?
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

            notesEncryptionSection
            backupSection

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
            Text("Uses the device Keychain on this phone plus a password you choose. After reinstall, enter the same password once to unlock notes.")
                .font(.caption)
                .foregroundStyle(.secondary)
            if !appState.noteEncryption.cryptoState.enabled {
                SecureField("Notes encryption password", text: $notesPassword)
                Button("Enable note encryption") { setupNotesEncryption() }
                    .disabled(notesPassword.count < 8)
            } else if !appState.noteEncryption.isUnlocked {
                SecureField("Notes encryption password", text: $notesPassword)
                Button("Unlock notes") { unlockNotesEncryption() }
                    .disabled(notesPassword.count < 8)
            } else {
                Text("Notes unlocked on this device.")
                    .foregroundStyle(MGTheme.accent)
                SecureField("Current notes password", text: $notesPassword)
                SecureField("New notes password", text: $notesNewPassword)
                Button("Change notes password") { changeNotesPassword() }
                    .disabled(notesPassword.count < 8 || notesNewPassword.count < 8)
            }
            if let notesMessage {
                Text(notesMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Encrypted highlight notes")
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

    private func setupNotesEncryption() {
        do {
            try appState.noteEncryption.setupWithPassword(notesPassword)
            notesPassword = ""
            notesMessage = "Note encryption enabled."
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
        do {
            try appState.noteEncryption.changePassword(oldPassword: notesPassword, newPassword: notesNewPassword)
            notesPassword = ""
            notesNewPassword = ""
            notesMessage = "Notes password updated."
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
