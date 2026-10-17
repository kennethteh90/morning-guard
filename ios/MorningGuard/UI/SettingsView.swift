import MorningGuardCore
import SwiftUI

struct SettingsView: View {
    @Environment(AppState.self) private var appState
    @State private var windowStart = Date()
    @State private var windowEnd = Date()
    @State private var alarmEnabled = false
    @State private var alarmTime = Date()
    @State private var newPassphrase = ""
    @State private var passphraseMessage: String?

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

            Section("Emergency passphrase") {
                SecureField("New passphrase (8+ characters)", text: $newPassphrase)
                Button("Save passphrase") { savePassphrase() }
                if let passphraseMessage {
                    Text(passphraseMessage)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Shields") {
                Text("To fully remove shields, revoke Screen Time access for Morning Guard in Settings → Screen Time, or clear your app selection during onboarding.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Clear shields now") {
                    appState.shieldController.revokeShieldsImmediately()
                }
            }
        }
        .navigationTitle("Settings")
        .onAppear { loadFromStore() }
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
