import MorningGuardCore
import SwiftUI
import UniformTypeIdentifiers

#if canImport(FamilyControls)
import FamilyControls
#endif

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
    @State private var backupMessage: String?
    @State private var backupPasswordMode: LocalBackupPasswordSheet.Mode?
    @State private var showRestoreConfirm = false
    @State private var showImportPicker = false
    @State private var exportDocument: BackupFileDocument?
    @State private var showExporter = false
    #if canImport(FamilyControls)
    @State private var shieldActivitySelection = FamilyActivitySelection()
    #endif

    var body: some View {
        Form {
            Section(L10n.tr("settings.morning_window")) {
                DatePicker(L10n.tr("settings.starts"), selection: $windowStart, displayedComponents: .hourAndMinute)
                    .onChange(of: windowStart) { _, _ in persistWindow() }
                DatePicker(L10n.tr("settings.ends"), selection: $windowEnd, displayedComponents: .hourAndMinute)
                    .onChange(of: windowEnd) { _, _ in persistWindow() }
            }

            Section(L10n.tr("settings.morning_alarm")) {
                Toggle(L10n.tr("settings.enabled"), isOn: $alarmEnabled)
                    .onChange(of: alarmEnabled) { _, _ in persistAlarm() }
                DatePicker(L10n.tr("settings.time"), selection: $alarmTime, displayedComponents: .hourAndMinute)
                    .onChange(of: alarmTime) { _, _ in persistAlarm() }
            }

            Section(L10n.tr("settings.goal")) {
                Picker(L10n.tr("settings.goal.type"), selection: goalTypeBinding) {
                    Text(L10n.tr("settings.goal.bible_chapter")).tag(GoalType.bibleChapter)
                    Text(L10n.tr("settings.goal.timed_focus")).tag(GoalType.timedFocus)
                    Text(L10n.tr("settings.goal.steps")).tag(GoalType.stepCount)
                }
                if appState.settingsStore.userSettings.morningGoal.type == .bibleChapter {
                    let goal = appState.settingsStore.userSettings.morningGoal
                    Text(L10n.tr("settings.goal.reading_format", goal.bibleBook, goal.bibleChapter))
                }
            }

            Section(L10n.tr("settings.bible.section")) {
                NavigationLink {
                    BibleTranslationPickerSheet(
                        selectedTranslationId: appState.settingsStore.userSettings.morningGoal.bibleTranslation,
                        onSelect: { appState.setBibleTranslation($0) }
                    )
                } label: {
                    HStack {
                        Text(L10n.tr("settings.bible.default_translation"))
                        Spacer()
                        Text(BibleTranslations.chipLabel(id: appState.settingsStore.userSettings.morningGoal.bibleTranslation))
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityLabel(L10n.tr("a11y.settings.translation"))
            }

            SyncAndRecoverySettingsSection()
            backupSection

            Section(L10n.tr("settings.emergency.section")) {
                MGSettingsSectionIntro(text: L10n.tr("settings.emergency.intro"))
                SecureField(L10n.tr("settings.emergency.new_field"), text: $newPassphrase)
                MGSettingsPrimaryButton(title: L10n.tr("settings.emergency.save"), action: savePassphrase)
                    .accessibilityLabel(L10n.tr("a11y.settings.save_passphrase"))
                if let passphraseMessage {
                    MGSettingsInlineMessage(text: passphraseMessage)
                }
            }

            Section(L10n.tr("privacy.heading")) {
                NavigationLink(L10n.tr("settings.privacy.in_app")) {
                    PrivacyPolicyView()
                }
                .accessibilityLabel(L10n.tr("a11y.settings.privacy_in_app"))
                if let url = SettingsView.privacyPolicyURL {
                    Link(L10n.tr("settings.privacy.web"), destination: url)
                        .accessibilityLabel(L10n.tr("a11y.settings.privacy_web"))
                }
            }

            Section(L10n.tr("settings.setup.section")) {
                Button(L10n.tr("settings.setup.replay")) {
                    appState.replayOnboarding()
                }
                .accessibilityLabel(L10n.tr("a11y.settings.replay_onboarding"))
                Text(L10n.tr("settings.setup.replay.footer"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section(L10n.tr("settings.on_iphone.section")) {
                Text(L10n.tr("settings.on_iphone.peace"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            shieldsSection

            if MGDebugFlags.showDebugPanels {
                Section {
                    Text(L10n.tr("settings.debug.intro"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    ForEach(DebugWindowPreset.allCases) { preset in
                        Button(preset.label) {
                            appState.applyDebugWindowPreset(preset)
                            loadFromStore()
                        }
                    }
                    Button(L10n.tr("settings.debug.reset_goal")) {
                        appState.debugResetTodayGoal()
                    }
                    Button(L10n.tr("settings.debug.reset_onboarding")) {
                        appState.debugResetOnboarding()
                    }
                } header: {
                    Text(L10n.tr("goal.debug.section"))
                }
            }
        }
        .navigationTitle(L10n.tr("settings.title"))
        .accessibilityLabel(L10n.tr("a11y.settings.title"))
        .accessibilityHint(L10n.tr("a11y.settings.title.hint"))
        .onAppear {
            loadFromStore()
            syncShieldSelectionFromController()
        }
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
            L10n.tr("settings.backup.restore_title"),
            isPresented: $showRestoreConfirm,
            titleVisibility: .visible
        ) {
            Button(L10n.tr("settings.backup.restore_button"), role: .destructive) {
                backupPasswordMode = .importFile
            }
            Button(L10n.tr("common.cancel"), role: .cancel) {}
        } message: {
            Text(L10n.tr("settings.backup.restore_message"))
        }
    }

    @State private var pendingImportPassword: String?

    @ViewBuilder
    private var backupSection: some View {
        Section {
            MGSettingsSectionIntro(text: L10n.tr("settings.backup.intro"))
            MGSettingsPrimaryButton(title: L10n.tr("settings.backup.export")) {
                backupPasswordMode = .export
            }
            .accessibilityLabel(L10n.tr("a11y.settings.export_backup"))
            MGSettingsSecondaryButton(title: L10n.tr("settings.backup.restore")) {
                showRestoreConfirm = true
            }
            .accessibilityLabel(L10n.tr("a11y.settings.restore_backup"))
            if let backupMessage {
                MGSettingsInlineMessage(text: backupMessage)
            }
        } header: {
            Text(L10n.tr("settings.backup.section"))
        }
    }

    @ViewBuilder
    private var shieldsSection: some View {
        Section(L10n.tr("settings.shields.section")) {
            #if canImport(FamilyControls)
            if appState.shieldController.isAuthorized || MGDebugFlags.relaxesShieldOnboarding {
                LabeledContent(L10n.tr("settings.shields.status")) {
                    Text(
                        appState.shieldController.shieldsApplied
                            ? L10n.tr("home.shields.active")
                            : L10n.tr("home.shields.off")
                    )
                    .foregroundStyle(appState.shieldController.shieldsApplied ? .orange : .secondary)
                }
                ShieldAppSelectionEditor(
                    selection: $shieldActivitySelection,
                    style: .settingsRow,
                    onSelectionChanged: {
                        appState.shieldController.selection = shieldActivitySelection
                        appState.shieldController.persistSelection()
                        appState.shieldController.refreshShields()
                    }
                )
            } else {
                Text(L10n.tr("settings.shields.not_authorized"))
                    .foregroundStyle(.secondary)
            }
            #else
            Text(L10n.tr("onboarding.apps.unavailable"))
                .foregroundStyle(.secondary)
            #endif
            Text(L10n.tr("settings.shields.help"))
                .font(.caption)
                .foregroundStyle(.secondary)
            if appState.gateService.evaluation.state != .locked {
                Button(L10n.tr("settings.shields.clear")) {
                    appState.shieldController.revokeShieldsImmediately()
                }
                .accessibilityLabel(L10n.tr("a11y.settings.clear_shields"))
                .accessibilityHint(L10n.tr("a11y.settings.clear_shields.hint"))
            }
        }
    }

    #if canImport(FamilyControls)
    private func syncShieldSelectionFromController() {
        shieldActivitySelection = appState.shieldController.selection
    }
    #endif

    private func exportBackup(password: String) {
        do {
            let data = try appState.localBackupRepository.exportEncrypted(password: password)
            exportDocument = BackupFileDocument(data: data)
            showExporter = true
            backupMessage = L10n.tr("settings.backup.choose_save")
        } catch {
            backupMessage = error.localizedDescription
        }
    }

    private func importBackup(result: Result<[URL], Error>) {
        guard let password = pendingImportPassword else { return }
        pendingImportPassword = nil
        switch result {
        case .failure:
            backupMessage = L10n.tr("settings.backup.open_failed")
        case .success(let urls):
            guard let url = urls.first else {
                backupMessage = L10n.tr("settings.backup.open_failed")
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
                    backupMessage = L10n.tr("settings.backup.restored_unlock")
                } else {
                    backupMessage = L10n.tr("settings.backup.restored")
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
            passphraseMessage = L10n.tr("settings.emergency.saved")
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
