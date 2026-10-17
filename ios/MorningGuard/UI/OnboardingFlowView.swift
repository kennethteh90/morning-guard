import MorningGuardCore
import SwiftUI

#if canImport(FamilyControls)
import FamilyControls
#endif

struct OnboardingFlowView: View {
    @Environment(AppState.self) private var appState
    @State private var step = 0
    @State private var authError: String?
    @State private var isRequestingAuth = false

    @State private var windowStart = Date()
    @State private var windowEnd = Date()
    @State private var selectedGoal: GoalType = .bibleChapter

    private let steps = ["Welcome", "Screen Time", "Apps", "Morning setup"]

    var body: some View {
        VStack(spacing: 0) {
            ProgressView(value: Double(step + 1), total: Double(steps.count))
                .padding(.horizontal, MGTheme.standardPadding)
                .padding(.top, 8)

            TabView(selection: $step) {
                welcomeStep.tag(0)
                screenTimeStep.tag(1)
                appPickerStep.tag(2)
                setupStep.tag(3)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: step)

            bottomBar
        }
        .background(MGTheme.background)
        .onAppear {
            syncWindowPickers()
            selectedGoal = appState.settingsStore.userSettings.morningGoal.type
        }
    }

    private var welcomeStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Protect your morning")
                    .font(.largeTitle.bold())
                Text("Morning Guard shields the apps you choose during your morning window until you complete a short goal.")
                    .foregroundStyle(.secondary)
                MGCard {
                    Label("No notification digest on iOS", systemImage: "bell.slash")
                        .font(MGTheme.titleFont)
                    Text("Apple does not let apps hold or replay notifications from other apps. We use Screen Time shields instead — Phone and Messages can stay unselected.")
                        .font(MGTheme.captionFont)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var screenTimeStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Screen Time access")
                    .font(.largeTitle.bold())
                Text("Authorization is individual (not parental controls). Morning Guard only applies shields you configure.")
                    .foregroundStyle(.secondary)
                if let authError {
                    Text(authError)
                        .foregroundStyle(.red)
                        .font(.callout)
                }
                Button {
                    Task { await requestFamilyControls() }
                } label: {
                    if isRequestingAuth {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text(appState.shieldController.isAuthorized ? "Authorized" : "Authorize Screen Time")
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(appState.shieldController.isAuthorized || isRequestingAuth)
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var appPickerStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Choose apps to shield")
                .font(.title.bold())
                .padding(.horizontal, MGTheme.standardPadding)
            Text("Exclude Phone and Messages if you need them during your morning routine.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .padding(.horizontal, MGTheme.standardPadding)
            #if canImport(FamilyControls)
            FamilyActivityPicker(selection: shieldSelectionBinding)
                .padding(.horizontal, 8)
            #else
            Text("Family Controls unavailable in this build.")
                .padding(MGTheme.standardPadding)
            #endif
        }
    }

    private var setupStep: some View {
        Form {
            Section("Morning window") {
                DatePicker("Starts", selection: $windowStart, displayedComponents: .hourAndMinute)
                DatePicker("Ends", selection: $windowEnd, displayedComponents: .hourAndMinute)
            }
            Section("Goal") {
                Picker("Type", selection: $selectedGoal) {
                    Text("Bible chapter (60s)").tag(GoalType.bibleChapter)
                    Text("Timed focus").tag(GoalType.timedFocus)
                    Text("Steps").tag(GoalType.stepCount)
                }
                if selectedGoal == .bibleChapter {
                    Text("Default: John 1 — advance automatically after completion.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var bottomBar: some View {
        HStack {
            if step > 0 {
                Button("Back") { step -= 1 }
            }
            Spacer()
            Button(step == steps.count - 1 ? "Finish" : "Continue") {
                if step == steps.count - 1 {
                    finishOnboarding()
                } else {
                    step += 1
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canAdvance)
        }
        .padding(MGTheme.standardPadding)
    }

    #if canImport(FamilyControls)
    private var shieldSelectionBinding: Binding<FamilyActivitySelection> {
        Binding(
            get: { appState.shieldController.selection },
            set: { newValue in
                appState.shieldController.selection = newValue
                appState.shieldController.persistSelection()
            }
        )
    }
    #endif

    private var canAdvance: Bool {
        switch step {
        case 1: appState.shieldController.isAuthorized
        case 2: appState.shieldController.hasSelection
        default: true
        }
    }

    private func syncWindowPickers() {
        let window = appState.settingsStore.userSettings.morningWindow
        windowStart = date(hour: window.startHour, minute: window.startMinute)
        windowEnd = date(hour: window.endHour, minute: window.endMinute)
    }

    private func date(hour: Int, minute: Int) -> Date {
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour = hour
        components.minute = minute
        return Calendar.current.date(from: components) ?? Date()
    }

    private func finishOnboarding() {
        let start = Calendar.current.dateComponents([.hour, .minute], from: windowStart)
        let end = Calendar.current.dateComponents([.hour, .minute], from: windowEnd)
        let window = MorningWindow(
            startHour: start.hour ?? 1,
            startMinute: start.minute ?? 0,
            endHour: end.hour ?? 10,
            endMinute: end.minute ?? 0
        )
        appState.settingsStore.updateUserSettings { settings in
            var copy = settings
            copy.morningWindow = window
            copy.morningGoal = MorningGoalConfig(type: selectedGoal)
            return copy
        }
        appState.shieldController.persistSelection()
        appState.completeOnboarding()
    }

    private func requestFamilyControls() async {
        isRequestingAuth = true
        authError = nil
        defer { isRequestingAuth = false }
        do {
            try await appState.shieldController.requestAuthorization()
        } catch {
            authError = error.localizedDescription
        }
    }
}
