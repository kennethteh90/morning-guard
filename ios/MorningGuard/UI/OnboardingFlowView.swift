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

    private let stepTitles = ["Access", "Apps", "Routine"]

    var body: some View {
        VStack(spacing: 0) {
            OnboardingStepHeader(steps: stepTitles, currentIndex: step)
                .padding(.horizontal, MGTheme.standardPadding)
                .padding(.top, 12)

            Group {
                switch step {
                case 0: screenTimeStep
                case 1: appPickerStep
                default: setupStep
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .animation(.easeInOut, value: step)
        }
        .background(MGTheme.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            bottomBar
        }
        .onAppear {
            syncWindowPickers()
            selectedGoal = appState.settingsStore.userSettings.morningGoal.type
        }
    }

    private var screenTimeStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                OnboardingSectionTitle(
                    title: "Screen Time access",
                    subtitle: "On a real iPhone, Morning Guard uses Apple’s Screen Time API to shield apps you pick until you finish your morning goal."
                )

                if MGDebugFlags.relaxesShieldOnboarding {
                    OnboardingSimulatorBanner(
                        text: "Simulator: authorization isn’t required. Tap Continue below to set your morning routine and try goals. Shields only work on a physical device."
                    )
                }

                MGCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("Individual authorization", systemImage: "person.crop.circle")
                            .font(.headline)
                        Text("This is self-control on your device—not parental monitoring.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                if let authError {
                    Text(authError)
                        .foregroundStyle(.red)
                        .font(.callout)
                }

                if !MGDebugFlags.relaxesShieldOnboarding {
                    Button {
                        Task { await requestFamilyControls() }
                    } label: {
                        if isRequestingAuth {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Label(
                                appState.shieldController.isAuthorized ? "Authorized" : "Authorize Screen Time",
                                systemImage: appState.shieldController.isAuthorized ? "checkmark.circle.fill" : "hourglass.circle"
                            )
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(MGTheme.accent)
                    .disabled(appState.shieldController.isAuthorized || isRequestingAuth)
                }
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var appPickerStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OnboardingSectionTitle(
                    title: "Apps to shield",
                    subtitle: "Choose what to limit during your morning window. Leave out Phone and Messages if you need them."
                )

                if MGDebugFlags.relaxesShieldOnboarding {
                    OnboardingSimulatorBanner(
                        text: "Simulator: the app picker may be empty or limited. You can continue without selecting apps."
                    )
                }

                #if canImport(FamilyControls)
                if appState.shieldController.isAuthorized || MGDebugFlags.relaxesShieldOnboarding {
                    FamilyActivityPicker(selection: shieldSelectionBinding)
                        .frame(maxHeight: 260)
                } else {
                    MGCard {
                        Text("Authorize Screen Time on the previous step to choose apps.")
                            .foregroundStyle(.secondary)
                    }
                }
                #else
                MGCard {
                    Text("Family Controls unavailable in this build.")
                        .foregroundStyle(.secondary)
                }
                #endif
            }
            .padding(.horizontal, MGTheme.standardPadding)
            .padding(.bottom, MGTheme.standardPadding)
        }
    }

    private var setupStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                OnboardingSectionTitle(
                    title: "Your morning routine",
                    subtitle: "When this window is active and your goal isn’t done, selected apps stay shielded."
                )

                MGCard {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Morning window")
                            .font(.headline)
                        DatePicker("Starts", selection: $windowStart, displayedComponents: .hourAndMinute)
                        DatePicker("Ends", selection: $windowEnd, displayedComponents: .hourAndMinute)
                    }
                }

                MGCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Daily goal")
                            .font(.headline)
                        Picker("Type", selection: $selectedGoal) {
                            Text("Bible chapter (60s)").tag(GoalType.bibleChapter)
                            Text("Timed focus").tag(GoalType.timedFocus)
                            Text("Steps").tag(GoalType.stepCount)
                        }
                        .pickerStyle(.segmented)

                        Text(selectedGoalHint)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                tipForTestingWindow
            }
            .padding(MGTheme.standardPadding)
        }
    }

    @ViewBuilder
    private var tipForTestingWindow: some View {
        if MGDebugFlags.relaxesShieldOnboarding {
            OnboardingSimulatorBanner(
                text: "Tip: set the window so the current time is inside the range, then finish onboarding and tap Start morning goal on Home."
            )
        }
    }

    private var selectedGoalHint: String {
        switch selectedGoal {
        case .bibleChapter:
            return "John 1 by default. Stay on the goal screen for about 60 seconds."
        case .timedFocus:
            return "Stay on the goal screen until the timer finishes (default 2 minutes)."
        case .stepCount:
            return "Counts steps via HealthKit on device; Simulator may not update steps."
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 16) {
            if step > 0 {
                Button("Back") { step -= 1 }
                    .buttonStyle(.bordered)
            }
            Spacer()
            Button(step == stepTitles.count - 1 ? "Finish" : "Continue") {
                if step == stepTitles.count - 1 {
                    finishOnboarding()
                } else {
                    step += 1
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(MGTheme.accent)
            .disabled(isContinueDisabled)
        }
        .padding(MGTheme.standardPadding)
        .background(.bar)
        .shadow(color: .black.opacity(0.08), radius: 8, y: -2)
    }

    private var isContinueDisabled: Bool {
        if MGDebugFlags.relaxesShieldOnboarding { return false }
        return !canAdvance
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
        if MGDebugFlags.relaxesShieldOnboarding { return true }
        switch step {
        case 0: return appState.shieldController.isAuthorized
        case 1: return appState.shieldController.hasSelection
        default: return true
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
        let window: MorningWindow
        if MGDebugFlags.relaxesShieldOnboarding {
            window = DebugWindowPresets.includingNow()
        } else {
            let start = Calendar.current.dateComponents([.hour, .minute], from: windowStart)
            let end = Calendar.current.dateComponents([.hour, .minute], from: windowEnd)
            window = MorningWindow(
                startHour: start.hour ?? 1,
                startMinute: start.minute ?? 0,
                endHour: end.hour ?? 10,
                endMinute: end.minute ?? 0
            )
        }
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
